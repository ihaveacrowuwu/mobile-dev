import Foundation
import Testing
@testable import SkyCast

/// Regression guards for sunrise and sunset being rendered in the **device's** time zone rather
/// than the location's, and for the forecast's day grouping depending on which path served the
/// read.
///
/// These assert **absolute** formatted strings for two different offsets rather than mutating the
/// process time zone. Two offsets producing two different strings from one `Date` is the property
/// that breaks when the offset is ignored, and it holds regardless of where the test machine is: a
/// test run in UTC cannot otherwise distinguish the correct implementation from the broken one.
///
/// The Android counterparts are `LocationTimeZoneTest` and `WeatherDetailsTest`.
@Suite("Location time zone")
struct LocationTimeZoneTests {
    private func fixture(_ name: String) throws -> Data {
        let bundle = Bundle(for: TimeZoneBundleToken.self)
        guard let url = bundle.url(forResource: name, withExtension: "json") else {
            throw TimeZoneFixtureError.missing(name)
        }
        return try Data(contentsOf: url)
    }

    private var decoder: JSONDecoder {
        JSONDecoder()
    }

    // MARK: - The offset reaches the domain and survives the cache

    @Test("Current weather carries the location's UTC offset, not the device's")
    func currentWeatherCarriesOffset() throws {
        let dto = try decoder.decode(CurrentWeatherDTO.self, from: fixture("current_weather_london"))

        let weather = WeatherMapper.weather(from: dto, locationID: 1, locationName: "London", cachedAt: .now)

        // The fixture's "timezone" field is 3600, London on BST.
        #expect(weather.timeZoneOffsetSeconds == 3_600)
        #expect(weather.timeZone.secondsFromGMT() == 3_600)
    }

    @Test("The offset survives a round trip through the cache")
    func offsetSurvivesCacheRoundTrip() throws {
        let dto = try decoder.decode(CurrentWeatherDTO.self, from: fixture("current_weather_london"))
        let weather = WeatherMapper.weather(from: dto, locationID: 1, locationName: "London", cachedAt: .now)

        let restored = WeatherMapper.weather(from: WeatherMapper.persistentWeather(from: weather))

        #expect(restored.timeZoneOffsetSeconds == weather.timeZoneOffsetSeconds)
    }

    @Test("A nonsensical offset degrades to UTC instead of trapping")
    func nonsensicalOffsetFallsBackToUTC() {
        // TimeZone(secondsFromGMT:) returns nil beyond ±18 hours.
        let weather = Fixtures.weather(timeZoneOffsetSeconds: 999_999)

        #expect(weather.timeZone == .gmt)
    }

    // MARK: - Formatting

    @Test("Sunrise and sunset render in the location's zone")
    func sunriseRendersInLocationZone() {
        let weather = londonWeather(offsetSeconds: 3_600)

        let details = Dictionary(
            uniqueKeysWithValues: weather.details(windUnit: .metresPerSecond).map { ($0.label, $0.value) }
        )

        // London's own clock. A device five hours ahead would say 09:49.
        #expect(details["Sunrise"] == "05:49")
        #expect(details["Sunset"] == "21:21")
    }

    @Test("The same instant formats differently for two different location zones")
    func sameInstantDiffersByZone() {
        let londonSunrise = sunriseLabel(offsetSeconds: 3_600)
        let maleSunrise = sunriseLabel(offsetSeconds: 5 * 3_600)

        #expect(maleSunrise == "09:49")
        // The assertion that actually breaks when the offset is dropped: two places must not
        // report the same wall-clock time for one instant.
        #expect(londonSunrise != maleSunrise)
    }

    // MARK: - Day grouping

    @Test("Forecast day grouping is identical from the API and from the cache")
    func groupingIsPathIndependent() throws {
        let dto = try decoder.decode(ForecastResponseDTO.self, from: fixture("forecast_london"))
        let fromAPI = WeatherMapper.forecast(from: dto, locationID: 1, locationName: "London", cachedAt: .now)

        let fromCache = WeatherMapper.forecast(from: WeatherMapper.persistentReadings(from: fromAPI))

        let cached = try #require(fromCache, "cached records must rebuild into a forecast")
        // The dates are the identity of a day-detail route. If these differ, tapping a day online
        // and reopening it offline lands on "day not available".
        #expect(cached.days.map(\.date) == fromAPI.days.map(\.date))
        #expect(cached.timeZoneOffsetSeconds == fromAPI.timeZoneOffsetSeconds)
    }

    // MARK: - Helpers

    private func londonWeather(offsetSeconds: Int) -> Weather {
        Weather(
            locationID: 1,
            locationName: "London",
            condition: .clear,
            description: "Clear sky",
            iconCode: "01d",
            temperatureCelsius: 22,
            feelsLikeCelsius: 21,
            minTemperatureCelsius: 18,
            maxTemperatureCelsius: 24,
            humidityPercent: 60,
            pressureHpa: 1_013,
            windSpeedMetresPerSecond: 4.5,
            windDirectionDegrees: 220,
            cloudinessPercent: 5,
            visibilityMetres: 10_000,
            // 04:49 UTC: 05:49 in London (BST), 09:49 on a UTC+5 device.
            sunrise: Date(timeIntervalSince1970: 1_781_671_740),
            sunset: Date(timeIntervalSince1970: 1_781_727_660),
            observedAt: .now,
            cachedAt: .now,
            timeZoneOffsetSeconds: offsetSeconds
        )
    }

    private func sunriseLabel(offsetSeconds: Int) -> String? {
        londonWeather(offsetSeconds: offsetSeconds)
            .details(windUnit: .metresPerSecond)
            .first { $0.label == "Sunrise" }?
            .value
    }
}

private enum TimeZoneFixtureError: Error {
    case missing(String)
}

/// Anchors `Bundle(for:)` to the test bundle. A `final class` is required.
private final class TimeZoneBundleToken {}
