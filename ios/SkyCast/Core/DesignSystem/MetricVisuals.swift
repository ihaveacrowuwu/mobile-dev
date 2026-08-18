import SwiftUI

/// How a reading draws itself.
///
/// Each case is a shape that suits a *kind* of reading rather than a specific metric, so the design
/// system decides how things look and the feature layer only says which kind it has.
enum MetricVisual: Equatable {
    /// Nothing but the number. For readings with no scale worth drawing.
    case plain
    /// A share of a known range, 0 to 1, on an arc. The middle of the tile stays free for the
    /// value, so the number remains the largest thing in it.
    case gauge(Double)
    /// A bearing in degrees, with its compass point already named. For wind.
    case compass(degrees: Double, cardinal: String)
}

/// An arc showing where a reading sits on its own scale.
///
/// 240° opening downwards, so the gap gives the eye a start and an end.
struct MetricGauge: View {
    let fraction: Double
    let colour: Color

    private var clamped: Double {
        min(max(fraction, 0), 1)
    }

    var body: some View {
        ZStack {
            arc(to: 1)
                .stroke(Color.primary.opacity(trackOpacity), style: stroke)
            arc(to: clamped)
                .stroke(colour, style: stroke)
        }
        .frame(width: diameter, height: diameter)
        // The value beside it is the fact; the arc restates it. Announcing both would be a stutter.
        .accessibilityHidden(true)
        // Length is geometry, so it animates, but only when the value genuinely changes.
        .animation(.smooth, value: clamped)
    }

    private func arc(to end: Double) -> Path {
        Path { path in
            let centre = CGPoint(x: diameter / 2, y: diameter / 2)
            path.addArc(
                center: centre,
                radius: diameter / 2 - lineWidth / 2,
                startAngle: .degrees(Self.startDegrees),
                endAngle: .degrees(Self.startDegrees + Self.sweepDegrees * end),
                clockwise: false
            )
        }
    }

    private var stroke: StrokeStyle {
        StrokeStyle(lineWidth: lineWidth, lineCap: .round)
    }

    /// 150° puts the opening at the bottom, symmetrical about vertical.
    private static let startDegrees: Double = 150
    private static let sweepDegrees: Double = 240

    private let diameter: CGFloat = 44
    private let lineWidth: CGFloat = 5
    private let trackOpacity: Double = 0.12
}

/// A compass rose with a needle on the bearing the wind is coming *from*.
///
/// Meteorological convention: 270° is a westerly, blowing from the west. The needle points at the
/// source, which is what "westerly" means.
struct WindCompass: View {
    let degrees: Double
    let colour: Color

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.primary.opacity(ringOpacity), lineWidth: 1)

            // North tick, so the rose has an orientation without four letters cluttering it.
            Rectangle()
                .fill(Color.primary.opacity(tickOpacity))
                .frame(width: 1, height: tickLength)
                .offset(y: -diameter / 2 + tickLength / 2)

            Capsule()
                .fill(colour)
                .frame(width: needleWidth, height: diameter * needleLengthRatio)
                .offset(y: -diameter * needleOffsetRatio)
                .rotationEffect(.degrees(degrees))
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
        .animation(.smooth, value: degrees)
    }

    private let diameter: CGFloat = 44
    private let ringOpacity: Double = 0.15
    private let tickOpacity: Double = 0.3
    private let tickLength: CGFloat = 5
    private let needleWidth: CGFloat = 3
    private let needleLengthRatio: CGFloat = 0.42
    private let needleOffsetRatio: CGFloat = 0.21
}

/// The sun's day, as an arc from sunrise to sunset with a marker at now.
///
/// The length of the day is the caption.
struct SunPathCard: View {
    /// 0 at sunrise, 1 at sunset. Outside that range the sun is down and the marker is hidden.
    let progress: Double
    let sunriseLabel: String
    let sunsetLabel: String
    let daylightLabel: String
    /// Spoken instead of the drawing.
    let announcement: String

    var body: some View {
        SkyPathCard(
            progress: progress,
            riseLabel: sunriseLabel,
            setLabel: sunsetLabel,
            centreLabel: daylightLabel,
            riseSymbol: "sunrise.fill",
            setSymbol: "sunset.fill",
            riseColour: WeatherPalette.sunrise,
            setColour: WeatherPalette.sunset,
            markerColour: WeatherPalette.sunrise,
            announcement: announcement
        )
    }
}

/// An arc from a rise to a set, with a marker at the body's current position.
///
/// Shared by the sun card and the moon card, which differ only in times, colours and symbols.
struct SkyPathCard: View {
    /// 0 at the rise, 1 at the set. Outside that range the body is below the horizon and the marker
    /// is hidden.
    let progress: Double
    let riseLabel: String
    let setLabel: String
    let centreLabel: String
    let riseSymbol: String
    let setSymbol: String
    let riseColour: Color
    let setColour: Color
    let markerColour: Color
    /// Spoken instead of the drawing.
    let announcement: String


    private var isUp: Bool {
        progress > 0 && progress < 1
    }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            GeometryReader { proxy in
                let size = proxy.size
                ZStack(alignment: .topLeading) {
                    SkyArc()
                        .stroke(
                            Color.primary.opacity(trackOpacity),
                            style: StrokeStyle(lineWidth: 2, dash: [4, 4])
                        )

                    SkyArc()
                        .trim(from: 0, to: min(max(progress, 0), 1))
                        .stroke(
                            .linearGradient(
                                colors: [riseColour, setColour],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round)
                        )

                    if isUp {
                        Circle()
                            .fill(markerColour)
                            .frame(width: markerDiameter, height: markerDiameter)
                            .position(SkyArc.point(at: progress, in: size))
                    }
                }
            }
            .frame(height: arcHeight)

            HStack {
                label(riseLabel, systemImage: riseSymbol, colour: riseColour)
                Spacer()
                Text(centreLabel)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                Spacer()
                label(setLabel, systemImage: setSymbol, colour: setColour)
            }
        }
        .padding(Spacing.md)
        .frostedCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    private func label(_ text: String, systemImage: String, colour: Color) -> some View {
        Label {
            Text(text).font(.caption).monospacedDigit()
        } icon: {
            Image(systemName: systemImage)
                .font(.caption)
                .foregroundStyle(colour)
        }
    }

    private let arcHeight: CGFloat = 64
    private let markerDiameter: CGFloat = 10
    private let trackOpacity: Double = 0.15
}

/// A shallow arc across a tile, flat enough to read as a horizon rather than a rainbow.
///
/// Shared by the sun card and the moon card.
struct SkyArc: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: 0, y: rect.maxY))
            path.addQuadCurve(
                to: CGPoint(x: rect.maxX, y: rect.maxY),
                control: CGPoint(x: rect.midX, y: rect.minY - rect.height * Self.controlLift)
            )
        }
    }

    /// The point on the same curve at `progress`, for placing the marker.
    ///
    /// Evaluated from the quadratic directly. The parameter is not arc length, but for a curve this
    /// shallow the difference is under a pixel.
    static func point(at progress: Double, in size: CGSize) -> CGPoint {
        let along = min(max(progress, 0), 1)
        let start = CGPoint(x: 0, y: size.height)
        let end = CGPoint(x: size.width, y: size.height)
        let control = CGPoint(x: size.width / 2, y: -size.height * controlLift)
        let inverse = 1 - along
        return CGPoint(
            x: inverse * inverse * start.x + 2 * inverse * along * control.x + along * along * end.x,
            y: inverse * inverse * start.y + 2 * inverse * along * control.y + along * along * end.y
        )
    }

    /// Lifts the control point above the tile so the drawn curve peaks inside it.
    private static let controlLift: CGFloat = 0.6
}

#Preview("Gauge and compass") {
    HStack(spacing: Spacing.lg) {
        MetricGauge(fraction: 0.78, colour: WeatherPalette.humidity)
        MetricGauge(fraction: 0.2, colour: WeatherPalette.pressure)
        WindCompass(degrees: 270, colour: WeatherPalette.wind)
    }
    .padding()
}

#Preview("Sun path") {
    SunPathCard(
        progress: 0.62,
        sunriseLabel: "05:50",
        sunsetLabel: "20:18",
        daylightLabel: "14h 28m",
        announcement: "Sunrise 05:50, sunset 20:18, 14 hours 28 minutes of daylight"
    )
    .padding()
    .background(Color.skyBackground)
}
