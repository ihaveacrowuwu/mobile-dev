package com.nauhaan.skycast.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Verbatim shape of `GET /data/2.5/forecast`, a flat list of 3-hourly readings
 * covering five days. Grouping into calendar days happens in `WeatherMapper`.
 */
@Serializable
data class ForecastResponseDto(
    @SerialName("list") val readings: List<ForecastReadingDto> = emptyList(),
    @SerialName("city") val city: ForecastCityDto,
)

@Serializable
data class ForecastReadingDto(
    @SerialName("dt") val timeEpochSeconds: Long,
    @SerialName("main") val main: MainDto,
    @SerialName("weather") val weather: List<WeatherDescriptionDto> = emptyList(),
    @SerialName("clouds") val clouds: CloudsDto = CloudsDto(),
    @SerialName("wind") val wind: WindDto = WindDto(),
    @SerialName("visibility") val visibility: Int = 0,
    /** Probability of precipitation, 0.0–1.0. */
    @SerialName("pop") val precipitationProbability: Double = 0.0,
)

@Serializable
data class ForecastCityDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("coord") val coordinates: CoordinatesDto,
    @SerialName("country") val country: String = "",
    @SerialName("timezone") val timezoneOffsetSeconds: Int = 0,
    @SerialName("sunrise") val sunriseEpochSeconds: Long = 0,
    @SerialName("sunset") val sunsetEpochSeconds: Long = 0,
)

/** One hit from `GET /geo/1.0/direct` (the geocoding endpoint). */
@Serializable
data class GeocodingResultDto(
    @SerialName("name") val name: String,
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
    @SerialName("country") val country: String = "",
    /** Present only for some countries, e.g. US states and UK constituent countries. */
    @SerialName("state") val state: String? = null,
)
