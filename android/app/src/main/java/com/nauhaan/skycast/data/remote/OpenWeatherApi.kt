package com.nauhaan.skycast.data.remote

import com.nauhaan.skycast.data.remote.dto.CurrentWeatherDto
import com.nauhaan.skycast.data.remote.dto.ForecastResponseDto
import com.nauhaan.skycast.data.remote.dto.GeocodingResultDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The OpenWeather endpoints SkyCast uses.
 *
 * The API key is **not** a parameter here, `ApiKeyInterceptor` appends it to every
 * request. That keeps the secret in exactly one place and out of every call site.
 *
 * `units=metric` is always requested so that cached values are canonically Celsius
 * and m/s; converting to the user's preferred unit is a pure presentation-layer
 * function, which means changing units works offline.
 */
interface OpenWeatherApi {
    /** Current conditions for a coordinate pair. */
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("units") units: String = METRIC,
        @Query("lang") language: String = ENGLISH,
    ): CurrentWeatherDto

    /** Five days of 3-hourly readings (40 entries) for a coordinate pair. */
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("units") units: String = METRIC,
        @Query("lang") language: String = ENGLISH,
    ): ForecastResponseDto

    /** Free-text place search. Returns up to [limit] matches, best first. */
    @GET("geo/1.0/direct")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("limit") limit: Int = DEFAULT_SEARCH_LIMIT,
    ): List<GeocodingResultDto>

    companion object {
        const val METRIC = "metric"
        const val ENGLISH = "en"
        const val DEFAULT_SEARCH_LIMIT = 8
    }
}
