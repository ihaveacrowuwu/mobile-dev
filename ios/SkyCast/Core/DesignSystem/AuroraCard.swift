import SwiftUI

/// The aurora card's content, already formatted.
struct AuroraReading: Equatable {
    /// "Not tonight", "Worth looking north", and so on.
    let headline: String
    /// The one line that says what to do about it.
    let detail: String
    let kpNowLabel: String
    let kpPeakLabel: String
    /// Where this place's threshold sits across the Kp scale, 0–1.
    let reachFraction: Double
    /// Where tonight's forecast peak sits on the same scale, 0–1.
    let peakFraction: Double
    let announcement: String
}

/// Whether the aurora is worth going outside for.
///
/// The bar shows *how far off* it is: a green line at the disturbance this place needs, and a
/// marker for how far tonight's forecast gets.
///
/// The Kotlin twin is `AuroraCard.kt`.
struct AuroraCard: View {
    let reading: AuroraReading

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(reading.headline)
                .font(.headline)
            Text(reading.detail)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    // The scale itself: quiet on the left, storm on the right.
                    Capsule()
                        .fill(
                            .linearGradient(
                                colors: [Self.quiet, Self.active, Self.storm],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .opacity(scaleOpacity)

                    // Where this place starts seeing anything, the threshold the reader is waiting for.
                    Rectangle()
                        .fill(Self.aurora)
                        .frame(width: thresholdWidth, height: barHeight)
                        .offset(x: proxy.size.width * min(max(reading.reachFraction, 0), 1))

                    Circle()
                        .fill(.white)
                        .overlay(Circle().stroke(Self.storm, lineWidth: 1.5))
                        .frame(width: markerDiameter, height: markerDiameter)
                        .offset(
                            x: min(
                                max(proxy.size.width * reading.peakFraction - markerDiameter / 2, 0),
                                proxy.size.width - markerDiameter
                            )
                        )
                }
                .frame(height: barHeight)
            }
            .frame(height: barHeight)

            HStack {
                Text(reading.kpNowLabel)
                Spacer()
                Text(reading.kpPeakLabel)
            }
            .font(.caption.weight(.medium))
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .frostedCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(reading.announcement)
    }

    // The aurora's own colours, not the theme's: a green curtain over a quiet-to-stormy scale.
    private static let quiet = Color(red: 0.23, green: 0.29, blue: 0.42)
    private static let active = Color(red: 0.42, green: 0.35, blue: 0.66)
    private static let storm = Color(red: 0.75, green: 0.31, blue: 0.48)
    private static let aurora = Color(red: 0.36, green: 0.88, blue: 0.63)

    private let barHeight: CGFloat = 16
    private let thresholdWidth: CGFloat = 3
    private let markerDiameter: CGFloat = 14
    private let scaleOpacity: Double = 0.75
}

#Preview {
    AuroraCard(
        reading: AuroraReading(
            headline: "A faint chance, for a camera",
            detail: "London needs Kp 6 before the aurora reaches this far. Tonight peaks at Kp 4.7.",
            kpNowLabel: "Now Kp 4",
            kpPeakLabel: "Tonight up to Kp 4.7",
            reachFraction: 6.0 / 9.0,
            peakFraction: 4.67 / 9.0,
            announcement: "A faint chance. London needs Kp 6; tonight peaks at 4.7."
        )
    )
    .padding()
    .background(Color.skyBackground)
}
