import Foundation
import Testing
@testable import SkyCast

/// Mapper and DTO decoding tests.
///
/// Pure functions with no dependencies, which makes these the cheapest high-value tests in
/// the project. They also pin down the API contract: if OpenWeather changes a field name,
/// these fail with a clear message instead of the app silently showing zeroes.
@Suite("WeatherMapper")
struct WeatherMapperTests {
    @Test("A realistic current-weather payload decodes and maps")
    func decodesAndMapsCurrentWeather() throws {
        let data = Data(Fixtures.currentWeatherJSON.utf8)
        let dto = try JSONDecoder().decode(CurrentWeatherDTO.self, from: data)

        let weather = WeatherMapper.weather(
            from: dto,
            locationID: 7,
            locationName: "London",
            cachedAt: Fixtures.now
        )

        #expect(weather.locationID == 7)
        #expect(weather.locationName == "London")
        #expect(weather.condition == .clear)
        // OpenWeather sends "clear sky"; we display it sentence-cased.
        #expect(weather.description == "Clear sky")
        #expect(weather.temperatureCelsius == 22.0)
        #expect(weather.humidityPercent == 60)
        #expect(weather.pressureHpa == 1_013)
        #expect(weather.windSpeedMetresPerSecond == 4.5)
        #expect(weather.visibilityMetres == 10_000)
        #expect(weather.cachedAt == Fixtures.now)
    }

    /// A sparse or partially-broken response must degrade, not crash. OpenWeather omits
    /// `wind.gust` in calm conditions and has been known to drop `visibility` entirely.
    @Test("Missing optional fields fall back to defaults instead of failing to decode")
    func toleratesMissingOptionalFields() throws {
        let json = """
        {
          "coord": { "lon": 73.5, "lat": 4.17 },
          "weather": [{ "id": 500, "main": "Rain", "description": "light rain", "icon": "10d" }],
          "main": { "temp": 29.0 },
          "dt": 1785931200
        }
        """
        let dto = try JSONDecoder().decode(CurrentWeatherDTO.self, from: Data(json.utf8))
        let weather = WeatherMapper.weather(
            from: dto,
            locationID: 1,
            locationName: "Male'",
            cachedAt: Fixtures.now
        )

        #expect(weather.condition == .rain)
        #expect(weather.temperatureCelsius == 29.0)
        // feels_like absent → falls back to temp rather than 0, which would read as freezing.
        #expect(weather.feelsLikeCelsius == 29.0)
        #expect(weather.windSpeedMetresPerSecond == 0)
        #expect(weather.visibilityMetres == 0)
    }

    @Test("An empty weather array maps to .unknown rather than trapping")
    func toleratesEmptyWeatherArray() throws {
        let json = """
        { "coord": { "lon": 0, "lat": 0 }, "weather": [], "main": { "temp": 10.0 }, "dt": 1 }
        """
        let dto = try JSONDecoder().decode(CurrentWeatherDTO.self, from: Data(json.utf8))
        let weather = WeatherMapper.weather(from: dto, locationID: 1, locationName: "X", cachedAt: Fixtures.now)

        #expect(weather.condition == .unknown)
        #expect(weather.description.isEmpty)
    }

    @Test("An empty location name falls back to the city name from the response")
    func fallsBackToCityName() throws {
        let dto = try JSONDecoder().decode(
            CurrentWeatherDTO.self,
            from: Data(Fixtures.currentWeatherJSON.utf8)
        )
        let weather = WeatherMapper.weather(from: dto, locationID: 1, locationName: "", cachedAt: Fixtures.now)

        #expect(weather.locationName == "London")
    }

    @Test("Domain → persistent → domain is lossless")
    func roundTripsThroughPersistentModel() {
        let original = Fixtures.weather()
        let persisted = WeatherMapper.persistentWeather(from: original)
        let restored = WeatherMapper.weather(from: persisted)

        // A round trip that loses a field would show wrong numbers only when offline,
        // exactly the case a manual test is least likely to catch.
        #expect(restored == original)
    }

    @Test("Forecast readings group into sorted calendar days")
    func groupsForecastByDay() throws {
        // Two days, three readings, out of order in the payload.
        let json = """
        {
          "city": { "id": 1, "name": "London", "country": "GB", "timezone": 0 },
          "list": [
            { "dt": 1785945600, "main": { "temp": 20.0, "temp_min": 18.0, "temp_max": 22.0 },
              "weather": [{ "id": 800, "main": "Clear", "description": "clear sky", "icon": "01d" }],
              "wind": { "speed": 3.0, "deg": 90 }, "pop": 0.1 },
            { "dt": 1785902400, "main": { "temp": 15.0, "temp_min": 14.0, "temp_max": 16.0 },
              "weather": [{ "id": 500, "main": "Rain", "description": "light rain", "icon": "10d" }],
              "wind": { "speed": 5.0, "deg": 180 }, "pop": 0.6 },
            { "dt": 1785913200, "main": { "temp": 17.0, "temp_min": 16.0, "temp_max": 19.0 },
              "weather": [{ "id": 801, "main": "Clouds", "description": "few clouds", "icon": "02d" }],
              "wind": { "speed": 4.0, "deg": 200 }, "pop": 0.2 }
          ]
        }
        """
        let dto = try JSONDecoder().decode(ForecastResponseDTO.self, from: Data(json.utf8))
        let forecast = WeatherMapper.forecast(
            from: dto,
            locationID: 1,
            locationName: "London",
            cachedAt: Fixtures.now
        )

        #expect(!forecast.days.isEmpty)
        // Days must come out chronologically regardless of payload order.
        #expect(forecast.days.map(\.date) == forecast.days.map(\.date).sorted())
        // Min/max are aggregated across the whole day, not taken from one reading.
        for day in forecast.days {
            #expect(day.minTemperatureCelsius <= day.maxTemperatureCelsius)
            #expect(!day.hourly.isEmpty)
            #expect(day.hourly.map(\.time) == day.hourly.map(\.time).sorted())
        }
    }

    @Test("Geocoding results map, keeping an absent state as nil")
    func mapsGeocodingResults() throws {
        let json = """
        [
          { "name": "London", "lat": 51.5085, "lon": -0.1257, "country": "GB", "state": "England" },
          { "name": "Male", "lat": 4.1748, "lon": 73.5089, "country": "MV" }
        ]
        """
        let dtos = try JSONDecoder().decode([GeocodingResultDTO].self, from: Data(json.utf8))
        let results = dtos.map(WeatherMapper.searchResult(from:))

        #expect(results.count == 2)
        #expect(results[0].displayName == "London, England, GB")
        // No state in the payload → the display name must not contain a stray comma.
        #expect(results[1].state == nil)
        #expect(results[1].displayName == "Male, MV")
    }
}
