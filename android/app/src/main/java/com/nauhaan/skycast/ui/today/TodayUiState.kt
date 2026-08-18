package com.nauhaan.skycast.ui.today

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.usecase.TodayLocationWeather
import java.time.Instant

/**
 * Everything the Today screen renders, one immutable value, one source of truth.
 *
 * The derived properties are the important part. They encode the offline-first presentation rules
 * **once**, so the composable contains no `if (isLoading && weather == null && !isRefreshing)`
 * tangles and cannot accidentally show a spinner over perfectly good cached data.
 *
 * Today shows one of several saved places at a time, [pages] holds them all in the order the
 * Locations tab lists them, and [selectedIndex] says which is on screen. The screen-level flags
 * below all describe **the selected page**, so the composable never indexes into the list itself.
 */
data class TodayUiState(
    val pages: List<TodayLocationWeather> = emptyList(),
    val selectedIndex: Int = 0,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasNoLocation: Boolean = false,
    /** Set when the user dismisses the stale/offline banner; reset on the next refresh. */
    val isBannerDismissed: Boolean = false,
    /** The error from the most recent manual refresh, if it failed. */
    val refreshError: AppError? = null,
) {
    /** The page currently on screen, or `null` before anything has loaded. */
    val selected: TodayLocationWeather? get() = pages.getOrNull(selectedIndex)

    val location: SavedLocation? get() = selected?.location
    val weather: Weather? get() = selected?.weather?.data

    val error: AppError? get() = selected?.weather?.error ?: refreshError
    val isStale: Boolean get() = selected?.weather?.isStale == true

    /**
     * The hours worth showing on the strip: from a little before now to roughly a day ahead.
     *
     * Readings arrive in three-hour steps, so a couple of past entries plus the next eight give the
     * user something to swipe through without the strip becoming a second forecast screen. Past
     * hours are kept so that "it was 4° colder this morning" is available as context.
     *
     * Takes the **page** rather than the selected one, so every page draws its own strip.
     */
    fun hourlyWindow(page: TodayLocationWeather, now: Instant = Instant.now()): List<HourlyForecast> {
        val hours = page.forecast.data?.days?.flatMap { it.hourly }.orEmpty()
        if (hours.isEmpty()) return emptyList()

        val firstUpcoming = hours.indexOfFirst { !it.time.isBefore(now) }
        // All readings in the past means a stale cache; show the tail rather than nothing.
        val anchor = if (firstUpcoming >= 0) firstUpcoming else hours.size - 1
        val from = (anchor - PAST_HOURS_SHOWN).coerceAtLeast(0)
        val until = (anchor + FUTURE_HOURS_SHOWN).coerceAtMost(hours.size)
        return hours.subList(from, until)
    }

    /** Blocking spinner **only** when there is genuinely nothing to render. */
    val showsFullScreenLoader: Boolean get() = isLoading && weather == null && !hasNoLocation

    /** Blocking error **only** when no cache exists to fall back on. */
    val showsFullScreenError: Boolean get() = error != null && weather == null && !hasNoLocation

    /** The "add your first location" prompt: an empty state, not a failure. */
    val showsEmptyState: Boolean get() = hasNoLocation && !isLoading

    /** Content is renderable. */
    val showsContent: Boolean get() = weather != null

    /** Only worth showing the pager dots when there is somewhere else to go. */
    val showsPageIndicator: Boolean get() = pages.size > 1

    /**
     * Non-blocking banner over existing content: either the refresh failed, or the cache is past
     * its TTL and we could not update it.
     */
    val showsStaleBanner: Boolean
        get() = showsContent && !isBannerDismissed && (error != null || isStale)

    private companion object {
        /** Two three-hourly readings back, enough for "this morning" without clutter. */
        const val PAST_HOURS_SHOWN = 2

        /** Eight three-hourly readings ahead, so the strip covers the next day. */
        const val FUTURE_HOURS_SHOWN = 8
    }
}
