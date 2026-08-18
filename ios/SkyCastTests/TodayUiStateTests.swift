import Foundation
import Testing
@testable import SkyCast

/// The hourly window, which is pure and therefore worth pinning precisely.
///
/// The first test is the one that matters. If the window were read off the *selected* page,
/// every other page in the pager drew an empty strip and "Through the day" appeared to pop into
/// existence as a swipe finished. Nothing caught it, because nothing asked a page that was not the
/// selected one what it would draw.
///
/// Mirrors `TodayUiStateTest.kt` on Android.
@Suite("Today hourly window")
struct TodayUiStateTests {
    @Test("Window describes the page it is given, not the selected one")
    func windowFollowsItsPage() {
        let state = TodayUiState(
            pages: [page(id: 1, firstTemperature: 10), page(id: 2, firstTemperature: 30)],
            selectedIndex: 0
        )

        let offScreen = state.hourlyWindow(for: state.pages[1], now: Fixtures.now)

        #expect(offScreen.map(\.temperatureCelsius) == [30, 31, 32, 33, 34, 35, 36, 37, 38, 39])
    }

    @Test("Window keeps two readings behind now and runs eight ahead")
    func windowSpansPastAndFuture() {
        let state = TodayUiState(pages: [page(id: 1, firstTemperature: 0)])

        let window = state.hourlyWindow(for: state.pages[0], now: Fixtures.now)

        #expect(window.count == 10)
        #expect(window.first?.time == Fixtures.now.addingTimeInterval(-6 * 3_600))
        #expect(window.last?.time == Fixtures.now.addingTimeInterval(21 * 3_600))
    }

    @Test("A cache with nothing upcoming shows its tail rather than nothing")
    func exhaustedCacheShowsTail() {
        // Every reading behind us: a forecast cached long enough ago that it has run out.
        let stale = page(
            id: 1,
            firstTemperature: 0,
            start: Fixtures.now.addingTimeInterval(-48 * 3_600)
        )
        let state = TodayUiState(pages: [stale])

        let window = state.hourlyWindow(for: stale, now: Fixtures.now)

        #expect(window.count == 3)
        #expect(window.last == stale.forecast.data?.days.first?.hourly.last)
    }

    @Test("A page with no forecast yet has an empty window")
    func missingForecastGivesNothing() {
        let page = TodayPage(location: Fixtures.location(), weather: DataState(data: Fixtures.weather()))

        #expect(TodayUiState().hourlyWindow(for: page, now: Fixtures.now).isEmpty)
    }

    /// Twelve three-hourly readings, the third of which is `now`. Temperatures ascend from
    /// `firstTemperature` so a window can be traced back to the page it came from.
    private func page(
        id: Int64,
        firstTemperature: Double,
        start: Date = Fixtures.now.addingTimeInterval(-6 * 3_600)
    )
        -> TodayPage
    {
        let hours = (0..<12).map { index in
            HourlyForecast(
                time: start.addingTimeInterval(Double(index) * 3 * 3_600),
                condition: .clear,
                iconCode: "01d",
                temperatureCelsius: firstTemperature + Double(index),
                precipitationProbability: 0,
                windSpeedMetresPerSecond: 1
            )
        }
        let forecast = Forecast(
            locationID: id,
            locationName: "Place \(id)",
            days: [
                ForecastDay(
                    date: start,
                    condition: .clear,
                    description: "Clear sky",
                    iconCode: "01d",
                    minTemperatureCelsius: firstTemperature,
                    maxTemperatureCelsius: firstTemperature + 11,
                    precipitationProbability: 0,
                    hourly: hours
                ),
            ],
            cachedAt: Fixtures.now,
            timeZoneOffsetSeconds: 0
        )
        return TodayPage(
            location: Fixtures.location(id: id, name: "Place \(id)"),
            weather: DataState(data: Fixtures.weather(locationID: id)),
            forecast: DataState(data: forecast)
        )
    }
}
