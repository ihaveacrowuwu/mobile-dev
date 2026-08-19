import Foundation

/// The aurora card's copy for a place, or `nil` when there is nothing worth saying.
///
/// Returns `nil` for the tropics rather than a card reading "never". Somewhere within reach of the oval a quiet
/// night is still information, "you need Kp 6, tonight reaches 4.7" tells you to look again tomorrow, but at a
/// geomagnetic latitude the aurora has never reached, a permanent negative is just clutter.
func auroraReading(for location: SavedLocation, weather: SpaceWeather) -> AuroraReading? {
    let magneticLatitude = AuroraCalculator.geomagneticLatitude(
        latitude: location.latitude,
        longitude: location.longitude
    )
    guard let minimumKp = AuroraCalculator.minimumKpForChance(geomagneticLatitude: magneticLatitude) else {
        return nil
    }

    let peak = weather.peakAhead()
    let bestKp = max(weather.kpNow, peak?.kp ?? 0)
    let chance = AuroraCalculator.chance(kp: bestKp, geomagneticLatitude: magneticLatitude)

    let headline: String = switch chance {
    case .none: "Not tonight"
    case .faintOnHorizon: "A faint chance, for a camera"
    case .possible: "Worth looking north"
    case .likely: "Aurora likely tonight"
    case .overhead: "Aurora overhead tonight"
    }

    let detail: String = if chance >= .possible {
        "The oval reaches \(location.name) tonight. It needs Kp \(minimumKp) here, and the field is disturbed "
            + "enough. Look north, away from streetlights."
    } else {
        "\(location.name) needs Kp \(minimumKp) before the aurora reaches this far. "
            + "Tonight peaks at Kp \(formatted(bestKp))."
    }

    let kpNow = weather.stormLevel.map { storm in
        "Now Kp \(formatted(weather.kpNow)) · \(storm) storm"
    } ?? "Now Kp \(formatted(weather.kpNow))"

    return AuroraReading(
        headline: headline,
        detail: detail,
        kpNowLabel: kpNow,
        kpPeakLabel: peak.map { "Tonight up to Kp \(formatted($0.kp))" } ?? "",
        // Both fractions are of the Kp scale, so the threshold line and the forecast marker are directly
        // comparable, which is the only reason the bar says anything.
        reachFraction: Double(minimumKp) / kpMaximum,
        peakFraction: bestKp / kpMaximum,
        announcement: "\(headline). \(detail)"
    )
}

/// One decimal place, and none when it is a whole number, so "Kp 5" rather than "Kp 5.0".
private func formatted(_ kp: Double) -> String {
    kp == kp.rounded(.towardZero) ? String(Int(kp)) : String(format: "%.1f", kp)
}

private let kpMaximum = 9.0
