package com.nauhaan.skycast.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Verbatim shape of `GET /data/2.5/weather`.
 *
 * DTOs mirror the wire format exactly, including OpenWeather's awkward names and
 * optional fields, and **never leave the data layer**. `WeatherMapper` converts
 * them into domain models, so an API change touches these files and the mapper only.
 */
@Serializable
data class CurrentWeatherDto(
    @SerialName("coord") val coordinates: CoordinatesDto,
    @SerialName("weather") val weather: List<WeatherDescriptionDto> = emptyList(),
    @SerialName("main") val main: MainDto,
    @SerialName("visibility") val visibility: Int = 0,
    @SerialName("wind") val wind: WindDto = WindDto(),
    @SerialName("clouds") val clouds: CloudsDto = CloudsDto(),
    @SerialName("dt") val observedAtEpochSeconds: Long,
    @SerialName("sys") val system: SystemDto,
    @SerialName("timezone") val timezoneOffsetSeconds: Int = 0,
    @SerialName("id") val cityId: Long = 0,
    @SerialName("name") val cityName: String = "",
)

@Serializable
data class CoordinatesDto(@SerialName("lon") val longitude: Double, @SerialName("lat") val latitude: Double)

@Serializable
data class WeatherDescriptionDto(
    @SerialName("id") val id: Int,
    @SerialName("main") val group: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("icon") val icon: String = "",
)

@Serializable
data class MainDto(
    @SerialName("temp") val temperature: Double,
    @SerialName("feels_like") val feelsLike: Double = 0.0,
    @SerialName("temp_min") val temperatureMin: Double = 0.0,
    @SerialName("temp_max") val temperatureMax: Double = 0.0,
    @SerialName("pressure") val pressure: Int = 0,
    @SerialName("humidity") val humidity: Int = 0,
)

@Serializable
data class WindDto(
    @SerialName("speed") val speed: Double = 0.0,
    @SerialName("deg") val degrees: Int = 0,
    // Absent in calm conditions, hence nullable rather than defaulted.
    @SerialName("gust") val gust: Double? = null,
)

@Serializable
data class CloudsDto(@SerialName("all") val cloudinessPercent: Int = 0)

@Serializable
data class SystemDto(
    @SerialName("country") val country: String = "",
    @SerialName("sunrise") val sunriseEpochSeconds: Long = 0,
    @SerialName("sunset") val sunsetEpochSeconds: Long = 0,
)
