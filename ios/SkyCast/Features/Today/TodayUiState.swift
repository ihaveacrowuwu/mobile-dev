import Foundation

/// Everything the Today screen renders, one immutable value, one source of truth.
///
/// The computed properties at the bottom are the important part. They encode the
/// offline-first presentation rules **once**, so the view contains no
/// `if isLoading && weather == nil && !isRefreshing` tangles and cannot accidentally show
/// a spinner over perfectly good cached data.
///
/// Identical in shape to `TodayUiState.kt` on Android.
struct TodayUiState: Equatable {
    var location: SavedLocation?
    var weather: Weather?
    var preferences: UserPreferences = .init()
    var isLoading = false
    var isRefreshing = false
    var isStale = false
    var error: AppError?
    var hasNoLocation = false
    /// Set when the user dismisses the stale/offline banner; reset on the next refresh.
    var isBannerDismissed = false

    /// Blocking spinner **only** when there is genuinely nothing to render.
    var showsFullScreenLoader: Bool {
        isLoading && weather == nil
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

    /// Non-blocking banner over existing content: either the refresh failed, or the cache
    /// is past its TTL and we could not update it.
    var showsStaleBanner: Bool {
        showsContent && !isBannerDismissed && (error != nil || isStale)
    }

    /// Copy for the banner. Prefers the specific error, falling back to a generic
    /// staleness note when the data is merely old rather than un-refreshable.
    var staleBannerMessage: String {
        error?.message ?? "Showing saved data, it may be out of date."
    }
}
