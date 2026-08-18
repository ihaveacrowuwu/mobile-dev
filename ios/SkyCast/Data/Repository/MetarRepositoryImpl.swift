import Foundation

/// Offline-first METARs.
///
/// The same algorithm as ``WeatherRepositoryImpl``, in the same order, so the two read alike: yield
/// the cache before any network call, stop if it is fresh, treat offline-with-a-cache as a normal
/// state, and on failure yield the error *alongside* the stale observation rather than clearing it.
///
/// Two things are specific to this source:
///
/// - There is no "nearest station" endpoint, so a fetch asks for every station in a box around the
///   location and ``MetarMapper`` picks the closest. The box is generous, `searchDegrees` either
///   side, because a tight box around a place with no nearby airport comes back empty, and an empty
///   list looks exactly like a network failure to whoever is reading the screen.
/// - A successful call that yields no usable station is `.notFound`, not an offline error. There is
///   no airport reporting nearby, which is a fact about the place rather than the network, and
///   telling the user to check their connection would send them looking in the wrong place.
///
/// `now` is injected rather than calling `Date()`, so TTL behaviour is testable without sleeping.
final class MetarRepositoryImpl: MetarRepository {
    private let api: any AviationAPI
    private let local: LocalDataStore
    private let networkMonitor: any NetworkMonitoring
    private let now: @Sendable () -> Date

    init(
        api: any AviationAPI,
        local: LocalDataStore,
        networkMonitor: any NetworkMonitoring,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.api = api
        self.local = local
        self.networkMonitor = networkMonitor
        self.now = now
    }

    func nearestMetar(for location: SavedLocation) -> AsyncStream<DataState<MetarReport>> {
        AsyncStream { continuation in
            let task = Task {
                await self.emit(for: location, into: continuation)
                continuation.finish()
            }
            // Without this, navigating away leaves the fetch running and the stream alive.
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    @discardableResult
    func refresh(_ location: SavedLocation) async -> AppError? {
        guard await networkMonitor.isOnline else { return .offline }
        do {
            return try await fetchAndCache(for: location) == nil ? .notFound : nil
        } catch {
            return AppError.from(error)
        }
    }

    func clearCache() async {
        try? await local.clearCache()
    }

    // MARK: - Internals

    private func emit(
        for location: SavedLocation,
        into continuation: AsyncStream<DataState<MetarReport>>.Continuation
    ) async {
        let cached = try? await local.cachedMetar(locationID: location.id)
        let stale = cached?.isStale(now: now()) ?? true

        // 1 ─ Whatever we already have, immediately and without a spinner.
        if let cached {
            continuation.yield(DataState(data: cached, isStale: stale))
        } else {
            continuation.yield(DataState(isLoading: true))
        }

        // 2 ─ Fresh enough? A METAR is issued hourly, so refetching cannot produce a newer one.
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
            if let fresh = try await fetchAndCache(for: location) {
                continuation.yield(DataState(data: fresh))
            } else {
                continuation.yield(DataState(data: cached, isStale: cached != nil, error: .notFound))
            }
        } catch {
            continuation.yield(
                DataState(data: cached, isStale: cached != nil, error: AppError.from(error))
            )
        }
    }

    /// Returns `nil` when the call succeeded but no station near the location reported.
    private func fetchAndCache(for location: SavedLocation) async throws -> MetarReport? {
        let stations = try await api.metars(boundingBox: Self.boundingBox(for: location))
        guard let report = MetarMapper.nearestReport(
            from: stations,
            latitude: location.latitude,
            longitude: location.longitude,
            cachedAt: now()
        ) else { return nil }
        try await local.upsert(report, locationID: location.id)
        return report
    }

    static func boundingBox(for location: SavedLocation) -> String {
        [
            location.latitude - searchDegrees,
            location.longitude - searchDegrees,
            location.latitude + searchDegrees,
            location.longitude + searchDegrees,
        ]
        .map { String(format: "%.3f", $0) }
        .joined(separator: ",")
    }

    /// Roughly 110 km either side at the equator, less further north.
    ///
    /// Wide enough that anywhere with an airport at a sensible distance finds one, narrow enough
    /// that the response stays small, London's box returns seven stations.
    private static let searchDegrees = 1.0
}
