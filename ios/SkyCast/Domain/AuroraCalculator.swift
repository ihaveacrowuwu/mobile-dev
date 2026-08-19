import Foundation

/// Whether the aurora is worth going outside for, at a particular place.
///
/// ## The two parts
///
/// **Where the aurora is.** The auroral oval sits around the *geomagnetic* pole, not the geographic
/// one, and reaches further from that pole the more disturbed the field is. NOAA's published
/// equatorward edge against Kp is `equatorwardBoundaryByKp` below, running from 66.5° of
/// geomagnetic latitude at Kp 0 down to 48.1° at Kp 9.
///
/// **Where you are.** Geomagnetic latitude, not the latitude on a map. London and Calgary sit at
/// almost the same geographic latitude but at very different geomagnetic ones.
///
/// ## Accuracy
///
/// The pole is taken as fixed, which costs a few tenths of a degree per year against bands that are
/// whole degrees wide.
///
/// The **centred dipole** approximation is good in the north, where the field is close to a dipole:
/// London 53.4° against ~54°, Tromsø 67.4° against ~67°, Anchorage 61.9° against ~61°. The southern
/// field is not a dipole, so southern latitudes are accurate only to several degrees. Results are
/// therefore reported as coarse bands rather than a percentage.
///
/// The Kotlin twin is `AuroraCalculator.kt`.
enum AuroraCalculator {
    /// Geomagnetic latitude for a place, in degrees.
    static func geomagneticLatitude(latitude: Double, longitude: Double) -> Double {
        let observerLatitude = radians(latitude)
        let poleLatitudeRadians = radians(poleLatitude)
        let relativeLongitude = radians(longitude - poleLongitude)
        let sine = sin(observerLatitude) * sin(poleLatitudeRadians)
            + cos(observerLatitude) * cos(poleLatitudeRadians) * cos(relativeLongitude)
        return degrees(asin(min(max(sine, -1), 1)))
    }

    /// The equatorward edge of the auroral oval at this Kp, in degrees of geomagnetic latitude.
    ///
    /// Interpolated between NOAA's whole-Kp values, because the reported index is not an integer.
    static func equatorwardBoundary(kp: Double) -> Double {
        let clamped = min(max(kp, 0), 9)
        let lower = Int(clamped)
        let upper = min(lower + 1, 9)
        let fraction = clamped - Double(lower)
        return equatorwardBoundaryByKp[lower]
            + (equatorwardBoundaryByKp[upper] - equatorwardBoundaryByKp[lower]) * fraction
    }

    /// How likely the aurora is to be visible from `geomagneticLatitude` at this `kp`.
    ///
    /// The bands are wider than the oval itself: aurora happens 100 to 300 km up, so it can be seen
    /// from a good way equatorward of where it actually is, low on the northern horizon. The
    /// thresholds match published guidance: Kp 6 for a chance in southern England, Kp 7 for a good
    /// one, Kp 5 for Scotland.
    static func chance(kp: Double, geomagneticLatitude: Double) -> AuroraChance {
        let distanceIntoOval = abs(geomagneticLatitude) - equatorwardBoundary(kp: kp)
        switch distanceIntoOval {
        case overheadMargin...: return .overhead
        case 0...: return .likely
        case possibleMargin...: return .possible
        case faintMargin...: return .faintOnHorizon
        default: return .none
        }
    }

    /// The lowest Kp at which this place has any real chance, or `nil` if even Kp 9 would not do
    /// it. `nil` in the tropics.
    static func minimumKpForChance(geomagneticLatitude: Double) -> Int? {
        (0...9).first { kp in
            chance(kp: Double(kp), geomagneticLatitude: geomagneticLatitude) >= .possible
        }
    }

    private static func radians(_ value: Double) -> Double {
        value * .pi / 180
    }

    private static func degrees(_ value: Double) -> Double {
        value * 180 / .pi
    }

    /// NOAA's equatorward auroral boundary, in geomagnetic latitude, indexed by whole Kp.
    ///
    /// Published values from NOAA SWPC's aurora tutorial.
    private static let equatorwardBoundaryByKp: [Double] = [
        66.5, 64.5, 62.4, 60.4, 58.3, 56.3, 54.2, 52.2, 50.1, 48.1,
    ]

    // The geomagnetic north pole, IGRF epoch 2020.
    private static let poleLatitude = 80.65
    private static let poleLongitude = -72.68

    /// Degrees inside the oval before the aurora is overhead rather than to the north.
    private static let overheadMargin: Double = 5

    /// Aurora is high enough to be seen a degree equatorward of the oval's edge.
    private static let possibleMargin: Double = -1

    /// And faintly, low on the horizon, for several degrees beyond that.
    private static let faintMargin: Double = -5
}
