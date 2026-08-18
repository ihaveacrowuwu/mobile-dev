import Foundation

// Repository protocols: the boundary between `Domain` and `Data`.
//
// They name **no framework type**, so an implementation could use URLSession and SwiftData, or
// fixtures in memory, and nothing above this layer changes.

// MARK: - Weather

/// A repository emission: data, whether it is being refreshed, and whether the last refresh
/// failed, all three at once.
///
/// One value rather than a `Result`, so that "failed, but here is the cache" is expressible.
struct DataState<Value: Sendable>: Sendable {
    var data: Value?
    var isLoading: Bool = false
    var isRefreshing: Bool = false
    var isStale: Bool = false
    var error: AppError?

    var hasData: Bool {
        data != nil
    }

    static func loading() -> Self {
        .init(isLoading: true)
    }

    static func refreshing(_ cached: Value?, stale: Bool = false) -> Self {
        .init(data: cached, isRefreshing: true, isStale: stale)
    }

    static func success(_ data: Value, stale: Bool = false) -> Self {
        .init(data: data, isStale: stale)
    }

    /// A failure that preserves whatever cached data we already had.
    static func failure(_ error: AppError, cached: Value? = nil, stale: Bool = false) -> Self {
        .init(data: cached, isStale: stale, error: error)
    }
}

extension DataState: Equatable where Value: Equatable {}

/// Weather data, offline-first.
///
/// Implementations must follow the offline-first read algorithm: emit the
/// cache first, then attempt a refresh, and **never discard cached data because the
/// network failed**.
protocol WeatherRepository: Sendable {
    /// Streams current weather for `location`.
    ///
    /// Yields at least once. The first element is the cached value if one exists, so the
    /// UI can render without a spinner; later elements carry the refreshed value or an
    /// error that leaves the cached data intact.
    func currentWeather(for location: SavedLocation) -> AsyncStream<DataState<Weather>>

    /// As `currentWeather(for:)`, for the multi-day forecast.
    func forecast(for location: SavedLocation) -> AsyncStream<DataState<Forecast>>

    /// Forces a network refresh, bypassing the TTL. Used by pull-to-refresh.
    ///
    /// Returns the `AppError` that occurred, or `nil` on success.
    @discardableResult
    func refresh(_ location: SavedLocation) async -> AppError?

    /// Drops every cached reading. Exposed in Settings so the user can reclaim space.
    func clearCache() async
}

// MARK: - Locations

/// The user's saved places, and geocoding search to add new ones.
protocol LocationRepository: Sendable {
    /// All saved locations, ordered by the user's arrangement.
    func savedLocations() async throws -> [SavedLocation]

    /// The location shown on the Home tab. `nil` only before the first is added.
    func primaryLocation() async throws -> SavedLocation?

    func location(id: Int64) async throws -> SavedLocation?

    /// Searches OpenWeather's geocoding API.
    ///
    /// Throws `AppError`. Unlike the weather streams, a search has no cache to fall back
    /// on, so a failure is total and the caller must handle it.
    func search(query: String) async throws -> [LocationSearchResult]

    /// Saves a search hit and returns its new id. The first location added becomes primary.
    @discardableResult
    func save(_ result: LocationSearchResult) async throws -> Int64

    func delete(_ location: SavedLocation) async throws

    func setPrimary(_ location: SavedLocation) async throws

    /// Persists a user-driven reorder of the list.
    func reorder(ids: [Int64]) async throws
}

// MARK: - Settings

/// User settings, backed by `UserDefaults`.
///
/// `@MainActor` because `UserDefaults` changes drive SwiftUI directly and every caller is already
/// on the main actor.
@MainActor
protocol SettingsRepository: AnyObject {
    var preferences: UserPreferences { get }

    func setTemperatureUnit(_ unit: TemperatureUnit)
    func setWindSpeedUnit(_ unit: WindSpeedUnit)
    func setThemeMode(_ mode: ThemeMode)
    func reset()
}

/// The nearest airport's METAR for a saved location, offline-first.
///
/// Same contract as ``WeatherRepository``: emit the cache immediately, then attempt a refresh, and
/// never discard a cached observation because the network failed. A METAR an hour old is still a real
/// observation, arguably more clearly so than a current-conditions reading, since it carries the
/// time it was taken.
/// The nearest airport's METAR for a saved location, offline-first.
///
/// Same contract as ``WeatherRepository``: emit the cache immediately, then attempt a refresh, and
/// never discard a cached observation because the network failed.
protocol MetarRepository: Sendable {
    func nearestMetar(for location: SavedLocation) -> AsyncStream<DataState<MetarReport>>

    /// Forces a fetch, bypassing the TTL. Returns the error, or `nil` on success.
    func refresh(_ location: SavedLocation) async -> AppError?

    /// Drops every cached observation. Called from Settings alongside the weather cache.
    func clearCache() async
}
