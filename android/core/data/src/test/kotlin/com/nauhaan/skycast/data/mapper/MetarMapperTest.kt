package com.nauhaan.skycast.data.mapper

import com.nauhaan.skycast.data.remote.dto.MetarDto
import com.nauhaan.skycast.domain.model.FlightCategory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The METAR mapper, against a response captured from the live API.
 *
 * The fixture in `src/test/resources/fixtures/metar_london.json` is real, not hand-written, and is
 * shared byte-for-byte with `ios/SkyCastTests/Fixtures/`. That matters here more than usual: this
 * API types two of its fields loosely, `visib` comes back as the *string* `"6+"` and `wdir` can be
 * `"VRB"`, and a hand-written fixture would have been written with whatever shape the DTO expected,
 * proving nothing.
 */
class MetarMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }
    private val now: Instant = Instant.parse("2026-08-18T15:00:00Z")

    private fun stations(): List<MetarDto> = json.decodeFromString(fixture("metar_london.json"))

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "missing fixture: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `the real response decodes without loss`() {
        val report = MetarMapper.nearestReport(stations(), latitude = 51.5074, longitude = -0.1278, cachedAt = now)

        checkNotNull(report)
        assertTrue("raw report kept verbatim", report.raw.contains(report.stationId))
        assertEquals(FlightCategory.VFR, report.flightCategory)
        assertTrue("station named", report.stationName.isNotBlank())
        assertEquals(26.0, report.temperatureCelsius ?: 0.0, 0.001)
        assertEquals(1010.0, report.altimeterHectopascals ?: 0.0, 0.001)
    }

    @Test
    fun `a clear sky has no layers, and that is not a decoding failure`() {
        // The nearest station in the capture is London City, which reported NCD (no cloud
        // detected), so `clouds` is legitimately empty. Asserting the layers were non-empty would be
        // the wrong property: it would fail on a cloudless day and pass on a parser that invented a
        // layer.
        val report = MetarMapper.nearestReport(stations(), latitude = 51.5074, longitude = -0.1278, cachedAt = now)

        assertEquals("EGLC", report?.stationId)
        assertEquals(emptyList<Any>(), report?.clouds)
    }

    @Test
    fun `reported layers are decoded with their heights`() {
        // Gatwick in the same capture reports FEW047, so a station that does have layers proves the
        // other half of the branch above.
        val report = MetarMapper.nearestReport(stations(), latitude = 51.15, longitude = -0.18, cachedAt = now)

        assertEquals("EGKK", report?.stationId)
        val layer = checkNotNull(report?.clouds?.firstOrNull())
        assertEquals("FEW", layer.cover)
        assertEquals(4700, layer.baseFeet)
    }

    @Test
    fun `the nearest station wins, not the first in the response`() {
        // The API returns stations in no useful order, in this capture Biggin Hill is listed first
        // while Heathrow is nearer central London. Taking `first()` would have looked correct in
        // testing and quietly shown the wrong airport.
        val report = MetarMapper.nearestReport(stations(), latitude = 51.5074, longitude = -0.1278, cachedAt = now)

        val nearest = stations().minByOrNull {
            MetarMapper.distanceKm(51.5074, -0.1278, it.latitude!!, it.longitude!!)
        }
        assertEquals(nearest?.icaoId, report?.stationId)
        assertTrue("distance is recorded", (report?.distanceKm ?: 0.0) > 0.0)
    }

    @Test
    fun `a plus on the visibility is kept as a floor rather than a measurement`() {
        // "6+" means at least six miles. Reporting exactly six would be a claim the observation
        // never made, so the digits and the "+" are carried separately.
        val report = MetarMapper.nearestReport(stations(), latitude = 51.5074, longitude = -0.1278, cachedAt = now)

        assertEquals(6.0, report?.visibilityStatuteMiles ?: 0.0, 0.001)
        assertTrue(report?.visibilityIsOrGreater == true)
    }

    @Test
    fun `a variable wind direction is absent rather than north`() {
        val variable = json.decodeFromString<List<MetarDto>>(
            """[{"icaoId":"EGLL","name":"Heathrow","lat":51.4,"lon":-0.4,"wdir":"VRB",
               "rawOb":"METAR EGLL 181320Z VRB03KT","fltCat":"VFR"}]""",
        )

        val report = MetarMapper.nearestReport(variable, latitude = 51.5, longitude = -0.1, cachedAt = now)

        // Zero would be due north, which is a direction the observation explicitly declined to give.
        assertNull(report?.windDirectionDegrees)
    }

    @Test
    fun `stations with no observation are skipped rather than shown half empty`() {
        val incomplete = json.decodeFromString<List<MetarDto>>(
            """[{"icaoId":"EGLL","lat":51.4,"lon":-0.4},
                {"icaoId":"EGKK","name":"Gatwick","lat":51.15,"lon":-0.18,
                 "rawOb":"METAR EGKK 181320Z 27010KT","fltCat":"VFR"}]""",
        )

        val report = MetarMapper.nearestReport(incomplete, latitude = 51.5, longitude = -0.1, cachedAt = now)

        // Heathrow is nearer but has no report in it; an entry with no observation is not a reading.
        assertEquals("EGKK", report?.stationId)
    }

    @Test
    fun `an empty response yields nothing rather than an empty report`() {
        assertNull(MetarMapper.nearestReport(emptyList(), 51.5, -0.1, now))
    }

    @Test
    fun `distance is a great-circle figure, not a flat approximation`() {
        // London to Malé, which any plane-geometry shortcut gets badly wrong. 8,517 km is the
        // great-circle figure.
        val km = MetarMapper.distanceKm(51.5074, -0.1278, 4.1755, 73.5093)

        assertEquals(8517.0, km, 5.0)
    }
}
