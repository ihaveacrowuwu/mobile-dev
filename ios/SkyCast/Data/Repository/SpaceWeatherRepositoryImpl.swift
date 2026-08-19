import Foundation

/// Offline-first space weather.
///
/// The same five steps in the same order as the weather and METAR repositories, so all three read alike: yield
/// the cache before any network call, stop if it is fresh, treat offline-with-a-cache as a normal state, and on
/// failure yield the error *alongside* the stale reading rather than clearing it.
///
/// Two things are specific to this source:
///
/// - **No location.** One reading serves every saved place, so there is one row and no cache key. The part that
///   makes it local, "is it worth looking from *here*", is computed on the device by ``AuroraCalculator``.
/// - **A successful call with no measured entry is `.notFound`**, not an offline error. The feed always contains
///   observed periods, so an empty result means its shape has changed rather than that the network failed, and
///   telling the user to check their connection would send them looking in the wrong place.
///
/// `now` is injected rather than calling `Date()`, so TTL behaviour is testable without sleeping.
final class SpaceWeatherRepositoryImpl: SpaceWeatherRepository {
    private let api: any SpaceWeatherAPI
    private let local: LocalDataStore
    private let networkMonitor: any NetworkMonitoring
    private let now: @Sendable () -> Date

    init(
        api: any SpaceWeatherAPI,
        local: LocalDataStore,
        networkMonitor: any NetworkMonitoring,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.api = api
        self.local = local
        self.networkMonitor = networkMonitor
        self.now = now
    }

    func spaceWeather() -> AsyncStream<DataState<SpaceWeather>> {
        AsyncStream { continuation in
            let task = Task {
                await self.emit(into: continuation)
                continuation.finish()
            }
            // Without this, navigating away leaves the fetch running and the stream alive.
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    @discardableResult
    func refresh() async -> AppError? {
        guard await networkMonitor.isOnline else { return .offline }
        do {
            return try await fetchAndCache() == nil ? .notFound : nil
        } catch {
            return AppError.from(error)
        }
    }

    func clearCache() async {
        try? await local.clearCache()
    }

    // MARK: - Internals

    private func emit(into continuation: AsyncStream<DataState<SpaceWeather>>.Continuation) async {
        let cached = try? await local.cachedSpaceWeather()
        let stale = cached?.isStale(now: now()) ?? true

        // 1 ─ Whatever we already have, immediately and without a spinner.
        if let cached {
            continuation.yield(DataState(data: cached, isStale: stale))
        } else {
            continuation.yield(DataState(isLoading: true))
        }

        // 2 ─ Fresh enough? Kp is issued every three hours; refetching inside the TTL cannot produce more.
        if cached != nil, !stale {
            return
        }

        // 3 ─ Offline with a cache is a normal state, not an error worth interrupting for.
        guard await networkMonitor.isOnline else {
            continuation.yield(DataState(data: cached, isStale: cached != nil, error: .offline))
            return
        }

        // 4 ─ Refreshing over existing data: subtle indicator, content stays visible.
        if cached != nil {
            continuation.yield(DataState(data: cached, isRefreshing: true, isStale: stale))
        }

        // 5 ─ Fetch, persist, yield. On failure keep the cache and attach the error.
        do {
            if let fresh = try await fetchAndCache() {
                continuation.yield(DataState(data: fresh))
            } else {
                continuation.yield(DataState(data: cached, isStale: cached != nil, error: .notFound))
            }
        } catch {
            continuation.yield(DataState(data: cached, isStale: cached != nil, error: AppError.from(error)))
        }
    }

    /// Returns `nil` when the call succeeded but the feed carried no measured period.
    private func fetchAndCache() async throws -> SpaceWeather? {
        let entries = try await api.kpForecast()
        guard let weather = SpaceWeatherMapper.spaceWeather(from: entries, cachedAt: now()) else { return nil }
        try await local.upsert(weather)
        return weather
    }
}
