package com.nauhaan.skycast.core.designsystem.component

/**
 * One labelled reading, e.g. "Humidity" / "69%".
 *
 * [value] is an already-formatted display string. Formatting is a presentation decision, how many
 * decimal places, which unit symbol, what time format, so it happens in the `ui` layer
 * (`WeatherDetails.kt`) and the design system just renders what it is given.
 */
data class WeatherDetail(
    val label: String,
    val value: String,
    val kind: WeatherDetailKind,
    /** How the reading draws itself. See [MetricVisual]. */
    val visual: MetricVisual = MetricVisual.Plain,
)

/**
 * Which reading a tile shows.
 *
 * An enum rather than a colour or an icon passed in from the caller, so the design system owns how
 * each metric looks and the feature layer cannot accidentally give humidity two different tints on
 * two different screens.
 */
enum class WeatherDetailKind {
    HUMIDITY,
    WIND,
    PRESSURE,
    VISIBILITY,
    SUNRISE,
    SUNSET,

    /** Reported all along and never shown until the tiles could draw it. */
    CLOUD,

    // Derived rather than reported, see `Weather.dewPointCelsius` and `Weather.daylightDuration`.
    // They share the hue of the reading they are closest to: dew point is moisture, so it takes
    // humidity's blue, and daylight takes the sunrise gold.
    DEW_POINT,
    DAYLIGHT,
}
