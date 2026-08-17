import Foundation
import SwiftData

// SwiftData models, the iOS counterpart to Android's Room entities.
//
// Like DTOs, these never leave the `Data` layer; `WeatherMapper` converts them to domain models at
// the repository boundary, so a schema change does not ripple into the UI.
//
// ## Changing the schema
//
// SwiftData migrates additive changes automatically, but renames and type changes need a
// `SchemaMigrationPlan`. Anything beyond adding an optional property requires one, plus a test
// proving the user's saved locations survive the upgrade. Never delete the store on a migration
// failure: that would destroy user data.

/// A place the user chose to track. Durable, user-owned data, never cache-evicted.
@Model
final class PersistentSavedLocation {
    /// Assigned by `LocationRepositoryImpl`, not SwiftData: the domain model and the
    /// Android build both use a stable integer id, and matching them keeps the two
    /// platforms' data models comparable.
    @Attribute(.unique) var id: Int64
    var name: String
    var countryCode: String
    var state: String?
    var latitude: Double
    var longitude: Double
    var sortOrder: Int
    var isPrimary: Bool

    /// Deleting a location removes its cached weather, the SwiftData equivalent of
    /// Room's `onDelete = CASCADE`. Without this, orphan cache rows accumulate forever.
    @Relationship(deleteRule: .cascade, inverse: \PersistentWeather.location)
    var cachedWeather: [PersistentWeather] = []

    @Relationship(deleteRule: .cascade, inverse: \PersistentForecastReading.location)
    var cachedForecastReadings: [PersistentForecastReading] = []

    init(
        id: Int64,
        name: String,
        countryCode: String,
        state: String? = nil,
        latitude: Double,
        longitude: Double,
        sortOrder: Int = 0,
        isPrimary: Bool = false
    ) {
        self.id = id
        self.name = name
        self.countryCode = countryCode
        self.state = state
        self.latitude = latitude
        self.longitude = longitude
        self.sortOrder = sortOrder
        self.isPrimary = isPrimary
    }
}

/// Cached current conditions, one record per saved location.
@Model
final class PersistentWeather {
    @Attribute(.unique) var locationID: Int64
    var locationName: String
    var conditionRawValue: Int
    var weatherDescription: String
    var iconCode: String
    var temperatureCelsius: Double
    var feelsLikeCelsius: Double
    var minTemperatureCelsius: Double
    var maxTemperatureCelsius: Double
    var humidityPercent: Int
    var pressureHpa: Int
    var windSpeedMetresPerSecond: Double
    var windDirectionDegrees: Int
    var cloudinessPercent: Int
    var visibilityMetres: Int
    var sunrise: Date
    var sunset: Date
    var observedAt: Date
    var cachedAt: Date
    /// The location's UTC offset in seconds.
    ///
    /// Defaulted, so SwiftData migrates an existing store lightweightly rather than needing a
    /// `VersionedSchema`: a record written before this property existed reads as UTC and is
    /// replaced by a correct one at the next refresh, within the 10-minute TTL. Both models here
    /// are caches, so there is no user data at risk.
    var timeZoneOffsetSeconds: Int = 0

    var location: PersistentSavedLocation?

    init(
        locationID: Int64,
        locationName: String,
        conditionRawValue: Int,
        weatherDescription: String,
        iconCode: String,
        temperatureCelsius: Double,
        feelsLikeCelsius: Double,
        minTemperatureCelsius: Double,
        maxTemperatureCelsius: Double,
        humidityPercent: Int,
        pressureHpa: Int,
        windSpeedMetresPerSecond: Double,
        windDirectionDegrees: Int,
        cloudinessPercent: Int,
        visibilityMetres: Int,
        sunrise: Date,
        sunset: Date,
        observedAt: Date,
        cachedAt: Date,
        timeZoneOffsetSeconds: Int
    ) {
        self.locationID = locationID
        self.locationName = locationName
        self.conditionRawValue = conditionRawValue
        self.weatherDescription = weatherDescription
        self.iconCode = iconCode
        self.temperatureCelsius = temperatureCelsius
        self.feelsLikeCelsius = feelsLikeCelsius
        self.minTemperatureCelsius = minTemperatureCelsius
        self.maxTemperatureCelsius = maxTemperatureCelsius
        self.humidityPercent = humidityPercent
        self.pressureHpa = pressureHpa
        self.windSpeedMetresPerSecond = windSpeedMetresPerSecond
        self.windDirectionDegrees = windDirectionDegrees
        self.cloudinessPercent = cloudinessPercent
        self.visibilityMetres = visibilityMetres
        self.sunrise = sunrise
        self.sunset = sunset
        self.observedAt = observedAt
        self.cachedAt = cachedAt
        self.timeZoneOffsetSeconds = timeZoneOffsetSeconds
    }
}

/// Cached forecast, stored as one record per 3-hourly reading rather than a serialised
/// blob, so "the next 24 hours" is a predicate instead of a decode-and-filter.
@Model
final class PersistentForecastReading {
    var locationID: Int64
    var locationName: String
    var time: Date
    var conditionRawValue: Int
    var weatherDescription: String
    var iconCode: String
    var temperatureCelsius: Double
    var precipitationProbability: Double
    var windSpeedMetresPerSecond: Double
    var cachedAt: Date
    /// See ``PersistentWeather/timeZoneOffsetSeconds``.
    var timeZoneOffsetSeconds: Int = 0

    var location: PersistentSavedLocation?

    init(
        locationID: Int64,
        locationName: String,
        time: Date,
        conditionRawValue: Int,
        weatherDescription: String,
        iconCode: String,
        temperatureCelsius: Double,
        precipitationProbability: Double,
        windSpeedMetresPerSecond: Double,
        cachedAt: Date,
        timeZoneOffsetSeconds: Int
    ) {
        self.locationID = locationID
        self.locationName = locationName
        self.time = time
        self.conditionRawValue = conditionRawValue
        self.weatherDescription = weatherDescription
        self.iconCode = iconCode
        self.temperatureCelsius = temperatureCelsius
        self.precipitationProbability = precipitationProbability
        self.windSpeedMetresPerSecond = windSpeedMetresPerSecond
        self.cachedAt = cachedAt
        self.timeZoneOffsetSeconds = timeZoneOffsetSeconds
    }
}

// MARK: - Container

/// Builds the app's `ModelContainer`.
enum ModelContainerFactory {
    static let schema = Schema([
        PersistentSavedLocation.self,
        PersistentWeather.self,
        PersistentForecastReading.self,
    ])

    /// The on-disk container used by the app.
    static func live() throws -> ModelContainer {
        try ModelContainer(
            for: schema,
            configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        )
    }

    /// An in-memory container for tests and SwiftUI previews.
    ///
    /// The same schema as `live()`, so a test that passes here is testing the real model
    /// graph, only the storage is different.
    static func inMemory() throws -> ModelContainer {
        try ModelContainer(
            for: schema,
            configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        )
    }
}
