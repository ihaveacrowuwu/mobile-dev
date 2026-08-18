import Foundation

/// Saved locations (SwiftData) plus geocoding search (network).
///
/// Search results are **not** cached, so a changed query cannot show stale matches.
final class LocationRepositoryImpl: LocationRepository {
    private let api: any WeatherAPI
    private let local: LocalDataStore

    init(api: any WeatherAPI, local: LocalDataStore) {
        self.api = api
        self.local = local
    }

    func savedLocations() async throws -> [SavedLocation] {
        do {
            return try await local.savedLocations()
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func primaryLocation() async throws -> SavedLocation? {
        do {
            return try await local.primaryLocation()
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func location(id: Int64) async throws -> SavedLocation? {
        do {
            return try await local.location(id: id)
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func search(query: String) async throws -> [LocationSearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        // Guarded here rather than in the view model so every caller is protected from
        // burning API quota on an empty or one-character query.
        guard trimmed.count >= Constants.minimumQueryLength else { return [] }

        let dtos = try await api.searchLocations(
            query: trimmed,
            limit: Constants.searchLimit
        )
        return dtos.map(WeatherMapper.searchResult(from:))
    }

    @discardableResult
    func save(_ result: LocationSearchResult) async throws -> Int64 {
        // The cap is enforced here rather than in the UI, because the UI is not the only caller and "the
        // list is full" is a fact about the data, not about a screen. See `SavedLocation.maxSaved`.
        let saved = try await savedLocations()
        guard SavedLocation.canSaveAnother(currentCount: saved.count) else {
            throw AppError.locationLimitReached(limit: SavedLocation.maxSaved)
        }

        do {
            return try await local.save(result)
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func delete(_ location: SavedLocation) async throws {
        do {
            try await local.delete(id: location.id)
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func setPrimary(_ location: SavedLocation) async throws {
        do {
            try await local.setPrimary(id: location.id)
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    func reorder(ids: [Int64]) async throws {
        do {
            try await local.reorder(ids: ids)
        } catch {
            throw AppError.storage(detail: error.localizedDescription)
        }
    }

    private enum Constants {
        /// Below this length OpenWeather's geocoder returns noise, not matches.
        static let minimumQueryLength = 2
        static let searchLimit = 8
    }
}
