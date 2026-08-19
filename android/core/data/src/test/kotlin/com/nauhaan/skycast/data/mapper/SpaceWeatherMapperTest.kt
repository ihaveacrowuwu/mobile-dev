package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.remote.dto.KpForecastEntryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The Kp mapper, against a response captured from the live NOAA feed.
 *
 * The fixture is real, not hand-written, and is shared byte-for-byte with
 * `ios/SkyCastTests/Fixtures/`. It was captured during a **G1 storm**: Kp 5.0 observed at 03:00 UTC
 * on 19 August 2026, with a `noaa_scale` of "G1", which exercises the storm-level branch that is
 * null in most samples.
 *
 * The expected values are identical to `SpaceWeatherMapperTest.swift`.
 */
class SpaceWeatherMapperTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val cachedAt = Instant.parse("2026-08-19T04:00:00Z")

    private fun entries(): List<KpForecastEntryDto> =
        json.decodeFromString(javaClass.getResource("/fixtures/kp_forecast.json")!!.readText())

    @Test
    fun `the real feed decodes and yields the latest measured period`() {
        val weather = SpaceWeatherMapper.toDomain(entries(), cachedAt)
        assertNotNull(weather)

        // The last entry the feed calls "observed", a G1 storm, which is what makes this fixture useful.
        assertEquals(5.0, weather!!.kpNow, 0.001)
        assertEquals(Instant.parse("2026-08-19T03:00:00Z"), weather.observedAt)
        assertEquals("G1", weather.stormLevel)
    }

    @Test
    fun `timestamps are read as UTC, not as local time`() {
        // The feed's `time_tag` has no zone suffix. Parsing it in the device's zone would shift every reading
        // by the offset, five hours in this project's own test environment, which would attribute tonight's
        // storm to this afternoon.
        val weather = SpaceWeatherMapper.toDomain(entries(), cachedAt)!!
        assertEquals("2026-08-19T03:00:00Z", weather.observedAt.toString())
    }

    @Test
    fun `the future comes from the observed field, not from comparing timestamps`() {
        // The feed is not trimmed to now: it carries "estimated" periods whose timestamps are already in the
        // past. Splitting on the clock would file them as history and lose the next few hours entirely.
        val weather = SpaceWeatherMapper.toDomain(entries(), cachedAt)!!

        assertTrue("expected forecast periods", weather.upcoming.isNotEmpty())
        // Every forecast period is at or after the last observed one, and they are in order.
        assertTrue(weather.upcoming.all { !it.time.isBefore(weather.observedAt) })
        assertEquals(weather.upcoming.sortedBy { it.time }, weather.upcoming)
    }

    @Test
    fun `tonight's peak comes from the forecast window`() {
        val weather = SpaceWeatherMapper.toDomain(entries(), cachedAt)!!
        val peak = weather.peakAhead()
        assertNotNull(peak)
        // The captured feed forecasts a maximum of Kp 4.67 in the next day.
        assertEquals(4.67, peak!!.kp, 0.001)
    }

    @Test
    fun `a feed with nothing measured maps to null rather than to a guess`() {
        // Every entry predicted: a shape change, not a quiet day. Inventing a "current" Kp from a forecast
        // would put a number on screen that nobody measured.
        val predictedOnly = entries().filterNot { it.observed == "observed" }
        assertNull(SpaceWeatherMapper.toDomain(predictedOnly, cachedAt))
    }

    @Test
    fun `a round trip through the cache loses nothing`() {
        val original = SpaceWeatherMapper.toDomain(entries(), cachedAt)!!
        val restored = SpaceWeatherMapper.toDomain(SpaceWeatherMapper.toEntity(original))

        // Including the storm level and every forecast period, the encoded string is the one place this
        // could silently drop data, since it is parsed by splitting rather than by a schema.
        assertEquals(original.kpNow, restored.kpNow, 0.001)
        assertEquals(original.observedAt, restored.observedAt)
        assertEquals(original.stormLevel, restored.stormLevel)
        assertEquals(original.upcoming, restored.upcoming)
        assertEquals(original.cachedAt, restored.cachedAt)
    }

    @Test
    fun `a reading with no storm level round trips as null, not as an empty string`() {
        // The common case, and the one the encoding could corrupt: an empty field between separators has to
        // come back as absent rather than as a storm called "".
        val quiet = SpaceWeatherMapper.toDomain(entries(), cachedAt)!!.copy(stormLevel = null)
        val restored = SpaceWeatherMapper.toDomain(SpaceWeatherMapper.toEntity(quiet))
        assertNull(restored.stormLevel)
    }
}
