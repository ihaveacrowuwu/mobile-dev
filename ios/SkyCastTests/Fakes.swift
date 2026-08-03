import Foundation
@testable import SkyCast

// Hand-written test doubles.
//
// These are **fakes**, not mocks: each is a tiny working implementation whose state a test drives
// directly, and the compiler checks them against the protocol.
//
// Because view models depend only on `Domain` protocols, these fakes are all a view model test
// needs. No network, no SwiftData, no simulator services.

/// `WeatherRepository` whose emissions the test chooses.
final class FakeWeatherRepository: WeatherRepository, @unchecked Sendable {
    /// Emitted in order by `currentWeather(for:)`.
    var currentWeatherStates: [DataState<Weather>] = []
    var forecastStates: [DataState<Forecast>] = []

    /// Returned by `refresh(_:)`; set to a non-nil value to simulate a failed refresh.
    var refreshError: AppError?

    private(set) var refreshCallCount = 0
    private(set) var clearCacheCallCount = 0

    func currentWeather(for _: SavedLocation) -> AsyncStream<DataState<Weather>> {
        let states = currentWeatherStates
        return AsyncStream { continuation in
            for state in states {
                continuation.yield(state)
            }
            continuation.finish()
        }
    }

    func forecast(for _: SavedLocation) -> AsyncStream<DataState<Forecast>> {
        let states = forecastStates
        return AsyncStream { continuation in
            for state in states {
                continuation.yield(state)
            }
            continuation.finish()
        }
    }

    @discardableResult
    func refresh(_: SavedLocation) async -> AppError? {
        refreshCallCount += 1
        return refreshError
    }

    func clearCache() async {
        clearCacheCallCount += 1
    }
}

/// `LocationRepository` backed by an in-memory array.
final class FakeLocationRepository: LocationRepository, @unchecked Sendable {
    var locations: [SavedLocation] = []
    var searchResults: [LocationSearchResult] = []
    /// Thrown by `search(query:)` when set, so error paths are testable.
    var searchError: AppError?
    /// Thrown by `primaryLocation()` when set.
    var primaryLocationError: AppError?

    func savedLocations() async throws -> [SavedLocation] {
        locations.sorted { $0.sortOrder < $1.sortOrder }
    }

    func primaryLocation() async throws -> SavedLocation? {
        if let primaryLocationError {
            throw primaryLocationError
        }
        return locations.first(where: \.isPrimary)
    }

    func location(id: Int64) async throws -> SavedLocation? {
        locations.first { $0.id == id }
    }

    func search(query: String) async throws -> [LocationSearchResult] {
        if let searchError {
            throw searchError
        }
        guard query.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 else { return [] }
        return searchResults
    }

    @discardableResult
    func save(_ result: LocationSearchResult) async throws -> Int64 {
        let id = (locations.map(\.id).max() ?? 0) + 1
        locations.append(
            SavedLocation(
                id: id,
                name: result.name,
                countryCode: result.countryCode,
                state: result.state,
                latitude: result.latitude,
                longitude: result.longitude,
                sortOrder: locations.count,
                isPrimary: locations.isEmpty
            )
        )
        return id
    }

    func delete(_ location: SavedLocation) async throws {
        locations.removeAll { $0.id == location.id }
    }

    func setPrimary(_ location: SavedLocation) async throws {
        for index in locations.indices {
            locations[index].isPrimary = locations[index].id == location.id
        }
    }

    func reorder(ids: [Int64]) async throws {
        for (position, id) in ids.enumerated() {
            if let index = locations.firstIndex(where: { $0.id == id }) {
                locations[index].sortOrder = position
            }
        }
    }
}

/// `WeatherAPI` returning canned DTOs, for repository tests.
final class StubWeatherAPI: WeatherAPI, @unchecked Sendable {
    var currentWeatherResult: Result<CurrentWeatherDTO, Error> = .failure(AppError.offline)
    var forecastResult: Result<ForecastResponseDTO, Error> = .failure(AppError.offline)
    var searchResult: Result<[GeocodingResultDTO], Error> = .success([])

    private(set) var currentWeatherCallCount = 0

    func currentWeather(latitude _: Double, longitude _: Double) async throws -> CurrentWeatherDTO {
        currentWeatherCallCount += 1
        return try currentWeatherResult.get()
    }

    func forecast(latitude _: Double, longitude _: Double) async throws -> ForecastResponseDTO {
        try forecastResult.get()
    }

    func searchLocations(query _: String, limit _: Int) async throws -> [GeocodingResultDTO] {
        try searchResult.get()
    }
}

// MARK: - Sample data

enum Fixtures {
    /// A fixed instant, so no test depends on the wall clock.
    static let now = Date(timeIntervalSince1970: 1_785_931_200) // 2026-08-04T12:00:00Z

    static func location(
        id: Int64 = 1,
        name: String = "London",
        isPrimary: Bool = true
    )
        -> SavedLocation
    {
        SavedLocation(
            id: id,
            name: name,
            countryCode: "GB",
            state: "England",
            latitude: 51.5074,
            longitude: -0.1278,
            isPrimary: isPrimary
        )
    }

    static func weather(
        locationID: Int64 = 1,
        temperatureCelsius: Double = 22,
        cachedAt: Date = Fixtures.now
    )
        -> Weather
    {
        Weather(
            locationID: locationID,
            locationName: "London",
            condition: .clear,
            description: "Clear sky",
            iconCode: "01d",
            temperatureCelsius: temperatureCelsius,
            feelsLikeCelsius: temperatureCelsius - 1,
            minTemperatureCelsius: temperatureCelsius - 4,
            maxTemperatureCelsius: temperatureCelsius + 3,
            humidityPercent: 60,
            pressureHpa: 1_013,
            windSpeedMetresPerSecond: 4.5,
            windDirectionDegrees: 220,
            cloudinessPercent: 5,
            visibilityMetres: 10_000,
            sunrise: Fixtures.now.addingTimeInterval(-6 * 3_600),
            sunset: Fixtures.now.addingTimeInterval(8 * 3_600),
            observedAt: Fixtures.now,
            cachedAt: cachedAt
        )
    }

    static func searchResult(name: String = "London") -> LocationSearchResult {
        LocationSearchResult(
            name: name,
            countryCode: "GB",
            state: "England",
            latitude: 51.5074,
            longitude: -0.1278
        )
    }

    /// A minimal but realistic `/data/2.5/weather` payload, for decoding and mapper tests.
    static let currentWeatherJSON = """
    {
      "coord": { "lon": -0.1257, "lat": 51.5085 },
      "weather": [{ "id": 800, "main": "Clear", "description": "clear sky", "icon": "01d" }],
      "main": {
        "temp": 22.0, "feels_like": 21.4, "temp_min": 18.2,
        "temp_max": 24.6, "pressure": 1013, "humidity": 60
      },
      "visibility": 10000,
      "wind": { "speed": 4.5, "deg": 220 },
      "clouds": { "all": 5 },
      "dt": 1785931200,
      "sys": { "country": "GB", "sunrise": 1785909600, "sunset": 1785960000 },
      "timezone": 3600,
      "id": 2643743,
      "name": "London"
    }
    """
}
