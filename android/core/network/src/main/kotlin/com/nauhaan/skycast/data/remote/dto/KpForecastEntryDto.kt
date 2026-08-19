package com.nauhaan.skycast.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One three-hour Kp period, as NOAA sends it.
 *
 * ```json
 * {"time_tag":"2026-08-19T03:00:00","kp":5.00,"observed":"observed","noaa_scale":"G1"}
 * ```
 *
 * Two things to know about the shape:
 *
 * - `time_tag` has **no zone suffix**, and the values are UTC. Parsing it as a local time would shift every
 *   reading by the device's offset, which is the same trap the OpenWeather DTOs document.
 * - `noaa_scale` is `null` far more often than not, it appears only when the disturbance reaches storm
 *   level, so it is nullable rather than defaulted.
 */
@Serializable
data class KpForecastEntryDto(
    @SerialName("time_tag") val timeTag: String,
    val kp: Double,
    /** `observed`, `estimated` or `predicted`. */
    val observed: String,
    @SerialName("noaa_scale") val noaaScale: String? = null,
)
