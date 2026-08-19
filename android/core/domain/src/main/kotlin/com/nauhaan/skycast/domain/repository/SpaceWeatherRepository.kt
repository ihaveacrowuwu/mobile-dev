package com.nauhaan.skycast.domain.repository

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.SpaceWeather
import kotlinx.coroutines.flow.Flow

/**
 * The state of the Earth's magnetic field, offline-first.
 *
 * Same contract as the others: emit the cache immediately, then attempt a refresh, and never
 * discard a cached reading because the network failed. **There is no location parameter.** Kp is a
 * property of the planet, so one reading serves every saved place, and the calculation that makes
 * it local to a place happens on the device (`AuroraCalculator`).
 */
interface SpaceWeatherRepository {
    fun observeSpaceWeather(): Flow<DataState<SpaceWeather>>

    /** Forces a fetch, bypassing the TTL. Returns the error, or `null` on success. */
    suspend fun refresh(): AppError?

    /** Drops the cached reading. Called from Settings alongside the weather cache. */
    suspend fun clearCache()
}
