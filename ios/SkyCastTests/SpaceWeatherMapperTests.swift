import Foundation
import Testing
@testable import SkyCast

/// The Kp mapper, against a response captured from the live NOAA feed.
///
/// The fixture is real, not hand-written, and is shared byte-for-byte with
/// `android/core/data/src/test/resources/fixtures/`. It was captured during a **G1 storm**: Kp 5.0
/// observed at 03:00 UTC on 19 August 2026, with a `noaa_scale` of "G1", which exercises the
/// storm-level branch that is null in most samples.
///
/// The expected values are identical to `SpaceWeatherMapperTest.kt`.
@Suite("Space weather mapping")
struct SpaceWeatherMapperTests {
    private let cachedAt = Date(timeIntervalSince1970: 1_787_112_000)

    private func entries() throws -> [KpForecastEntryDTO] {
        let url = try #require(
            Bundle(for: SpaceWeatherFixtureAnchor.self).url(forResource: "kp_forecast", withExtension: "json")
        )
        return try JSONDecoder().decode([KpForecastEntryDTO].self, from: Data(contentsOf: url))
    }

    @Test("The real feed decodes and yields the latest measured period")
    func decodesLiveFeed() throws {
        let weather = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))

        // The last entry the feed calls "observed", a G1 storm, which is what makes this fixture useful.
        #expect(weather.kpNow == 5.0)
        #expect(weather.stormLevel == "G1")
    }

    @Test("Timestamps are read as UTC, not as local time")
    func utcTimestamps() throws {
        // The feed's `time_tag` has no zone suffix. Decoding it in the device's zone would shift every reading by
        // the offset, which on this feed means attributing tonight's storm to this afternoon.
        let weather = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "UTC"))
        let components = calendar.dateComponents([.year, .month, .day, .hour], from: weather.observedAt)
        #expect(components.year == 2_026)
        #expect(components.month == 8)
        #expect(components.day == 19)
        #expect(components.hour == 3)
    }

    @Test("The future comes from the observed field, not from comparing timestamps")
    func futureFromField() throws {
        // The feed is not trimmed to now: it carries "estimated" periods whose timestamps are already in the
        // past. Splitting on the clock would file them as history and lose the next few hours entirely.
        let weather = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))
        #expect(!weather.upcoming.isEmpty)
        #expect(weather.upcoming.allSatisfy { $0.time >= weather.observedAt })
        #expect(weather.upcoming == weather.upcoming.sorted { $0.time < $1.time })
    }

    @Test("Tonight's peak comes from the forecast window")
    func peak() throws {
        let weather = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))
        let peak = try #require(weather.peakAhead())
        // The captured feed forecasts a maximum of Kp 4.67 in the next day.
        #expect(abs(peak.kp - 4.67) < 0.001)
    }

    @Test("A feed with nothing measured maps to nil rather than to a guess")
    func nothingMeasured() throws {
        // Every entry predicted: a shape change, not a quiet day. Inventing a "current" Kp from a forecast would
        // put a number on screen that nobody measured.
        let predictedOnly = try entries().filter { $0.observed != "observed" }
        #expect(SpaceWeatherMapper.spaceWeather(from: predictedOnly, cachedAt: cachedAt) == nil)
    }

    @Test("A round trip through the store loses nothing")
    func roundTrip() throws {
        let original = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))
        let restored = SpaceWeatherPersistence.spaceWeather(
            from: SpaceWeatherPersistence.persistent(from: original)
        )

        // Including the storm level and every forecast period, the encoded string is the one place this could
        // silently drop data, since it is parsed by splitting rather than by a schema.
        #expect(restored.kpNow == original.kpNow)
        #expect(restored.stormLevel == original.stormLevel)
        #expect(restored.upcoming.count == original.upcoming.count)
        #expect(restored.upcoming.map(\.kp) == original.upcoming.map(\.kp))
    }

    @Test("A reading with no storm level round trips as nil, not as an empty string")
    func quietRoundTrip() throws {
        // The common case, and the one the encoding could corrupt: an empty field between separators has to come
        // back as absent rather than as a storm called "".
        let original = try #require(try SpaceWeatherMapper.spaceWeather(from: entries(), cachedAt: cachedAt))
        let quiet = SpaceWeather(
            kpNow: original.kpNow,
            observedAt: original.observedAt,
            stormLevel: nil,
            upcoming: original.upcoming.map { KpPeriod(time: $0.time, kp: $0.kp, stormLevel: nil) },
            cachedAt: original.cachedAt
        )
        let restored = SpaceWeatherPersistence.spaceWeather(from: SpaceWeatherPersistence.persistent(from: quiet))
        #expect(restored.stormLevel == nil)
        #expect(restored.upcoming.allSatisfy { $0.stormLevel == nil })
    }
}

/// Locates the test bundle so the shared fixture can be read.
///
/// A class of its own rather than reusing `MetarMapperTests`': that one is `private` to its file,
/// which is right, and a per-file anchor is the smallest thing that works.
private final class SpaceWeatherFixtureAnchor {}
