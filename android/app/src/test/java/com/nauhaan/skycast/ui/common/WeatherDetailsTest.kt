package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.domain.model.PressureUnit
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.VisibilityUnit
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.testing.sampleWeather
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * [toDetails] formatting, with an emphasis on the timezone.
 *
 * Guards against sunrise and sunset being formatted with `ZoneId.systemDefault()`, which would show
 * London's 04:49 sunrise as 09:49 on a phone five hours ahead.
 *
 * The JVM default zone is set to UTC+5 here. In UTC the correct and incorrect implementations
 * produce identical output, so a test that does not move the default zone cannot fail either way.
 */
class WeatherDetailsTest {
    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Indian/Maldives")) // UTC+5
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private val labels = WeatherDetailLabels(
        humidity = "Humidity",
        wind = "Wind",
        pressure = "Pressure",
        visibility = "Visibility",
        sunrise = "Sunrise",
        sunset = "Sunset",
        dewPoint = "Dew point",
        daylight = "Daylight",
    )

    private fun details(
        zoneOffset: ZoneOffset,
        preferences: UserPreferences = UserPreferences(),
        includeDerived: Boolean = false,
    ) = sampleWeather(zoneOffset = zoneOffset)
        .copy(
            // 04:49 UTC. In London (UTC+1 in summer) that is 05:49; on a UTC+5 device, 09:49.
            sunrise = Instant.parse("2026-06-21T04:49:00Z"),
            sunset = Instant.parse("2026-06-21T20:21:00Z"),
        )
        .toDetails(preferences = preferences, labels = labels, includeDerived = includeDerived)
        .associate { it.label to it.value }

    @Test
    fun `sunrise and sunset render in the location's zone`() {
        val formatted = details(ZoneOffset.ofHours(1))

        // London's own clock, not the device's: the device would say 09:49.
        assertEquals("05:49", formatted["Sunrise"])
        assertEquals("21:21", formatted["Sunset"])
    }

    @Test
    fun `a different location zone produces a different time from the same instant`() {
        val london = details(ZoneOffset.ofHours(1))["Sunrise"]
        val male = details(ZoneOffset.ofHours(5))["Sunrise"]

        // Same `Instant`, two places: the formatted strings must differ. This is the property that
        // breaks when the offset is ignored, since then both would read as the device's zone.
        assertEquals("09:49", male)
        assertEquals(false, london == male)
    }

    @Test
    fun `aviation units format the way a pilot expects to read them`() {
        val formatted = details(
            ZoneOffset.UTC,
            UserPreferences(
                windSpeedUnit = WindSpeedUnit.KNOTS,
                pressureUnit = PressureUnit.INCHES_OF_MERCURY,
                visibilityUnit = VisibilityUnit.NAUTICAL_MILES,
            ),
        )

        // The fixture is 4.5 m/s, 1013 hPa, 10 000 m.
        assertEquals("8.7 kt", formatted["Wind"])
        // Two decimals, because an altimeter setting is always quoted that way. 29.91, not the
        // familiar 29.92: that figure comes from the standard atmosphere's 1013.25 hPa, and this
        // fixture is a plain 1013. `UnitConversionTest` pins the 1013.25 → 29.92 relationship.
        assertEquals("29.91 inHg", formatted["Pressure"])
        assertEquals("5.4 NM", formatted["Visibility"])
    }

    @Test
    fun `beaufort is a whole force, never a decimal`() {
        val formatted = details(
            ZoneOffset.UTC,
            UserPreferences(windSpeedUnit = WindSpeedUnit.BEAUFORT),
        )

        // 4.5 m/s is a gentle breeze: force 3. The scale has no fractional forces, so the unit
        // declares itself whole-numbered.
        assertEquals("3 Bft", formatted["Wind"])
    }

    @Test
    fun `hectopascals stay whole while inches of mercury keep two decimals`() {
        assertEquals(
            "1013 hPa",
            details(ZoneOffset.UTC, UserPreferences(pressureUnit = PressureUnit.HECTOPASCALS))["Pressure"],
        )
        assertEquals(
            "760 mmHg",
            details(
                ZoneOffset.UTC,
                UserPreferences(pressureUnit = PressureUnit.MILLIMETRES_OF_MERCURY),
            )["Pressure"],
        )
    }

    @Test
    fun `readings are formatted for display, not dumped raw`() {
        val formatted = details(ZoneOffset.UTC)

        assertEquals("60%", formatted["Humidity"])
        assertEquals("4.5 m/s", formatted["Wind"])
        assertEquals("1013 hPa", formatted["Pressure"])
        // Metres in the API, kilometres for people.
        assertEquals("10.0 km", formatted["Visibility"])
    }
}
