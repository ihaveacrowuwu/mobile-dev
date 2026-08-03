package com.nauhaan.skycast.ui.today

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather

/**
 * Everything the Today screen renders, one immutable value, one source of truth.
 *
 * The derived properties at the bottom are the important part. They encode the
 * offline-first presentation rules **once**, so the composable contains no
 * `if (isLoading && weather == null && !isRefreshing)` tangles and cannot accidentally
 * show a spinner over perfectly good cached data.
 */
data class TodayUiState(
    val location: SavedLocation? = null,
    val weather: Weather? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: AppError? = null,
    val hasNoLocation: Boolean = false,
    /** Set when the user dismisses the stale/offline banner; reset on the next refresh. */
    val isBannerDismissed: Boolean = false,
) {
    /** Blocking spinner **only** when there is genuinely nothing to render. */
    val showsFullScreenLoader: Boolean get() = isLoading && weather == null

    /** Blocking error **only** when no cache exists to fall back on. */
    val showsFullScreenError: Boolean get() = error != null && weather == null && !hasNoLocation

    /** The "add your first location" prompt: an empty state, not a failure. */
    val showsEmptyState: Boolean get() = hasNoLocation && !isLoading

    /** Content is renderable. */
    val showsContent: Boolean get() = weather != null

    /**
     * Non-blocking banner over existing content: either the refresh failed, or the
     * cache is past its TTL and we could not update it.
     */
    val showsStaleBanner: Boolean
        get() = showsContent && !isBannerDismissed && (error != null || isStale)
}
