package com.nauhaan.skycast.domain.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.Weather
import kotlinx.coroutines.flow.Flow

/**
 * Weather data, offline-first.
 *
 * Implementations must follow the read algorithm documented in
 * The offline-first read algorithm: emit the cache immediately, then attempt a refresh,
 * and **never discard cached data because the network failed**.
 *
 * Declared in `domain` and implemented in `data` so that view model tests can
 * substitute a fake with no Retrofit, OkHttp or Room on the classpath.
 */
interface WeatherRepository {
    /**
     * Observes current weather for [location].
     *
     * Emits at least once. The first emission is the cached value if one exists,
     * so the UI can render without a spinner; later emissions carry the refreshed
     * value or an error that leaves the cached data intact.
     */
    fun observeCurrentWeather(location: SavedLocation): Flow<DataState<Weather>>

    /** As [observeCurrentWeather], for the multi-day forecast. */
    fun observeForecast(location: SavedLocation): Flow<DataState<Forecast>>

    /**
     * Forces a network refresh, bypassing the TTL. Used by pull-to-refresh.
     *
     * Returns the [AppError] that occurred, or `null` on success. Errors are
     * returned rather than thrown because a failed refresh is an expected,
     * recoverable outcome, not an exception.
     */
    suspend fun refresh(location: SavedLocation): AppError?

    /** Drops every cached reading. Exposed in Settings so the user can reclaim space. */
    suspend fun clearCache()
}

/**
 * A repository emission: data, whether it is being refreshed, and whether the last
 * refresh failed, all three at once.
 *
 * Modelling these as one value (rather than three separate flows) is what makes
 * "stale data plus an error banner" expressible. A plain `Result` cannot represent
 * "failed, but here is the cache", which is precisely the offline case.
 */
data class DataState<out T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: AppError? = null,
) {
    val hasData: Boolean get() = data != null

    companion object {
        fun <T> loading(): DataState<T> = DataState(isLoading = true)

        fun <T> refreshing(cached: T?, stale: Boolean = false): DataState<T> =
            DataState(data = cached, isRefreshing = true, isStale = stale)

        fun <T> success(data: T): DataState<T> = DataState(data = data)

        /** A failure that preserves whatever cached data we already had. */
        fun <T> failure(error: AppError, cached: T? = null, stale: Boolean = false): DataState<T> =
            DataState(data = cached, error = error, isStale = stale)
    }
}
