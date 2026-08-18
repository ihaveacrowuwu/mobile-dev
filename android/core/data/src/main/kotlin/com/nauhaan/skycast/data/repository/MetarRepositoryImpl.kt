package com.nauhaan.skycast.data.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.core.common.NetworkMonitor
import com.nauhaan.skycast.data.local.dao.MetarCacheDao
import com.nauhaan.skycast.data.mapper.MetarMapper
import com.nauhaan.skycast.data.mapper.toDomain
import com.nauhaan.skycast.data.mapper.toEntity
import com.nauhaan.skycast.data.remote.AviationWeatherApi
import com.nauhaan.skycast.data.remote.ErrorMapper
import com.nauhaan.skycast.domain.model.MetarReport
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.MetarRepository
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
 * The offline-first read algorithm, applied to METARs.
 *
 * The same five steps as `WeatherRepositoryImpl`, in the same order: show the cache, stop if it is
 * fresh, treat offline-with-a-cache as normal, show a subtle refresh over existing data, and on
 * failure keep the cache and attach the error.
 *
 * Two things are specific to this source:
 *
 * - There is no "nearest station" endpoint, so a fetch asks for every station in a box around the
 *   location and [MetarMapper] picks the closest. The box is [SEARCH_DEGREES] either side, because
 *   a tighter box around a place with no nearby airport comes back empty, which is indistinguishable
 *   from a network failure.
 * - A successful call that yields no usable station is [AppError.NotFound], not an offline error,
 *   since it is a fact about the place rather than the network.
 */
@Singleton
class MetarRepositoryImpl
@Inject
constructor(
    private val api: AviationWeatherApi,
    private val cacheDao: MetarCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : MetarRepository {
    override fun observeNearestMetar(location: SavedLocation): Flow<DataState<MetarReport>> = flow {
        val cached = cacheDao.observe(location.id).first()?.toDomain()
        val now = clock.instant()
        val stale = cached?.isStale(now) ?: true

        // 1 ─ Show whatever we already have, immediately and without a spinner.
        if (cached != null) {
            emit(DataState.success(cached).copy(isStale = stale))
        } else {
            emit(DataState.loading())
        }

        // 2 ─ Fresh enough? A METAR is issued hourly, so refetching cannot produce a newer one.
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
            val fresh = fetchAndCache(location)
            if (fresh == null) {
                emit(DataState.failure(AppError.NotFound, cached = cached, stale = cached != null))
            } else {
                emit(DataState.success(fresh))
            }
        } catch (throwable: Throwable) {
            emit(DataState.failure(ErrorMapper.map(throwable), cached = cached, stale = cached != null))
        }
    }.flowOn(dispatchers.io)

    override suspend fun refresh(location: SavedLocation): AppError? = withContext(dispatchers.io) {
        if (!networkMonitor.isOnline.first()) return@withContext AppError.Offline
        try {
            if (fetchAndCache(location) == null) AppError.NotFound else null
        } catch (throwable: Throwable) {
            ErrorMapper.map(throwable)
        }
    }

    override suspend fun clearCache(): Unit = withContext(dispatchers.io) { cacheDao.clear() }

    /** Returns `null` when the call succeeded but no station near the location reported. */
    private suspend fun fetchAndCache(location: SavedLocation): MetarReport? {
        val stations = api.getMetars(bbox = location.boundingBox())
        val report = MetarMapper.nearestReport(
            stations = stations,
            latitude = location.latitude,
            longitude = location.longitude,
            cachedAt = clock.instant(),
        ) ?: return null
        cacheDao.upsert(report.toEntity(location.id))
        return report
    }

    private fun SavedLocation.boundingBox(): String = listOf(
        latitude - SEARCH_DEGREES,
        longitude - SEARCH_DEGREES,
        latitude + SEARCH_DEGREES,
        longitude + SEARCH_DEGREES,
    ).joinToString(",") { BOX_FORMAT.format(it) }

    private companion object {
        /**
         * Roughly 110 km either side at the equator, less further north.
         *
         * Wide enough that anywhere with an airport at a sensible distance finds one, narrow enough
         * that the response stays small, London's box returns seven stations.
         */
        const val SEARCH_DEGREES = 1.0

        /** Three decimals is about 100 m, far finer than a bounding box needs. */
        const val BOX_FORMAT = "%.3f"
    }
}
