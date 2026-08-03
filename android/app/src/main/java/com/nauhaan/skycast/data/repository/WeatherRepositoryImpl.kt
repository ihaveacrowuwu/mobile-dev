package com.nauhaan.skycast.data.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.core.common.NetworkMonitor
import com.nauhaan.skycast.data.local.dao.WeatherCacheDao
import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomain
import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomainForecast
import com.nauhaan.skycast.data.mapper.WeatherMapper.toEntities
import com.nauhaan.skycast.data.mapper.WeatherMapper.toEntity
import com.nauhaan.skycast.data.remote.ErrorMapper
import com.nauhaan.skycast.data.remote.OpenWeatherApi
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first weather.
 *
 * Implements the offline-first read algorithm. The two properties that
 * matter most, and that the unit tests assert:
 *
 * 1. **Cached data is emitted before any network call**, so a warm start never shows
 *    a spinner.
 * 2. **A failed refresh never clears the cache.** The error is emitted *alongside*
 *    the stale data so the UI can show a banner rather than an empty screen.
 *
 * [Clock] is injected rather than calling `Instant.now()`, so TTL and staleness
 * behaviour is testable without sleeping.
 */
@Singleton
class WeatherRepositoryImpl
@Inject
constructor(
    private val api: OpenWeatherApi,
    private val cacheDao: WeatherCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : WeatherRepository {
    override fun observeCurrentWeather(location: SavedLocation): Flow<DataState<Weather>> = flow {
        val cached = cacheDao.getWeather(location.id)?.toDomain()
        val now = clock.instant()
        val stale = cached?.isStale(now) ?: true

        // 1 ─ Show whatever we already have, immediately and without a spinner.
        if (cached != null) {
            emit(DataState.success(cached).copy(isStale = stale))
        } else {
            emit(DataState.loading())
        }

        // 2 ─ Fresh enough? Then we are done; do not spend the user's data or our quota.
        if (cached != null && !stale) return@flow

        // 3 ─ Offline with a cache is a normal state, not an error worth interrupting for.
        if (!networkMonitor.isOnline.first()) {
            emit(
                if (cached != null) {
                    DataState.failure(AppError.Offline, cached = cached, stale = true)
                } else {
                    DataState.failure(AppError.Offline)
                },
            )
            return@flow
        }

        // 4 ─ Refreshing over existing data: subtle indicator, content stays visible.
        emit(DataState.refreshing(cached, stale = stale))

        // 5 ─ Fetch, persist, emit. On failure keep the cache and attach the error.
        try {
            val fresh = fetchAndCacheWeather(location)
            emit(DataState.success(fresh))
        } catch (throwable: Throwable) {
            val error = ErrorMapper.map(throwable)
            emit(DataState.failure(error, cached = cached, stale = cached != null))
        }
    }.flowOn(dispatchers.io)

    override fun observeForecast(location: SavedLocation): Flow<DataState<Forecast>> = flow {
        val cached =
            cacheDao
                .observeForecast(location.id)
                .map { it.toDomainForecast() }
                .first()
        val now = clock.instant()
        val stale = cached?.isStale(now) ?: true

        if (cached != null) {
            emit(DataState.success(cached).copy(isStale = stale))
        } else {
            emit(DataState.loading())
        }

        if (cached != null && !stale) return@flow

        if (!networkMonitor.isOnline.first()) {
            emit(
                if (cached != null) {
                    DataState.failure(AppError.Offline, cached = cached, stale = true)
                } else {
                    DataState.failure(AppError.Offline)
                },
            )
            return@flow
        }

        emit(DataState.refreshing(cached, stale = stale))

        try {
            val fresh = fetchAndCacheForecast(location)
            emit(DataState.success(fresh))
        } catch (throwable: Throwable) {
            val error = ErrorMapper.map(throwable)
            emit(DataState.failure(error, cached = cached, stale = cached != null))
        }
    }.flowOn(dispatchers.io)

    override suspend fun refresh(location: SavedLocation): AppError? = withContext(dispatchers.io) {
        if (!networkMonitor.isOnline.first()) return@withContext AppError.Offline
        try {
            // Pull-to-refresh should update both tabs, not just the visible one.
            fetchAndCacheWeather(location)
            fetchAndCacheForecast(location)
            null
        } catch (throwable: Throwable) {
            ErrorMapper.map(throwable)
        }
    }

    override suspend fun clearCache() = withContext(dispatchers.io) {
        cacheDao.clearAll()
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private suspend fun fetchAndCacheWeather(location: SavedLocation): Weather {
        val dto = api.getCurrentWeather(location.latitude, location.longitude)
        val weather =
            dto.toDomain(
                locationId = location.id,
                locationName = location.name,
                cachedAt = clock.instant(),
            )
        cacheDao.upsertWeather(weather.toEntity())
        return weather
    }

    private suspend fun fetchAndCacheForecast(location: SavedLocation): Forecast {
        val dto = api.getForecast(location.latitude, location.longitude)
        val forecast =
            dto.toDomain(
                locationId = location.id,
                locationName = location.name,
                cachedAt = clock.instant(),
            )
        // Replace, never merge: leftover readings from a previous fetch would show
        // as phantom days once the forecast window rolls forward.
        cacheDao.replaceForecast(location.id, forecast.toEntities())
        return forecast
    }
}
