import Foundation

/// Offline-first weather.
///
/// Implements the offline-first read algorithm. The two properties that matter
/// most, and that `WeatherRepositoryTests` asserts:
///
/// 1. **Cached data is yielded before any network call**, so a warm start never spins.
/// 2. **A failed refresh never clears the cache.** The error is yielded *alongside* the
///    stale data so the UI can show a banner rather than an empty screen.
///
/// `now` is injected rather than calling `Date()`, so TTL and staleness behaviour is
/// testable without sleeping.
final class WeatherRepositoryImpl: WeatherRepository {
    private let api: any WeatherAPI
    private let local: LocalDataStore
    private let networkMonitor: any NetworkMonitoring
    private let now: @Sendable () -> Date

    init(
        api: any WeatherAPI,
        local: LocalDataStore,
        networkMonitor: any NetworkMonitoring,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.api = api
        self.local = local
        self.networkMonitor = networkMonitor
        self.now = now
    }

    func currentWeather(for location: SavedLocation) -> AsyncStream<DataState<Weather>> {
        AsyncStream { continuation in
            let task = Task {
                await self.emitCurrentWeather(for: location, into: continuation)
                continuation.finish()
            }
            // Without this, navigating away leaves the fetch running and the stream alive.
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func forecast(for location: SavedLocation) -> AsyncStream<DataState<Forecast>> {
        AsyncStream { continuation in
            let task = Task {
                await self.emitForecast(for: location, into: continuation)
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    @discardableResult
    func refresh(_ location: SavedLocation) async -> AppError? {
        guard await networkMonitor.isOnline else { return .offline }
        do {
            // Pull-to-refresh should update both tabs, not just the visible one.
            _ = try await fetchAndCacheWeather(for: location)
            _ = try await fetchAndCacheForecast(for: location)
            return nil
        } catch {
            return AppError.from(error)
        }
    }

    func clearCache() async {
        try? await local.clearCache()
    }

    // MARK: - Current weather

    private func emitCurrentWeather(
        for location: SavedLocation,
        into continuation: AsyncStream<DataState<Weather>>.Continuation
    ) async {
        let cached = try? await local.cachedWeather(locationID: location.id)
        let stale = cached?.isStale(now: now()) ?? true

        // 1 ─ Show whatever we already have, immediately and without a spinner.
        if let cached {
            continuation.yield(.success(cached, stale: stale))
        } else {
            continuation.yield(.loading())
        }

        // 2 ─ Fresh enough? Then stop; do not spend the user's data or our quota.
        if cached != nil, !stale {
            return
        }

        // 3 ─ Offline with a cache is a normal state, not an error worth interrupting for.
        guard await networkMonitor.isOnline else {
            continuation.yield(.failure(.offline, cached: cached, stale: cached != nil))
            return
        }

        // 4 ─ Refreshing over existing data: subtle indicator, content stays visible.
        continuation.yield(.refreshing(cached, stale: stale))

        // 5 ─ Fetch, persist, yield. On failure keep the cache and attach the error.
        do {
            let fresh = try await fetchAndCacheWeather(for: location)
            continuation.yield(.success(fresh))
        } catch is CancellationError {
            // Navigating away is not a failure; yield nothing.
        } catch {
            continuation.yield(
                .failure(AppError.from(error), cached: cached, stale: cached != nil)
            )
        }
    }

    private func fetchAndCacheWeather(for location: SavedLocation) async throws -> Weather {
        let dto = try await api.currentWeather(
            latitude: location.latitude,
            longitude: location.longitude
        )
        let weather = WeatherMapper.weather(
            from: dto,
            locationID: location.id,
            locationName: location.name,
            cachedAt: now()
        )
        try await local.upsert(weather)
        return weather
    }

    // MARK: - Forecast

    private func emitForecast(
        for location: SavedLocation,
        into continuation: AsyncStream<DataState<Forecast>>.Continuation
    ) async {
        let cached = try? await local.cachedForecast(locationID: location.id)
        let stale = cached?.isStale(now: now()) ?? true

        if let cached {
            continuation.yield(.success(cached, stale: stale))
        } else {
            continuation.yield(.loading())
        }

        if cached != nil, !stale {
            return
        }

        guard await networkMonitor.isOnline else {
            continuation.yield(.failure(.offline, cached: cached, stale: cached != nil))
            return
        }

        continuation.yield(.refreshing(cached, stale: stale))

        do {
            let fresh = try await fetchAndCacheForecast(for: location)
            continuation.yield(.success(fresh))
        } catch is CancellationError {
            // Ignored, as above.
        } catch {
            continuation.yield(
                .failure(AppError.from(error), cached: cached, stale: cached != nil)
            )
        }
    }

    private func fetchAndCacheForecast(for location: SavedLocation) async throws -> Forecast {
        let dto = try await api.forecast(
            latitude: location.latitude,
            longitude: location.longitude
        )
        let forecast = WeatherMapper.forecast(
            from: dto,
            locationID: location.id,
            locationName: location.name,
            cachedAt: now()
        )
        try await local.replaceForecast(forecast)
        return forecast
    }
}
