package com.nauhaan.skycast.ui.common

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

    private fun details(zoneOffset: ZoneOffset) = sampleWeather(zoneOffset = zoneOffset)
        .copy(
            // 04:49 UTC. In London (UTC+1 in summer) that is 05:49; on a UTC+5 device, 09:49.
            sunrise = Instant.parse("2026-06-21T04:49:00Z"),
            sunset = Instant.parse("2026-06-21T20:21:00Z"),
        )
        .toDetails(
            windUnit = WindSpeedUnit.METRES_PER_SECOND,
            humidityLabel = "Humidity",
            windLabel = "Wind",
            pressureLabel = "Pressure",
            visibilityLabel = "Visibility",
            sunriseLabel = "Sunrise",
            sunsetLabel = "Sunset",
        )
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
    fun `readings are formatted for display, not dumped raw`() {
        val formatted = details(ZoneOffset.UTC)

        assertEquals("60%", formatted["Humidity"])
        assertEquals("4.5 m/s", formatted["Wind"])
        assertEquals("1013 hPa", formatted["Pressure"])
        // Metres in the API, kilometres for people.
        assertEquals("10.0 km", formatted["Visibility"])
    }
}
