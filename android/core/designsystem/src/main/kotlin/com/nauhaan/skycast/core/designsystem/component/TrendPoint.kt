package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * One point on the temperature trend.
 *
 * [value] is already in the user's unit and [valueLabel] already formatted, the design system
 * plots and prints what it is given, exactly as [WeatherDetail] does, so unit choices stay in one
 * place in the feature layer.
 */
@Immutable
data class TrendPoint(
    val value: Double,
    val valueLabel: String,
    /** Printed under the axis, for the first reading of each day. `null` for every other point. */
    val dayLabel: String? = null,
)
