package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.runtime.Immutable
import com.nauhaan.skycast.domain.model.WeatherCondition

/**
 * One day of the forecast as a temperature *range*.
 *
 * Fractions are positions within the **whole period's** range, not within this day, that is what
 * makes the bars comparable down the column, so a cold snap on Thursday is visible as a bar that
 * sits further left rather than as two numbers the reader has to hold in their head.
 */
@Immutable
data class DayRange(
    val dayLabel: String,
    val condition: WeatherCondition,
    val isDaytime: Boolean,
    val lowLabel: String,
    val highLabel: String,
    val lowFraction: Float,
    val highFraction: Float,
    /** Already formatted, or `null` when rain is not worth mentioning. */
    val precipitationLabel: String? = null,
    /** The whole row, spoken as one sentence. */
    val contentDescription: String,
    /**
     * Identifies the day to the caller when a row is tapped.
     *
     * An epoch day rather than a formatted label: the label is localised and could collide, and the
     * navigation route needs a number anyway.
     */
    val epochDay: Long = 0,
    /** What tapping the row does, for a screen reader's action list. `null` if it does nothing. */
    val openLabel: String? = null,
)
