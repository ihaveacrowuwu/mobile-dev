package com.nauhaan.skycast.data.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.core.common.NetworkMonitor
import com.nauhaan.skycast.data.local.dao.SpaceWeatherCacheDao
import com.nauhaan.skycast.data.mapper.SpaceWeatherMapper
import com.nauhaan.skycast.data.remote.ErrorMapper
import com.nauhaan.skycast.data.remote.SpaceWeatherApi
import com.nauhaan.skycast.domain.model.SpaceWeather
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.SpaceWeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offline-first read algorithm, applied to Kp.
 *
 * The same five steps in the same order as the weather and METAR repositories: show the cache, stop if it is
 * fresh, treat offline-with-a-cache as normal, show a subtle refresh over existing data, and on failure keep
 * the cache and attach the error.
 *
 * Two things are specific to this source:
 *
 * - **No location.** One reading serves every saved place, so there is one row and no cache key. The part
 *   that makes it local, "is it worth looking from *here*", is computed on the device by `AuroraCalculator`.
 * - **A successful call with no measured entry is [AppError.NotFound]**, not an offline error. The feed
 *   always contains observed periods, so an empty result means its shape has changed rather than that the
 *   network failed, and telling the user to check their connection would send them looking in the wrong
 *   place.
 */
@Singleton
class SpaceWeatherRepositoryImpl
@Inject
constructor(
    private val api: SpaceWeatherApi,
    private val cacheDao: SpaceWeatherCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : SpaceWeatherRepository {
    override fun observeSpaceWeather(): Flow<DataState<SpaceWeather>> = flow {
        val cached = cacheDao.observe().first()?.let(SpaceWeatherMapper::toDomain)
        val now = clock.instant()
        val stale = cached?.isStale(now) ?: true

        // 1 ─ Show whatever we already have, immediately and without a spinner.
        if (cached != null) {
            emit(DataState.success(cached).copy(isStale = stale))
        } else {
            emit(DataState.loading())
        }

        // 2 ─ Fresh enough? Kp is issued every three hours; refetching inside the TTL cannot produce more.
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
            val fresh = fetchAndCache()
            if (fresh == null) {
                emit(DataState.failure(AppError.NotFound, cached = cached, stale = cached != null))
            } else {
                emit(DataState.success(fresh))
            }
        } catch (throwable: Throwable) {
            emit(DataState.failure(ErrorMapper.map(throwable), cached = cached, stale = cached != null))
        }
    }.flowOn(dispatchers.io)

    override suspend fun refresh(): AppError? = withContext(dispatchers.io) {
        if (!networkMonitor.isOnline.first()) return@withContext AppError.Offline
        try {
            if (fetchAndCache() == null) AppError.NotFound else null
        } catch (throwable: Throwable) {
            ErrorMapper.map(throwable)
        }
    }

    override suspend fun clearCache(): Unit = withContext(dispatchers.io) { cacheDao.clear() }

    /** Returns `null` when the call succeeded but the feed carried no measured period. */
    private suspend fun fetchAndCache(): SpaceWeather? {
        val entries = api.getKpForecast()
        val weather = SpaceWeatherMapper.toDomain(entries, cachedAt = clock.instant()) ?: return null
        cacheDao.upsert(SpaceWeatherMapper.toEntity(weather))
        return weather
    }
}
