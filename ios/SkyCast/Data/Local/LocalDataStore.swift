import Foundation
import SwiftData

/// All SwiftData access, confined to one actor.
///
/// `@ModelActor` generates an `init(modelContainer:)` and a `modelContext` bound to this
/// actor's executor. That matters under Swift 6: `ModelContext` is **not** `Sendable`, so
/// touching one from arbitrary tasks is a compile error. Funnelling every read and write
/// through a single actor makes the isolation correct by construction instead of by
/// convention, and keeps database work off the main thread.
///
/// This is the Swift counterpart to Android's DAO layer.
@ModelActor
actor LocalDataStore {
    // MARK: - Saved locations

    func savedLocations() throws -> [SavedLocation] {
        let descriptor = FetchDescriptor<PersistentSavedLocation>(
            sortBy: [
                SortDescriptor(\.sortOrder, order: .forward),
                SortDescriptor(\.name, order: .forward),
            ]
        )
        return try modelContext.fetch(descriptor).map(WeatherMapper.location(from:))
    }

    func primaryLocation() throws -> SavedLocation? {
        var descriptor = FetchDescriptor<PersistentSavedLocation>(
            predicate: #Predicate { $0.isPrimary }
        )
        descriptor.fetchLimit = 1
        return try modelContext.fetch(descriptor).first.map(WeatherMapper.location(from:))
    }

    func location(id: Int64) throws -> SavedLocation? {
        try persistentLocation(id: id).map(WeatherMapper.location(from:))
    }

    func locationCount() throws -> Int {
        try modelContext.fetchCount(FetchDescriptor<PersistentSavedLocation>())
    }

    /// Saves a search hit, or updates the existing record when the same coordinates were
    /// already saved, re-adding a place must not create a duplicate.
    ///
    /// The first location added becomes primary, so the Home tab is never left with
    /// nothing to show.
    @discardableResult
    func save(_ result: LocationSearchResult) throws -> Int64 {
        let latitude = result.latitude
        let longitude = result.longitude
        let existing = try modelContext.fetch(
            FetchDescriptor<PersistentSavedLocation>(
                predicate: #Predicate { $0.latitude == latitude && $0.longitude == longitude }
            )
        ).first

        if let existing {
            existing.name = result.name
            existing.countryCode = result.countryCode
            existing.state = result.state
            try modelContext.save()
            return existing.id
        }

        let isFirst = try locationCount() == 0
        let id = try nextLocationID()
        let model = try PersistentSavedLocation(
            id: id,
            name: result.name,
            countryCode: result.countryCode,
            state: result.state,
            latitude: result.latitude,
            longitude: result.longitude,
            sortOrder: nextSortOrder(),
            isPrimary: isFirst
        )
        modelContext.insert(model)
        try modelContext.save()
        return id
    }

    /// Deletes a location and promotes another to primary if needed.
    ///
    /// Cached weather and forecast records are removed automatically by the cascade rules
    /// on `PersistentSavedLocation`.
    func delete(id: Int64) throws {
        guard let model = try persistentLocation(id: id) else { return }
        let wasPrimary = model.isPrimary
        modelContext.delete(model)
        try modelContext.save()

        // Exactly one primary must always survive, or the Home tab goes permanently empty.
        guard wasPrimary else { return }
        if let replacement = try modelContext.fetch(
            FetchDescriptor<PersistentSavedLocation>(
                sortBy: [SortDescriptor(\.sortOrder, order: .forward)]
            )
        ).first {
            try setPrimary(id: replacement.id)
        }
    }

    func setPrimary(id: Int64) throws {
        // Clear every flag then set one, in a single save, so the store is never observed
        // with zero or two primaries.
        for model in try modelContext.fetch(FetchDescriptor<PersistentSavedLocation>()) {
            model.isPrimary = (model.id == id)
        }
        try modelContext.save()
    }

    func reorder(ids: [Int64]) throws {
        let models = try modelContext.fetch(FetchDescriptor<PersistentSavedLocation>())
        let positions = Dictionary(uniqueKeysWithValues: ids.enumerated().map { ($1, $0) })
        for model in models {
            if let position = positions[model.id] {
                model.sortOrder = position
            }
        }
        try modelContext.save()
    }

    // MARK: - Weather cache

    func cachedWeather(locationID: Int64) throws -> Weather? {
        var descriptor = FetchDescriptor<PersistentWeather>(
            predicate: #Predicate { $0.locationID == locationID }
        )
        descriptor.fetchLimit = 1
        return try modelContext.fetch(descriptor).first.map(WeatherMapper.weather(from:))
    }

    /// Upsert: replace any existing record for this location so the cache holds exactly
    /// one current reading per place.
    func upsert(_ weather: Weather) throws {
        let locationID = weather.locationID
        let existing = try modelContext.fetch(
            FetchDescriptor<PersistentWeather>(predicate: #Predicate { $0.locationID == locationID })
        )
        for model in existing {
            modelContext.delete(model)
        }

        let model = WeatherMapper.persistentWeather(from: weather)
        model.location = try persistentLocation(id: locationID)
        modelContext.insert(model)
        try modelContext.save()
    }

    // MARK: - Forecast cache

    func cachedForecast(locationID: Int64) throws -> Forecast? {
        let descriptor = FetchDescriptor<PersistentForecastReading>(
            predicate: #Predicate { $0.locationID == locationID },
            sortBy: [SortDescriptor(\.time, order: .forward)]
        )
        return try WeatherMapper.forecast(from: modelContext.fetch(descriptor))
    }

    /// Replaces a location's forecast wholesale.
    ///
    /// Replace, never merge: leftover readings from a previous fetch would appear as
    /// phantom days once the forecast window rolls forward.
    func replaceForecast(_ forecast: Forecast) throws {
        let locationID = forecast.locationID
        let existing = try modelContext.fetch(
            FetchDescriptor<PersistentForecastReading>(
                predicate: #Predicate { $0.locationID == locationID }
            )
        )
        for model in existing {
            modelContext.delete(model)
        }

        let parent = try persistentLocation(id: locationID)
        for model in WeatherMapper.persistentReadings(from: forecast) {
            model.location = parent
            modelContext.insert(model)
        }
        try modelContext.save()
    }

    // MARK: - Maintenance

    /// Clears cached weather but **not** saved locations: cache is disposable, the user's
    /// places are not.
    func clearCache() throws {
        try modelContext.delete(model: PersistentWeather.self)
        try modelContext.delete(model: PersistentForecastReading.self)
        try modelContext.save()
    }

    // MARK: - Internals

    private func persistentLocation(id: Int64) throws -> PersistentSavedLocation? {
        var descriptor = FetchDescriptor<PersistentSavedLocation>(
            predicate: #Predicate { $0.id == id }
        )
        descriptor.fetchLimit = 1
        return try modelContext.fetch(descriptor).first
    }

    /// SwiftData has no autoincrement, so ids are assigned here. Matching Android's
    /// integer keys keeps the two data models directly comparable.
    private func nextLocationID() throws -> Int64 {
        let descriptor = FetchDescriptor<PersistentSavedLocation>(
            sortBy: [SortDescriptor(\.id, order: .reverse)]
        )
        return try (modelContext.fetch(descriptor).first?.id ?? 0) + 1
    }

    private func nextSortOrder() throws -> Int {
        let descriptor = FetchDescriptor<PersistentSavedLocation>(
            sortBy: [SortDescriptor(\.sortOrder, order: .reverse)]
        )
        return try (modelContext.fetch(descriptor).first?.sortOrder ?? -1) + 1
    }
}
