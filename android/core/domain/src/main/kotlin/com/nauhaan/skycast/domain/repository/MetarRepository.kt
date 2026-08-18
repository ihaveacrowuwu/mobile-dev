package com.nauhaan.skycast.domain.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.MetarReport
import com.nauhaan.skycast.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

/**
 * The nearest airport's METAR for a saved location, offline-first.
 *
 * Same contract as [WeatherRepository]: emit the cache immediately, then attempt a refresh, and never
 * discard a cached observation because the network failed. A METAR an hour old is still a real
 * observation, arguably more clearly so than a current-conditions reading, since it is stamped with
 * the time it was taken.
 */
interface MetarRepository {
    fun observeNearestMetar(location: SavedLocation): Flow<DataState<MetarReport>>

    /** Forces a fetch, bypassing the TTL. Returns the error, or `null` on success. */
    suspend fun refresh(location: SavedLocation): AppError?

    /** Drops every cached observation. Called from Settings alongside the weather cache. */
    suspend fun clearCache()
}
