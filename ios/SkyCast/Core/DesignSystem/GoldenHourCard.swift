import SwiftUI

/// The golden-hour card's content, already formatted.
struct GoldenHourReading: Equatable {
    let goldenRangeLabel: String
    let blueRangeLabel: String
    /// Where now sits across the whole golden-plus-blue span, 0–1, or `nil` when it is not happening.
    let progress: Double?
    let announcement: String
}

/// The evening's light, as a band that runs from gold to blue to night.
///
/// Computed from the sun's altitude rather than read from a weather API, so its length depends on
/// where you are. See ``SolarCalculator``.
///
/// The Kotlin twin is `GoldenHourCard.kt`.
struct GoldenHourCard: View {
    let reading: GoldenHourReading

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                label("Golden hour", range: reading.goldenRangeLabel, colour: Self.golden)
                Spacer()
                label("Blue hour", range: reading.blueRangeLabel, colour: Self.blue, alignment: .trailing)
            }

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(
                        .linearGradient(
                            colors: [Self.golden, Self.amber, Self.blue, Self.night],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(height: bandHeight)

                if let progress = reading.progress {
                    // A marker for now, so the band answers "how long have I got?" and not only "when".
                    GeometryReader { proxy in
                        Circle()
                            .fill(.white)
                            .overlay(Circle().stroke(Self.night.opacity(markerRingOpacity), lineWidth: 1))
                            .frame(width: markerDiameter, height: markerDiameter)
                            .position(
                                x: min(
                                    max(proxy.size.width * progress, markerDiameter),
                                    proxy.size.width - markerDiameter
                                ),
                                y: bandHeight / 2
                            )
                    }
                    .frame(height: bandHeight)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .frostedCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(reading.announcement)
    }

    private func label(
        _ title: String,
        range: String,
        colour: Color,
        alignment: HorizontalAlignment = .leading
    )
        -> some View
    {
        VStack(alignment: alignment, spacing: Spacing.xxs) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(colour)
            Text(range)
                .font(.subheadline)
                .monospacedDigit()
        }
    }

    // The light itself, not theme colours: this band is a picture of the sky at dusk, and the sky does not
    // take its colours from an appearance setting.
    private static let golden = Color(red: 0.96, green: 0.72, blue: 0.35)
    private static let amber = Color(red: 0.88, green: 0.48, blue: 0.29)
    private static let blue = Color(red: 0.29, green: 0.43, blue: 0.66)
    private static let night = Color(red: 0.11, green: 0.14, blue: 0.25)

    private let bandHeight: CGFloat = 18
    private let markerDiameter: CGFloat = 12
    private let markerRingOpacity: Double = 0.55
}

#Preview {
    GoldenHourCard(
        reading: GoldenHourReading(
            goldenRangeLabel: "19:29 – 20:37",
            blueRangeLabel: "20:37 – 20:51",
            progress: 0.4,
            announcement: "Golden hour from 19:29 to 20:37, then blue hour until 20:51"
        )
    )
    .padding()
}
