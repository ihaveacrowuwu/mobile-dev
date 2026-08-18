import Foundation
import Testing
@testable import SkyCast

/// Golden hour and blue hour.
///
/// ## Where the expected numbers come from
///
/// The solar position is checked where it is independently published: **sunrise and sunset**. London on
/// 19 August 2026 rises at 05:52 and sets at 20:14 BST, and the sun's altitude at those instants must be the
/// standard −0.833°, the refracted, semidiameter-corrected horizon. That pins the position calculation
/// without relying on anyone's golden-hour table, which is what the rest of this suite then builds on.
///
/// The windows themselves are checked structurally: ordering, the altitude at each boundary, and the latitude
/// behaviour that makes the feature worth having, golden hour is brief in the tropics and long in the far
/// north, which is exactly what a fixed "hour" would get wrong.
///
/// The Kotlin twin is `SolarCalculatorTest.kt`, asserting the same things.
@Suite("Solar calculator")
struct SolarCalculatorTests {
    private static let london = (latitude: 51.5074, longitude: -0.1278)
    private static let male = (latitude: 4.1755, longitude: 73.5093)
    private static let tromso = (latitude: 69.6496, longitude: 18.9560)

    @Test("The sun is at the standard horizon angle at published sunrise and sunset")
    func horizonAngle() {
        // London, 19 August 2026: 05:52 and 20:14 BST, 04:52 and 19:14 UTC.
        let sunrise = Date(timeIntervalSince1970: 1_787_115_120)
        let sunset = Date(timeIntervalSince1970: 1_787_166_840)

        let atSunrise = SolarCalculator.altitudeDegrees(
            at: sunrise, latitude: Self.london.latitude, longitude: Self.london.longitude
        )
        let atSunset = SolarCalculator.altitudeDegrees(
            at: sunset, latitude: Self.london.latitude, longitude: Self.london.longitude
        )

        // −0.833° is the standard: refraction at the horizon plus the sun's semidiameter. A quarter of a
        // degree is well inside the minute the published times are rounded to.
        #expect(abs(atSunrise - -0.833) < 0.25)
        #expect(abs(atSunset - -0.833) < 0.25)
    }

    @Test("The windows run in order, and each boundary is at its defined altitude")
    func boundaries() throws {
        let light = try #require(Self.eveningLight(for: Self.london))

        #expect(light.goldenStart < light.goldenEnd)
        #expect(light.goldenEnd < light.blueEnd)

        /// Each instant is defined by an altitude, so that is what to assert: a window that drifted would
        /// still be ordered, and this is what notices.
        func altitude(at date: Date) -> Double {
            SolarCalculator.altitudeDegrees(
                at: date, latitude: Self.london.latitude, longitude: Self.london.longitude
            )
        }
        #expect(abs(altitude(at: light.goldenStart) - SolarCalculator.goldenHourStartDegrees) < 0.05)
        #expect(abs(altitude(at: light.goldenEnd) - SolarCalculator.goldenHourEndDegrees) < 0.05)
        #expect(abs(altitude(at: light.blueEnd) - SolarCalculator.blueHourEndDegrees) < 0.05)
    }

    @Test("Golden hour is the evening one, not the morning")
    func eveningOnly() throws {
        // Every one of these altitudes is crossed twice a day. Pairing a morning crossing with an evening
        // one would produce a "golden hour" lasting most of the day, which is the bug this guards.
        let light = try #require(Self.eveningLight(for: Self.london))
        #expect(light.goldenDuration < 3 * 3_600)
        #expect(light.goldenDuration > 20 * 60)
        #expect(light.goldenStart > Self.noon)
    }

    @Test("Golden hour is short in the tropics and long in the far north")
    func latitudeChangesEverything() throws {
        // The whole reason this is computed rather than a fixed hour. The sun sets almost vertically at the
        // equator and at a shallow angle near the pole, so the same altitude band takes very different times
        // to cross.
        let tropical = try #require(Self.eveningLight(for: Self.male))
        let northern = try #require(Self.eveningLight(for: Self.tromso))

        #expect(tropical.goldenDuration < northern.goldenDuration)
        // And the tropical one really is brief, well under the "hour" the name promises.
        #expect(tropical.goldenDuration < 60 * 60)
    }

    @Test("A polar summer day has no evening light to report")
    func polarSummer() {
        // Tromsø in June: the sun never drops to -6°, so there is no blue hour and the result is
        // `nil`.
        let midsummer = Date(timeIntervalSince1970: 1_782_043_200)
        #expect(
            SolarCalculator.eveningLight(
                on: midsummer,
                latitude: Self.tromso.latitude,
                longitude: Self.tromso.longitude,
                timeZone: .gmt
            ) == nil
        )
    }

    @Test("Now is inside the window it says it is inside")
    func windowMembership() throws {
        let light = try #require(Self.eveningLight(for: Self.london))
        let midGolden = light.goldenStart.addingTimeInterval(light.goldenDuration / 2)
        let midBlue = light.goldenEnd.addingTimeInterval(light.blueDuration / 2)

        #expect(light.isGolden(at: midGolden))
        #expect(!light.isBlue(at: midGolden))
        #expect(light.isBlue(at: midBlue))
        #expect(!light.isGolden(at: midBlue))
        // The boundaries belong to exactly one window each, not both and not neither.
        #expect(light.isGolden(at: light.goldenStart))
        #expect(light.isBlue(at: light.goldenEnd))
        #expect(!light.isBlue(at: light.blueEnd))
    }

    @Test("Progress is nil outside the windows and runs 0 to 1 across them")
    func progress() throws {
        let light = try #require(Self.eveningLight(for: Self.london))
        #expect(light.progress(at: light.goldenStart.addingTimeInterval(-3_600)) == nil)
        #expect(light.progress(at: light.blueEnd.addingTimeInterval(3_600)) == nil)
        #expect(light.progress(at: light.goldenStart) == 0)
        let end = try #require(light.progress(at: light.blueEnd))
        #expect(abs(end - 1) < 0.001)
    }

    /// 19 August 2026, midday UTC.
    private static let noon = Date(timeIntervalSince1970: 1_787_140_800)

    private static func eveningLight(for place: (latitude: Double, longitude: Double)) -> GoldenHour? {
        SolarCalculator.eveningLight(
            on: noon,
            latitude: place.latitude,
            longitude: place.longitude,
            timeZone: .gmt
        )
    }
}
