import Foundation

/// One saved location's current conditions and forecast.
///
/// Mirrors `TodayLocationWeather` on Android.
struct HomePage: Equatable, Identifiable {
    let location: SavedLocation
    var weather: DataState<Weather> = .init()
    var forecast: DataState<Forecast> = .init()

    var id: Int64 {
        location.id
    }
}

/// Everything the Home screen renders, one immutable value, one source of truth.
///
/// The computed properties are the important part. They encode the offline-first presentation
/// rules **once**, so the view contains no `if isLoading && weather == nil && !isRefreshing`
/// tangles and cannot accidentally show a spinner over perfectly good cached data.
///
/// Home shows one of several saved places at a time, ``pages`` holds them all in the order the
/// Locations tab lists them, and ``selectedIndex`` says which is on screen. The screen-level flags
/// below all describe **the selected page**, so the view never indexes into the list itself.
///
/// Identical in shape to `HomeUiState.kt` on Android.
struct HomeUiState: Equatable {
    var pages: [HomePage] = []
    var selectedIndex: Int = 0
    var preferences: UserPreferences = .init()
    var isLoading = false
    var isRefreshing = false
    var hasNoLocation = false
    /// Set when the user dismisses the stale/offline banner; reset on the next refresh.
    var isBannerDismissed = false
    /// The error from the most recent manual refresh, if it failed.
    var refreshError: AppError?

    /// The page currently on screen, or `nil` before anything has loaded.
    var selected: HomePage? {
        pages.indices.contains(selectedIndex) ? pages[selectedIndex] : nil
    }

    var location: SavedLocation? {
        selected?.location
    }

    var weather: Weather? {
        selected?.weather.data
    }

    var error: AppError? {
        selected?.weather.error ?? refreshError
    }

    var isStale: Bool {
        selected?.weather.isStale ?? false
    }

    /// The hours worth showing on the strip: from a little before now to roughly a day ahead.
    ///
    /// Readings arrive in three-hour steps, so a couple of past entries plus the next eight give
    /// the user something to swipe through without the strip becoming a second forecast screen.
    /// Past hours are kept so that "it was 4° colder this morning" is available as context.
    ///
    /// Takes the **page** rather than the selected one, so every page draws its own strip.
    func hourlyWindow(for page: HomePage, now: Date = .now) -> [HourlyForecast] {
        let hours = page.forecast.data?.days.flatMap(\.hourly) ?? []
        guard !hours.isEmpty else { return [] }

        let firstUpcoming = hours.firstIndex { $0.time >= now }
        // All readings in the past means a stale cache; show the tail rather than nothing.
        let anchor = firstUpcoming ?? hours.index(before: hours.endIndex)
        let from = max(hours.startIndex, anchor - Self.pastHoursShown)
        let until = min(hours.endIndex, anchor + Self.futureHoursShown)
        return Array(hours[from..<until])
    }

    /// Blocking spinner **only** when there is genuinely nothing to render.
    var showsFullScreenLoader: Bool {
        isLoading && weather == nil && !hasNoLocation
    }

    /// Blocking error **only** when no cache exists to fall back on.
    var showsFullScreenError: Bool {
        error != nil && weather == nil && !hasNoLocation
    }

    /// The "add your first location" prompt, an empty state, not a failure.
    var showsEmptyState: Bool {
        hasNoLocation && !isLoading
    }

    /// Content is renderable.
    var showsContent: Bool {
        weather != nil
    }

    /// Only worth showing the pager dots when there is somewhere else to go.
    var showsPageIndicator: Bool {
        pages.count > 1
    }

    /// Non-blocking banner over existing content: either the refresh failed, or the cache is past
    /// its TTL and we could not update it.
    var showsStaleBanner: Bool {
        showsContent && !isBannerDismissed && (error != nil || isStale)
    }

    /// Copy for the banner. Prefers the specific error, falling back to a generic staleness note
    /// when the data is merely old rather than un-refreshable.
    var staleBannerMessage: String {
        error?.message ?? "Showing saved data. It may be out of date."
    }

    /// Two three-hourly readings back, enough for "this morning" without clutter.
    private static let pastHoursShown = 2

    /// Eight three-hourly readings ahead, so the strip covers the next day.
    private static let futureHoursShown = 8
}
