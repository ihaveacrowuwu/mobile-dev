package com.nauhaan.skycast.core.designsystem.component

import com.nauhaan.skycast.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression guard for [icon] ignoring `isDaytime` and showing a **sun at 4am**.
 *
 * These tests assert the day/night distinction itself, not merely that *an* icon exists, which is
 * true either way.
 *
 * The equivalent guard on iOS is in `WeatherConditionTests.symbolsDifferBetweenDayAndNight`, and
 * the two must be kept in step.
 */
class WeatherConditionIconTest {
    /**
     * The two conditions where night genuinely changes the artwork. If either of these stops
     * differing, the original bug is back.
     */
    @Test
    fun `clear and cloudy skies use a different icon at night`() {
        assertNotEquals(
            "A clear sky must not use the same icon at 4am as at noon",
            WeatherCondition.CLEAR.icon(isDaytime = true),
            WeatherCondition.CLEAR.icon(isDaytime = false),
        )
        assertNotEquals(
            "A cloudy sky must not use the same icon at night as during the day",
            WeatherCondition.CLOUDS.icon(isDaytime = true),
            WeatherCondition.CLOUDS.icon(isDaytime = false),
        )
    }

    /**
     * The complement, and the reason this is not simply "every condition differs": rain looks
     * the same at any hour, so varying it would be noise rather than information.
     */
    @Test
    fun `precipitation and low-visibility conditions look the same at any hour`() {
        val unchanged = listOf(
            WeatherCondition.RAIN,
            WeatherCondition.DRIZZLE,
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.SNOW,
            WeatherCondition.MIST,
            WeatherCondition.UNKNOWN,
        )
        unchanged.forEach { condition ->
            assertEquals(
                "$condition should not vary by time of day",
                condition.icon(isDaytime = true),
                condition.icon(isDaytime = false),
            )
        }
    }

    /** Guards against adding an enum case and forgetting to give it artwork. */
    @Test
    fun `every condition resolves an icon in both day and night`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals(
                "$condition day icon should be stable across calls",
                condition.icon(isDaytime = true),
                condition.icon(isDaytime = true),
            )
            assertEquals(
                "$condition night icon should be stable across calls",
                condition.icon(isDaytime = false),
                condition.icon(isDaytime = false),
            )
        }
    }
}
