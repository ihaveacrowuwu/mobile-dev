package com.nauhaan.skycast.ui.home

import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.ForecastDay
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.usecase.TodayLocationWeather
import com.nauhaan.skycast.testing.TestInstant
import com.nauhaan.skycast.testing.sampleLocation
import com.nauhaan.skycast.testing.sampleWeather
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * The hourly window, which is pure and therefore worth pinning precisely.
 *
 * The first test asserts that a page which is not the selected one still draws its own strip.
 */
class HomeUiStateTest {
    @Test
    fun `window describes the page it is given, not the selected one`() {
        val state = HomeUiState(
            pages = listOf(page(id = 1, firstTemperature = 10.0), page(id = 2, firstTemperature = 30.0)),
            selectedIndex = 0,
        )

        val offScreen = state.hourlyWindow(state.pages[1], now = TestInstant)

        assertEquals(
            listOf(30.0, 31.0, 32.0, 33.0, 34.0, 35.0, 36.0, 37.0, 38.0, 39.0),
            offScreen.map { it.temperatureCelsius },
        )
    }

    @Test
    fun `window keeps two readings behind now and runs eight ahead`() {
        val state = HomeUiState(pages = listOf(page(id = 1, firstTemperature = 0.0)))

        val window = state.hourlyWindow(state.pages[0], now = TestInstant)

        assertEquals(10, window.size)
        assertEquals(TestInstant.minusSeconds(6 * 60 * 60), window.first().time)
        assertEquals(TestInstant.plusSeconds(21 * 60 * 60), window.last().time)
    }

    @Test
    fun `a cache with nothing upcoming shows its tail rather than nothing`() {
        // Every reading behind us: a forecast cached long enough ago that it has run out.
        val stale = page(id = 1, firstTemperature = 0.0, start = TestInstant.minusSeconds(48 * 60 * 60))
        val state = HomeUiState(pages = listOf(stale))

        val window = state.hourlyWindow(stale, now = TestInstant)

        assertEquals(3, window.size)
        assertEquals(stale.forecast.data!!.days.first().hourly.last(), window.last())
    }

    @Test
    fun `a page with no forecast yet has an empty window`() {
        val page = TodayLocationWeather(
            location = sampleLocation(),
            weather = DataState(data = sampleWeather()),
            forecast = DataState(),
        )

        assertEquals(emptyList<HourlyForecast>(), HomeUiState().hourlyWindow(page, now = TestInstant))
    }

    /**
     * Twelve three-hourly readings, the third of which is [now]. Temperatures ascend from
     * [firstTemperature] so a window can be traced back to the page it came from.
     */
    private fun page(
        id: Long,
        firstTemperature: Double,
        start: Instant = TestInstant.minusSeconds(6 * 60 * 60),
    ): TodayLocationWeather {
        val hours = (0 until 12).map { index ->
            HourlyForecast(
                time = start.plusSeconds(index * 3L * 60 * 60),
                condition = WeatherCondition.CLEAR,
                iconCode = "01d",
                temperatureCelsius = firstTemperature + index,
                precipitationProbability = 0.0,
                windSpeedMetresPerSecond = 1.0,
            )
        }
        val forecast = Forecast(
            locationId = id,
            locationName = "Place $id",
            days = listOf(
                ForecastDay(
                    date = start.atZone(ZoneOffset.UTC).toLocalDate(),
                    condition = WeatherCondition.CLEAR,
                    description = "Clear sky",
                    iconCode = "01d",
                    minTemperatureCelsius = firstTemperature,
                    maxTemperatureCelsius = firstTemperature + 11,
                    precipitationProbability = 0.0,
                    hourly = hours,
                ),
            ),
            cachedAt = TestInstant,
            zoneOffset = ZoneOffset.UTC,
        )
        return TodayLocationWeather(
            location = sampleLocation(id = id, name = "Place $id"),
            weather = DataState(data = sampleWeather(locationId = id)),
            forecast = DataState(data = forecast),
        )
    }
}
