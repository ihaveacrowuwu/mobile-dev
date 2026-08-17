package com.nauhaan.skycast.core.designsystem.component

/**
 * One labelled reading, e.g. "Humidity" / "69%".
 *
 * Both fields are already-formatted display strings. Formatting is a presentation decision, how
 * many decimal places, which unit symbol, what time format, so it happens in the `ui` layer
 * (`WeatherDetails.kt`) and the design system just renders what it is given.
 */
data class WeatherDetail(val label: String, val value: String)
