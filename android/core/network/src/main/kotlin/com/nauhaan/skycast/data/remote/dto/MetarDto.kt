package com.nauhaan.skycast.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * One station's METAR, as `aviationweather.gov` returns it.
 *
 * Two fields are typed as [JsonElement] rather than a number:
 *
 * - `visib` comes back as `6`, `3.5`, or the string `"6+"`, the last meaning "at least six miles".
 * - `wdir` is a bearing, except when the wind is variable, when it is the string `"VRB"`.
 *
 * Declaring either as `Double` fails to parse a real response, and declaring them as `String` fails
 * on the numeric case. Keeping the raw element and interpreting it in the mapper means an
 * unexpected shape degrades to `null` instead of discarding the whole report.
 */
@Serializable
data class MetarDto(
    @SerialName("icaoId") val icaoId: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("lat") val latitude: Double? = null,
    @SerialName("lon") val longitude: Double? = null,
    @SerialName("elev") val elevationMetres: Int? = null,
    /** Epoch seconds. */
    @SerialName("obsTime") val observedAtEpochSeconds: Long? = null,
    @SerialName("temp") val temperatureCelsius: Double? = null,
    @SerialName("dewp") val dewPointCelsius: Double? = null,
    @SerialName("wdir") val windDirection: JsonElement? = null,
    @SerialName("wspd") val windSpeedKnots: Int? = null,
    @SerialName("visib") val visibility: JsonElement? = null,
    /** Hectopascals, the `Q` group. */
    @SerialName("altim") val altimeterHectopascals: Double? = null,
    @SerialName("clouds") val clouds: List<CloudLayerDto> = emptyList(),
    @SerialName("fltCat") val flightCategory: String? = null,
    @SerialName("rawOb") val raw: String? = null,
) {
    /**
     * A bearing, or `null` when the report says the wind is variable.
     *
     * `"VRB"` is genuinely absent information, so it maps to `null` rather than to 0, which would
     * be north, and wrong.
     */
    val windDirectionDegrees: Int?
        get() = (windDirection as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    /**
     * Visibility in statute miles.
     *
     * The digits of `"6+"` are kept and the "+" recorded separately by [visibilityIsOrGreater], so
     * the UI can render "6+ mi" rather than silently claiming exactly six.
     */
    val visibilityStatuteMiles: Double?
        get() = (visibility as? JsonPrimitive)?.let {
            it.doubleOrNull ?: it.content.trimEnd('+').toDoubleOrNull()
        }

    /** Whether the visibility figure is a floor rather than a measurement. */
    val visibilityIsOrGreater: Boolean
        get() = (visibility as? JsonPrimitive)?.content?.endsWith("+") == true
}

@Serializable
data class CloudLayerDto(
    @SerialName("cover") val cover: String? = null,
    /** Feet above the field. Absent for a clear sky. */
    @SerialName("base") val baseFeet: Int? = null,
)
