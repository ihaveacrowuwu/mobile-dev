import Foundation
import Testing
@testable import SkyCast

// Pure-domain tests. No simulator services, no network, no store, they run in milliseconds.
//
// Uses **Swift Testing** (`@Test` / `#expect`) rather than XCTest: it is the current
// framework, the failure output is far more informative, and parameterised tests via
// `arguments:` remove the loop-with-assertions pattern entirely.

@Suite("WeatherCondition mapping")
struct WeatherConditionTests {
    /// Boundary values matter: the ranges are hand-written from the OpenWeather docs, and an
    /// off-by-one would silently show the wrong artwork rather than crash.
    @Test(
        "OpenWeather ids map to the right condition",
        arguments: [
            (200, WeatherCondition.thunderstorm),
            (232, .thunderstorm),
            (300, .drizzle),
            (321, .drizzle),
            (500, .rain),
            (531, .rain),
            (600, .snow),
            (622, .snow),
            (701, .mist),
            (781, .mist),
            (800, .clear),
            (801, .clouds),
            (804, .clouds),
        ]
    )
    func mapsKnownIDs(id: Int, expected: WeatherCondition) {
        #expect(WeatherCondition.fromOpenWeatherID(id) == expected)
    }

    /// A future API addition must degrade to generic artwork, never crash the app.
    @Test("Unrecognised ids fall back to .unknown", arguments: [0, 999, -1, 805])
    func unknownIDsFallBack(id: Int) {
        #expect(WeatherCondition.fromOpenWeatherID(id) == .unknown)
    }

    @Test("Every condition has a non-empty symbol in both day and night")
    func everyConditionHasSymbols() {
        for condition in WeatherCondition.allCases {
            #expect(!condition.symbolName(isDaytime: true).isEmpty)
            #expect(!condition.symbolName(isDaytime: false).isEmpty)
        }
    }

    /// Asserts that the symbol differs between day and night.
    ///
    /// `everyConditionHasSymbols` above passes whether or not the distinction is honoured, so this
    /// is the test that holds the property.
    ///
    /// Mirrored by `WeatherConditionIconTest` on Android.
    @Test("Clear and cloudy skies use a different symbol at night")
    func symbolsDifferBetweenDayAndNight() {
        #expect(
            WeatherCondition.clear.symbolName(isDaytime: true)
                != WeatherCondition.clear.symbolName(isDaytime: false),
            "A clear sky must not use the same symbol at 4am as at noon"
        )
        #expect(
            WeatherCondition.clouds.symbolName(isDaytime: true)
                != WeatherCondition.clouds.symbolName(isDaytime: false)
        )
    }

    /// The complement: rain looks the same at any hour, so varying it would be noise.
    @Test(
        "Precipitation and low-visibility conditions look the same at any hour",
        arguments: [
            WeatherCondition.rain,
            .drizzle,
            .thunderstorm,
            .snow,
            .mist,
            .unknown,
        ]
    )
    func symbolsInvariantToTimeOfDay(condition: WeatherCondition) {
        #expect(condition.symbolName(isDaytime: true) == condition.symbolName(isDaytime: false))
    }
}

@Suite("Unit conversion")
struct UnitConversionTests {
    /// Because everything is cached in Celsius and converted at render time, a bug here
    /// shows wrong numbers on every screen, including offline, where there is no network
    /// response to blame.
    @Test("Celsius to Celsius is the identity")
    func celsiusIdentity() {
        #expect(TemperatureUnit.celsius.convertFromCelsius(22) == 22)
    }

    @Test(
        "Celsius to Fahrenheit at known reference points",
        arguments: [(0.0, 32.0), (100.0, 212.0), (-40.0, -40.0), (37.0, 98.6)]
    )
    func celsiusToFahrenheit(celsius: Double, expected: Double) {
        let actual = TemperatureUnit.fahrenheit.convertFromCelsius(celsius)
        #expect(abs(actual - expected) < 0.001)
    }

    @Test("Wind speed conversions")
    func windSpeed() {
        let tenMetresPerSecond = 10.0
        #expect(WindSpeedUnit.metresPerSecond.convertFromMetresPerSecond(tenMetresPerSecond) == 10)
        #expect(abs(WindSpeedUnit.kilometresPerHour.convertFromMetresPerSecond(tenMetresPerSecond) - 36) < 0.001)
        #expect(abs(WindSpeedUnit.milesPerHour.convertFromMetresPerSecond(tenMetresPerSecond) - 22.369) < 0.01)
    }

    /// Guards against adding an enum case and forgetting its label, which would render as
    /// an empty string next to the temperature.
    @Test("Every unit exposes a non-empty symbol")
    func everyUnitHasSymbol() {
        for unit in TemperatureUnit.allCases {
            #expect(!unit.symbol.isEmpty)
        }
        for unit in WindSpeedUnit.allCases {
            #expect(!unit.symbol.isEmpty)
        }
    }
}

@Suite("AppError classification")
struct AppErrorTests {
    /// `isRetryable` drives whether the UI offers a Retry button, so getting it wrong means
    /// either hiding a working recovery path or showing a button that can never succeed.
    @Test(
        "Transient failures are retryable",
        arguments: [AppError.offline, .timeout, .rateLimited, .server(statusCode: 503)]
    )
    func transientIsRetryable(error: AppError) {
        #expect(error.isRetryable)
    }

    @Test(
        "Deterministic failures are not retryable",
        arguments: [
            AppError.notFound,
            .unauthorized,
            .decoding(detail: "bad shape"),
            .storage(detail: "disk full"),
        ]
    )
    func deterministicIsNotRetryable(error: AppError) {
        #expect(!error.isRetryable)
    }

    @Test("A missing API key is a configuration problem, not a transient one")
    func unauthorizedIsNotRetryable() {
        // Retrying a bad key can never succeed; the UI shows setup instructions instead.
        #expect(!AppError.unauthorized.isRetryable)
        #expect(!AppError.unauthorized.isConnectivityRelated)
    }

    @Test("HTTP status codes map to the right case")
    func httpStatusMapping() {
        #expect(AppError.fromHTTPStatus(401) == .unauthorized)
        #expect(AppError.fromHTTPStatus(403) == .unauthorized)
        #expect(AppError.fromHTTPStatus(404) == .notFound)
        #expect(AppError.fromHTTPStatus(429) == .rateLimited)
        #expect(AppError.fromHTTPStatus(500) == .server(statusCode: 500))
        #expect(AppError.fromHTTPStatus(503) == .server(statusCode: 503))
    }

    @Test("URLError is translated, never leaked")
    func urlErrorTranslation() {
        #expect(AppError.from(URLError(.notConnectedToInternet)) == .offline)
        #expect(AppError.from(URLError(.timedOut)) == .timeout)
        #expect(AppError.from(URLError(.cannotFindHost)) == .offline)
    }

    @Test("Every case has user-facing copy")
    func everyCaseHasCopy() {
        let all: [AppError] = [
            .offline, .timeout, .notFound, .rateLimited, .unauthorized,
            .server(statusCode: 500), .decoding(detail: ""), .storage(detail: ""),
            .unknown(description: ""),
        ]
        for error in all {
            #expect(!error.title.isEmpty)
            #expect(!error.message.isEmpty)
            #expect(!error.symbolName.isEmpty)
        }
    }
}

@Suite("Cache staleness")
struct StalenessTests {
    @Test("Weather is fresh inside its TTL and stale outside it")
    func weatherStaleness() {
        let weather = Fixtures.weather(cachedAt: Fixtures.now)

        #expect(!weather.isStale(now: Fixtures.now))
        #expect(!weather.isStale(now: Fixtures.now.addingTimeInterval(9 * 60)))
        #expect(weather.isStale(now: Fixtures.now.addingTimeInterval(11 * 60)))
    }

    @Test("Forecast uses a longer TTL than current conditions")
    func forecastTTLIsLonger() {
        // The forecast changes slowly, so refreshing it as often as current conditions
        // would waste the free-tier quota for no user benefit.
        #expect(Forecast.forecastTTL > Weather.currentWeatherTTL)
    }
}
