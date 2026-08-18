import SwiftUI

// The METAR tab's sections.
//
// Split out of `MetarScreen.swift` when the redesign pushed that file past SwiftLint's 500-line limit. The
// division is the useful one rather than an arbitrary cut: this file is all presentation, and
// `MetarScreen.swift` keeps the screen's `MetarUiState`, its view model and the content that arranges these.
//
// `private` became internal in the move, which is a small loss of enclosure. The reason to accept it is that
// these views are meaningless outside the METAR tab, which their names say.

/// The flight-rules category, as a filled disc.
///
/// The colours are the conventional ones, green visual, blue marginal, amber instrument, violet low
/// instrument, and they come from the weather palette rather than being invented here, so they are the same
/// contrast-checked colours the rest of the app uses.
///
/// This was a card of its own, sitting under an equally prominent block of station details. Both were about
/// the same thing, *this airport, right now*, so they were merged into one identity card and this became
/// the piece that lives in its top-right corner.
struct FlightCategoryDisc: View {
    let category: FlightCategory

    private var colour: Color {
        switch category {
        case .vfr: WeatherPalette.wind
        case .mvfr: WeatherPalette.humidity
        case .ifr: WeatherPalette.sunset
        case .lifr: WeatherPalette.pressure
        case .unknown: Color.secondary
        }
    }

    var body: some View {
        ZStack {
            Circle().fill(colour.opacity(discOpacity))
            Text(category.label)
                .font(.headline)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .padding(Spacing.xs)
        }
        .frame(width: discDiameter, height: discDiameter)
        .accessibilityHidden(true)
    }

    private let discDiameter: CGFloat = 64
    /// Enough colour to read the category across a room, light enough to keep its label legible on it.
    private let discOpacity: Double = 0.35
}

extension FlightCategory {
    /// What the abbreviation means, for everyone who is not a pilot.
    var meaning: String {
        switch self {
        case .vfr:
            "Visual flight rules: the ceiling is above 3,000 ft and visibility better than 5 miles."
        case .mvfr:
            "Marginal visual: ceiling 1,000–3,000 ft, or visibility 3–5 miles."
        case .ifr:
            "Instrument flight rules: ceiling 500–1,000 ft, or visibility 1–3 miles."
        case .lifr:
            "Low instrument: ceiling below 500 ft, or visibility under a mile."
        case .unknown:
            "This report did not include a flight category."
        }
    }
}

/// The sky, drawn to scale, with the ceiling marked. See ``SkyLayersDiagram``.
struct SkySection: View {
    let report: MetarReport

    private var layers: [SkyLayer] {
        let ceiling = report.ceilingFeet
        return report.clouds.compactMap { layer in
            guard let base = layer.baseFeet else { return nil }
            return SkyLayer(
                cover: layer.cover,
                baseFeet: base,
                coverFraction: SkyLayer.coverFraction(for: layer.cover),
                isCeiling: base == ceiling
            )
        }
    }

    private var announcement: String {
        guard let ceiling = report.ceilingFeet else {
            return "Cloud reported, but no ceiling: nothing broken or overcast."
        }
        return "Ceiling at \(ceiling) feet above the field"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SectionHeader("Sky")
            if layers.isEmpty {
                // A clear sky is a real observation, not missing data, and it deserves saying rather than an
                // empty frame.
                Text("No cloud reported: a clear sky above the field.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                SkyLayersDiagram(layers: layers, announcement: announcement)
                    .frostedCard()
            }
        }
    }
}

/// Wind as a compass, because a bearing is a direction and not a magnitude.
struct WindSection: View {
    let report: MetarReport

    private var description: String {
        guard let knots = report.windSpeedKnots, knots > 0 else { return "Calm" }
        guard let bearing = report.windDirectionDegrees else { return "Variable direction" }
        return "From \(bearing)° (\(Weather.cardinal(for: bearing)))"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SectionHeader("Wind")
            HStack(spacing: Spacing.md) {
                // Calm and variable winds have no bearing to point at, so the needle is parked north and the
                // text carries the meaning instead of the drawing implying a direction never reported.
                WindCompass(
                    degrees: Double(report.windDirectionDegrees ?? 0),
                    colour: WeatherPalette.wind
                )
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text("\(report.windSpeedKnots ?? 0) kt")
                        .font(.title3.weight(.semibold))
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding(Spacing.md)
            .frostedCard()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Wind \(report.windSpeedKnots ?? 0) knots. \(description)")
        }
    }
}

/// The figures a pilot works out, rather than reads.
///
/// A METAR reports none of these. See ``MetarReport`` for how each is derived, and `MetarDerivationsTests`
/// for the references they are checked against.
struct DerivedSection: View {
    let report: MetarReport

    private var rows: [(label: String, value: String)] {
        var result: [(String, String)] = []
        if let humidity = report.relativeHumidityPercent {
            result.append(("Relative humidity", "\(humidity)%"))
        }
        if let spread = report.dewPointSpreadCelsius {
            result.append(("Dew point spread", String(format: "%.1f°C, %@", spread, Self.fogRisk(spread))))
        }
        if let density = report.densityAltitudeFeet {
            let formatter = NumberFormatter()
            formatter.numberStyle = .decimal
            let value = formatter.string(from: NSNumber(value: density)) ?? "\(density)"
            result.append(("Density altitude", "about \(value) ft"))
        }
        return result
    }

    /// How close the air is to saturating, in words.
    private static func fogRisk(_ spreadCelsius: Double) -> String {
        switch spreadCelsius {
        case ...1: "fog or low cloud likely"
        case ...3: "fog possible"
        default: "fog unlikely"
        }
    }

    var body: some View {
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                SectionHeader("Worked out from the report")
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        if index > 0 {
                            Divider()
                        }
                        HStack {
                            Text(row.label).foregroundStyle(.secondary)
                            Spacer()
                            Text(row.value)
                        }
                        .padding(.vertical, Spacing.sm)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(row.label), \(row.value)")
                    }
                }
                .padding(.horizontal, Spacing.md)
                .frostedCard()
            }
        }
    }
}

struct RawReportCard: View {
    let raw: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Raw report")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(raw)
                // Monospaced: a METAR is a fixed-format line, and the groups stay aligned.
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .frostedCard()
    }
}

struct DecodedRows: View {
    let report: MetarReport

    var body: some View {
        VStack(spacing: 0) {
            Text("Decoded")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.md)
                .padding(.top, Spacing.sm)

            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                if index > 0 {
                    Divider().padding(.horizontal, Spacing.md)
                }
                HStack {
                    Text(row.label)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(row.value)
                        .multilineTextAlignment(.trailing)
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(row.label), \(row.value)")
            }
        }
        .frostedCard()
    }

    private var rows: [(label: String, value: String)] {
        var result: [(String, String)] = [
            ("Wind", windDescription),
            ("Visibility", visibilityDescription),
        ]
        if let temperature = report.temperatureCelsius {
            result.append(("Temperature", "\(Int(temperature.rounded()))°C"))
        }
        if let dewPoint = report.dewPointCelsius {
            result.append(("Dew point", "\(Int(dewPoint.rounded()))°C"))
        }
        if let altimeter = report.altimeterHectopascals {
            let inches = String(format: "%.2f", altimeter / Self.hectopascalsPerInch)
            result.append(("Altimeter", "\(Int(altimeter.rounded())) hPa · \(inches) inHg"))
        }
        result.append(("Cloud", cloudDescription))
        return result
    }

    private var windDescription: String {
        guard let knots = report.windSpeedKnots, knots > 0 else { return "Calm" }
        guard let bearing = report.windDirectionDegrees else { return "Variable at \(knots) kt" }
        return "\(bearing)° at \(knots) kt"
    }

    private var visibilityDescription: String {
        guard let miles = report.visibilityStatuteMiles else { return "N/A" }
        let text = miles == miles.rounded() ? "\(Int(miles))" : "\(miles)"
        return report.visibilityIsOrGreater ? "\(text)+ mi" : "\(text) mi"
    }

    private var cloudDescription: String {
        guard !report.clouds.isEmpty else { return "No cloud reported" }
        return report.clouds
            .map { layer in
                layer.baseFeet.map { "\(layer.cover) at \($0) ft" } ?? layer.cover
            }
            .joined(separator: ", ")
    }

    /// The same pressure in the unit the other half of the world's charts use.
    private static let hectopascalsPerInch = 33.8639
}
