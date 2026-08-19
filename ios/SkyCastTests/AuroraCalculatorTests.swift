import Foundation
import Testing
@testable import SkyCast

/// Whether the aurora is worth going outside for.
///
/// ## Where the expected numbers come from
///
/// Two independent sources, neither of them this code:
///
/// 1. **Published geomagnetic latitudes** for well-known places, London ~54°, Tromsø ~67°, Anchorage ~61°,
///    which checks the coordinate transform on its own.
/// 2. **Published aurora-watching guidance**, which is unusually specific and widely agreed: Kp 5 puts aurora
///    over Scotland, Kp 6 gives southern England a chance, Kp 7 makes it likely there, and Tromsø sees it at
///    almost any Kp because it sits under the oval. Those are the assertions that matter, because they test the
///    boundary table, the visibility margins and the transform *together* against reality.
///
/// The Kotlin twin is `AuroraCalculatorTest.kt`, asserting the same values.
@Suite("Aurora calculator")
struct AuroraCalculatorTests {
    private static let london = (latitude: 51.5074, longitude: -0.1278)
    private static let edinburgh = (latitude: 55.9533, longitude: -3.1883)
    private static let tromso = (latitude: 69.6496, longitude: 18.9560)
    private static let anchorage = (latitude: 61.2181, longitude: -149.9003)
    private static let male = (latitude: 4.1755, longitude: 73.5093)

    // MARK: - The coordinate transform

    @Test("Geomagnetic latitude matches published values")
    func geomagneticLatitude() {
        // Checked alone, because everything else depends on it. Geographic latitude does not work
        // here: London and Calgary are at nearly the same latitude on a map, and one of them sees
        // aurora most years.
        #expect(abs(Self.magnetic(Self.london) - 54) < 1.5)
        #expect(abs(Self.magnetic(Self.tromso) - 67) < 1.5)
        #expect(abs(Self.magnetic(Self.anchorage) - 61) < 1.5)
    }

    @Test("The tropics are nowhere near the oval")
    func tropics() {
        #expect(abs(Self.magnetic(Self.male)) < 10)
    }

    // MARK: - The boundary table

    @Test("The oval reaches further from the pole as the field is disturbed")
    func boundaryDirection() {
        // NOAA's published endpoints, and the direction between them. A sign error here would put the aurora
        // over the equator during a storm.
        #expect(abs(AuroraCalculator.equatorwardBoundary(kp: 0) - 66.5) < 0.01)
        #expect(abs(AuroraCalculator.equatorwardBoundary(kp: 9) - 48.1) < 0.01)
        #expect(AuroraCalculator.equatorwardBoundary(kp: 3) > AuroraCalculator.equatorwardBoundary(kp: 6))
    }

    @Test("A fractional Kp interpolates rather than rounding")
    func interpolation() {
        // The reported index is not an integer, 4.67 is a real value NOAA publishes, and rounding it away
        // moves the boundary by about a degree, which is a whole band of this screen's answers.
        let low = AuroraCalculator.equatorwardBoundary(kp: 4)
        let high = AuroraCalculator.equatorwardBoundary(kp: 5)
        #expect(abs(AuroraCalculator.equatorwardBoundary(kp: 4.5) - (low + high) / 2) < 0.01)
    }

    @Test("Kp outside 0 to 9 is clamped rather than extrapolated")
    func clamping() {
        #expect(AuroraCalculator.equatorwardBoundary(kp: -2) == AuroraCalculator.equatorwardBoundary(kp: 0))
        #expect(AuroraCalculator.equatorwardBoundary(kp: 12) == AuroraCalculator.equatorwardBoundary(kp: 9))
    }

    // MARK: - The answer, against published guidance

    @Test("Tromsø sees aurora at almost any Kp")
    func tromsoAlwaysWorks() {
        // It sits under the oval; that is why people go there. A model that needed a storm for Tromsø would be
        // wrong about the one place everybody knows the answer for.
        let magnetic = Self.magnetic(Self.tromso)
        #expect(AuroraCalculator.chance(kp: 0, geomagneticLatitude: magnetic) >= .likely)
        #expect(AuroraCalculator.chance(kp: 3, geomagneticLatitude: magnetic) >= .overhead)
    }

    @Test("Southern England needs a storm, Scotland needs less")
    func britishThresholds() {
        // The published guidance this whole model is judged against.
        let londonMagnetic = Self.magnetic(Self.london)
        #expect(AuroraCalculator.chance(kp: 4, geomagneticLatitude: londonMagnetic) < .possible)
        #expect(AuroraCalculator.chance(kp: 6, geomagneticLatitude: londonMagnetic) >= .possible)
        #expect(AuroraCalculator.chance(kp: 7, geomagneticLatitude: londonMagnetic) >= .likely)
        #expect(AuroraCalculator.chance(kp: 5, geomagneticLatitude: Self.magnetic(Self.edinburgh)) >= .possible)
    }

    @Test("The minimum Kp for a place matches what aurora watchers quote")
    func minimumKp() {
        // "You need Kp 6 in southern England" is the single most repeated number in this hobby.
        #expect(AuroraCalculator.minimumKpForChance(geomagneticLatitude: Self.magnetic(Self.london)) == 6)
        #expect(AuroraCalculator.minimumKpForChance(geomagneticLatitude: Self.magnetic(Self.tromso)) == 0)
    }

    @Test("The tropics never see it, even at Kp 9")
    func tropicsNever() {
        // The honest answer, and the one a "chance" model most easily gets wrong by scaling smoothly.
        let magnetic = Self.magnetic(Self.male)
        #expect(AuroraCalculator.chance(kp: 9, geomagneticLatitude: magnetic) == AuroraChance.none)
        #expect(AuroraCalculator.minimumKpForChance(geomagneticLatitude: magnetic) == nil)
    }

    @Test("The southern hemisphere is handled, if less precisely")
    func southernHemisphere() {
        // Asserted loosely: the centred-dipole transform is good in the north and rough in the
        // south, where the real field is least dipole-like. This checks the two things the
        // approximation supports: the sign is right, and a great storm registers as something.
        let magnetic = AuroraCalculator.geomagneticLatitude(latitude: -46.4, longitude: 168.35)
        #expect(magnetic < 0)
        #expect(AuroraCalculator.chance(kp: 9, geomagneticLatitude: magnetic) >= .faintOnHorizon)
    }

    // MARK: - The forecast wrapper

    @Test("The peak ahead is the highest Kp inside the window, not the whole feed")
    func peakWindow() throws {
        let now = Date(timeIntervalSince1970: 1_787_097_600)
        let weather = SpaceWeather(
            kpNow: 3,
            observedAt: now,
            stormLevel: nil,
            upcoming: [
                KpPeriod(time: now.addingTimeInterval(3 * 3_600), kp: 4, stormLevel: nil),
                KpPeriod(time: now.addingTimeInterval(9 * 3_600), kp: 6, stormLevel: "G2"),
                // Beyond the window: a storm three days out must not be reported as tonight's peak.
                KpPeriod(time: now.addingTimeInterval(60 * 3_600), kp: 8, stormLevel: "G4"),
            ],
            cachedAt: now
        )

        let peak = try #require(weather.peakAhead())
        #expect(peak.kp == 6)
        #expect(peak.stormLevel == "G2")
    }

    @Test("Staleness follows the TTL")
    func staleness() {
        let now = Date(timeIntervalSince1970: 1_787_097_600)
        let weather = SpaceWeather(kpNow: 3, observedAt: now, stormLevel: nil, upcoming: [], cachedAt: now)
        #expect(!weather.isStale(now: now.addingTimeInterval(29 * 60)))
        #expect(weather.isStale(now: now.addingTimeInterval(31 * 60)))
    }

    private static func magnetic(_ place: (latitude: Double, longitude: Double)) -> Double {
        AuroraCalculator.geomagneticLatitude(latitude: place.latitude, longitude: place.longitude)
    }
}
