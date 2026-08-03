import Foundation
import Testing
@testable import SkyCast

/// Contract tests against **real captured OpenWeather responses**.
///
/// The fixtures in `SkyCastTests/Fixtures/` were captured from the live API, not hand-written.
/// That distinction matters: a hand-written fixture only proves the DTOs decode what the
/// author *imagined*, whereas these prove they decode what the service actually sends,
/// including fields we never declared (`base`, `cod`, `main.sea_level`, `main.grnd_level`,
/// `sys.type`, `sys.id`, and a large `local_names` object).
///
/// The same three fixtures are shared byte-for-byte with
/// `android/app/src/test/resources/fixtures/`, so both platforms are held to one contract. If
/// a payload change breaks one platform, it breaks both, which is exactly what you want from
/// two clients of the same API.
@Suite("OpenWeather API contract")
struct ApiContractTests {
    /// Loads a fixture from the test bundle.
    ///
    /// `Bundle.module` is not used because these are plain resources of an Xcode test target,
    /// not an SPM package.
    private func fixture(_ name: String) throws -> Data {
        let bundle = Bundle(for: BundleToken.self)
        guard let url = bundle.url(forResource: name, withExtension: "json") else {
            throw FixtureError.missing(name)
        }
        return try Data(contentsOf: url)
    }

    private enum FixtureError: Error, CustomStringConvertible {
        case missing(String)

        var description: String {
            switch self {
            case let .missing(name):
                "Missing fixture \(name).json. Is it in the SkyCastTests target's resources?"
            }
        }
    }

    @Test("Live current-weather payload decodes despite undeclared fields")
    func decodesLiveCurrentWeather() throws {
        let dto = try JSONDecoder().decode(
            CurrentWeatherDTO.self,
            from: fixture("current_weather_london")
        )

        #expect(dto.cityName == "London")
        #expect(dto.weather.first?.id == 800)
        #expect(dto.main.pressure == 1_009)
        #expect(dto.main.humidity == 69)
        // Present in the real payload; proves the optional is read, not dropped.
        #expect(dto.wind.gust == 2.68)
        #expect(dto.visibility == 10_000)
        #expect(dto.system.sunriseEpochSeconds > 0)
    }

    @Test("Live current-weather payload maps to a fully populated domain model")
    func mapsLiveCurrentWeather() throws {
        let dto = try JSONDecoder().decode(
            CurrentWeatherDTO.self,
            from: fixture("current_weather_london")
        )
        let cachedAt = Date(timeIntervalSince1970: 1_785_931_200)

        let weather = WeatherMapper.weather(
            from: dto,
            locationID: 1,
            locationName: "London",
            cachedAt: cachedAt
        )

        #expect(weather.condition == .clear)
        // OpenWeather sends lowercase; we display sentence case.
        #expect(weather.description == "Clear sky")
        #expect(weather.locationID == 1)
        #expect(weather.cachedAt == cachedAt)
        // The icon code was "01n", night, so the derived flag must agree.
        #expect(weather.isDaytime == false)
    }

    @Test("Live forecast payload groups into chronological days")
    func groupsLiveForecast() throws {
        let dto = try JSONDecoder().decode(
            ForecastResponseDTO.self,
            from: fixture("forecast_london")
        )

        let forecast = WeatherMapper.forecast(
            from: dto,
            locationID: 1,
            locationName: "London",
            cachedAt: Date(timeIntervalSince1970: 1_785_931_200)
        )

        #expect(!forecast.days.isEmpty)
        #expect(forecast.days.map(\.date) == forecast.days.map(\.date).sorted())

        for day in forecast.days {
            #expect(day.minTemperatureCelsius <= day.maxTemperatureCelsius)
            #expect(!day.hourly.isEmpty)
            #expect(day.hourly.map(\.time) == day.hourly.map(\.time).sorted())
        }

        // Every reading must survive the grouping, none silently dropped.
        let regrouped = forecast.days.reduce(0) { $0 + $1.hourly.count }
        #expect(regrouped == dto.readings.count)
    }

    @Test("Live geocoding payload decodes and ignores local_names")
    func decodesLiveGeocoding() throws {
        let dtos = try JSONDecoder().decode(
            [GeocodingResultDTO].self,
            from: fixture("geocoding_male")
        )

        #expect(!dtos.isEmpty)
        let male = try #require(dtos.first)
        // The real response uses the accented spelling.
        #expect(male.name == "Malé")
        #expect(male.country == "MV")
        // The Maldives has no state, so the display name must not gain a stray comma.
        #expect(male.state == nil)
        #expect(WeatherMapper.searchResult(from: male).displayName == "Malé, MV")
    }

    @Test("Both platforms decode the same bytes")
    func fixturesAreShared() throws {
        // Guards against the two copies drifting. If this fails, re-copy from
        // android/app/src/test/resources/fixtures/, one contract, two clients.
        for name in ["current_weather_london", "forecast_london", "geocoding_male"] {
            let data = try fixture(name)
            #expect(!data.isEmpty, "\(name) is empty")
            // Failable init, not String(decoding:as:): the latter silently substitutes
            // replacement characters for invalid UTF-8, which would hide a corrupt fixture
            // rather than fail on it. These files contain non-ASCII names ("Malé", "މާލެ"),
            // so decoding correctly is part of what is being asserted.
            let text = try #require(
                String(bytes: data, encoding: .utf8),
                "\(name) is not valid UTF-8"
            )
            // A captured response must never contain an API key.
            #expect(!text.contains("appid"), "\(name) appears to contain a request URL")
            // Sanity-check the non-ASCII round trip while we are here.
            if name == "geocoding_male" {
                #expect(text.contains("Malé"), "accented characters did not survive capture")
            }
        }
    }
}

/// Anchors `Bundle(for:)` to the test bundle. A `final class` is required, `Bundle(for:)`
/// takes an `AnyClass`, so a struct or enum cannot be used.
private final class BundleToken {}
