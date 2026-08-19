package com.nauhaan.skycast.data.remote

import com.nauhaan.skycast.data.remote.dto.KpForecastEntryDto
import retrofit2.http.GET

/**
 * NOAA's Space Weather Prediction Center.
 *
 * One endpoint, and it is unusually well suited to this app: the planetary-K-index forecast returns the last
 * few days of *observed* Kp and the next three days of *predicted* Kp in one seven-kilobyte array. That is
 * both halves of the question, what the field is doing now, and whether tonight is worth staying up for.
 *
 * No API key, like the aviation service. See [com.nauhaan.skycast.core.network.di.Noaa].
 */
interface SpaceWeatherApi {
    /**
     * The three-hourly planetary K index, observed and forecast.
     *
     * Entries carry an `observed` field of `observed`, `estimated` or `predicted`, which is how the past is
     * told from the future, the timestamps alone would not do it, because the feed is not trimmed to now.
     */
    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun getKpForecast(): List<KpForecastEntryDto>
}
