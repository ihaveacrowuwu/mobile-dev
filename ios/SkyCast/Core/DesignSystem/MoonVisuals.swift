import SwiftUI

/// The Moon, drawn at its actual phase.
///
/// ## The geometry
///
/// A lit lunar disc is bounded by two curves: the **limb**, a semicircle of radius *R*, and the
/// **terminator**, the day/night line, which projects to a half-ellipse of semi-axis *R*·cos θ where
/// θ is the elongation. The sign of that cosine is what makes one formula cover the whole month:
/// positive gives a crescent bulging away from the limb, negative gives a gibbous moon bulging past
/// the centre, and zero gives the straight edge of a quarter moon.
struct MoonDisc: View {
    /// Elongation from the Sun in degrees: 0 new, 90 first quarter, 180 full, 270 last quarter.
    let elongationDegrees: Double
    let diameter: CGFloat
    /// Draws the glow and the craters. Off for the small discs in the "coming up" row, where they
    /// would be sub-pixel noise.
    var showsDetail = true

    var body: some View {
        ZStack {
            if showsDetail {
                // Moonlight. Scaled with the lit fraction, so a new moon does not glow.
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [Self.glow.opacity(glowOpacity), .clear],
                            center: .center,
                            startRadius: diameter * 0.35,
                            endRadius: diameter * 0.85
                        )
                    )
                    .frame(width: diameter * 1.7, height: diameter * 1.7)
                    .blur(radius: diameter * 0.06)
            }

            // The unlit disc, kept faintly visible for earthshine.
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Self.darkSide, Self.darkSideEdge],
                        center: UnitPoint(x: 0.4, y: 0.35),
                        startRadius: 0,
                        endRadius: diameter * 0.7
                    )
                )
                .frame(width: diameter, height: diameter)

            // The lit region, and the craters clipped to it.
            MoonTerminator(elongationDegrees: elongationDegrees)
                .fill(
                    RadialGradient(
                        colors: [Self.litCentre, Self.litEdge],
                        center: UnitPoint(x: 0.38, y: 0.32),
                        startRadius: 0,
                        endRadius: diameter * 0.75
                    )
                )
                .frame(width: diameter, height: diameter)
                .overlay {
                    if showsDetail {
                        craters
                            .frame(width: diameter, height: diameter)
                            .clipShape(MoonTerminator(elongationDegrees: elongationDegrees))
                    }
                }

            // A hairline limb, so the disc still has an edge against a bright sky.
            Circle()
                .strokeBorder(Self.limb, lineWidth: 0.5)
                .frame(width: diameter, height: diameter)
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }

    /// The maria, as soft grey blots at fixed positions so the face does not reshuffle between
    /// redraws. They roughly follow the near side: Tranquillitatis and Imbrium upper left, Crisium
    /// right.
    private var craters: some View {
        ZStack {
            ForEach(Array(Self.maria.enumerated()), id: \.offset) { _, mare in
                Circle()
                    .fill(Self.mareTint.opacity(mare.opacity))
                    .frame(width: diameter * mare.size, height: diameter * mare.size)
                    .offset(x: diameter * mare.x, y: diameter * mare.y)
                    .blur(radius: diameter * 0.015)
            }
        }
    }

    private var glowOpacity: Double {
        let fraction = MoonCalculator.illuminatedFraction(elongationDegrees: elongationDegrees)
        return 0.10 + 0.45 * fraction
    }

    private struct Mare {
        let x: CGFloat
        let y: CGFloat
        let size: CGFloat
        let opacity: Double
    }

    private static let maria: [Mare] = [
        Mare(x: -0.14, y: -0.16, size: 0.30, opacity: 0.22),
        Mare(x: 0.06, y: -0.24, size: 0.20, opacity: 0.16),
        Mare(x: 0.24, y: -0.05, size: 0.16, opacity: 0.20),
        Mare(x: -0.05, y: 0.14, size: 0.26, opacity: 0.14),
        Mare(x: 0.16, y: 0.26, size: 0.13, opacity: 0.17),
        Mare(x: -0.26, y: 0.10, size: 0.12, opacity: 0.15),
    ]

    private static let litCentre = Color(red: 0.98, green: 0.97, blue: 0.93)
    private static let litEdge = Color(red: 0.80, green: 0.79, blue: 0.76)
    private static let darkSide = Color(red: 0.13, green: 0.14, blue: 0.20)
    private static let darkSideEdge = Color(red: 0.07, green: 0.08, blue: 0.12)
    private static let mareTint = Color(red: 0.42, green: 0.43, blue: 0.47)
    private static let limb = Color.white.opacity(0.18)
    private static let glow = Color(red: 0.85, green: 0.89, blue: 1.0)
}

/// The lit region of the disc at a given elongation.
///
/// Traced as the limb from top to bottom, then the terminator back from bottom to top, sampled at
/// ``steps`` points along each curve.
struct MoonTerminator: Shape {
    let elongationDegrees: Double

    func path(in rect: CGRect) -> Path {
        let radius = min(rect.width, rect.height) / 2
        let centre = CGPoint(x: rect.midX, y: rect.midY)
        let theta = elongationDegrees * .pi / 180

        // Signed semi-axis of the terminator: +R at new, 0 at the quarters, -R at full.
        let terminatorAxis = radius * cos(theta)
        // Waxing moons are lit on the right in the northern hemisphere, waning on the left.
        let side: CGFloat = elongationDegrees < 180 ? 1 : -1

        return Path { path in
            path.move(to: CGPoint(x: centre.x, y: centre.y - radius))

            for step in 0...Self.steps {
                let angle = Double(step) / Double(Self.steps) * .pi
                path.addLine(
                    to: CGPoint(
                        x: centre.x + side * radius * sin(angle),
                        y: centre.y - radius * cos(angle)
                    )
                )
            }
            for step in stride(from: Self.steps, through: 0, by: -1) {
                let angle = Double(step) / Double(Self.steps) * .pi
                path.addLine(
                    to: CGPoint(
                        x: centre.x + side * terminatorAxis * sin(angle),
                        y: centre.y - radius * cos(angle)
                    )
                )
            }
            path.closeSubpath()
        }
    }

    private static let steps = 48
}

/// The night sky the Moon hangs in. Dark in **both** appearances.
struct NightSkyPanel<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .nightSky(in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
    }
}

extension View {
    /// Puts the night sky behind this view, clipped to `shape`.
    func nightSky(in shape: some Shape = Rectangle()) -> some View {
        background {
            shape
                .fill(
                    LinearGradient(
                        colors: [NightSky.zenith, NightSky.horizon],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay { Starfield().clipShape(shape) }
        }
    }
}

/// The sky's two colours.
///
/// Outside ``NightSkyPanel`` because a generic type cannot hold static stored properties, and the
/// panel has to be generic to take arbitrary content.
enum NightSky {
    static let zenith = Color(red: 0.05, green: 0.06, blue: 0.14)
    static let horizon = Color(red: 0.11, green: 0.13, blue: 0.24)
}

/// Stars at fixed positions, generated once from a fixed seed so the layout is stable across
/// redraws.
struct Starfield: View {
    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                ForEach(Array(Self.stars.enumerated()), id: \.offset) { _, star in
                    Circle()
                        .fill(.white.opacity(star.opacity))
                        .frame(width: star.size, height: star.size)
                        .position(
                            x: star.x * proxy.size.width,
                            y: star.y * proxy.size.height
                        )
                }
            }
        }
        .accessibilityHidden(true)
    }

    private struct Star {
        let x: CGFloat
        let y: CGFloat
        let size: CGFloat
        let opacity: Double
    }

    /// A small linear congruential generator, so the layout is reproducible across launches and
    /// platforms.
    private static let stars: [Star] = {
        var seed: UInt64 = 0x5CA5_7CA5
        func next() -> Double {
            seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
            return Double(seed >> 11) / Double(UInt64(1) << 53)
        }
        return (0..<70).map { _ in
            Star(
                x: next(),
                y: next(),
                size: 0.6 + next() * 1.6,
                opacity: 0.20 + next() * 0.55
            )
        }
    }()
}

/// Progress through the lunar month, as a ring with the four principal phases marked.
struct LunarCycleRing: View {
    /// 0 at new moon, 1 at the next new moon.
    let cycleFraction: Double
    let diameter: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .stroke(.white.opacity(trackOpacity), lineWidth: lineWidth)

            Circle()
                .trim(from: 0, to: min(max(cycleFraction, 0), 1))
                .stroke(
                    AngularGradient(
                        colors: [Self.start, Self.mid, Self.end],
                        center: .center,
                        startAngle: .degrees(0),
                        endAngle: .degrees(360)
                    ),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                // Trim starts at three o'clock; the month should start at the top.
                .rotationEffect(.degrees(-90))

            ForEach([0.0, 0.25, 0.5, 0.75], id: \.self) { position in
                Circle()
                    .fill(.white.opacity(tickOpacity))
                    .frame(width: tickDiameter, height: tickDiameter)
                    .offset(y: -diameter / 2)
                    .rotationEffect(.degrees(position * 360))
            }

            Circle()
                .fill(.white)
                .frame(width: markerDiameter, height: markerDiameter)
                .shadow(color: .white.opacity(0.6), radius: 4)
                .offset(y: -diameter / 2)
                .rotationEffect(.degrees(min(max(cycleFraction, 0), 1) * 360))
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }

    private let lineWidth: CGFloat = 3
    private let trackOpacity: Double = 0.16
    private let tickOpacity: Double = 0.45
    private let tickDiameter: CGFloat = 4
    private let markerDiameter: CGFloat = 8

    private static let start = Color(red: 0.45, green: 0.52, blue: 0.85)
    private static let mid = Color(red: 0.95, green: 0.94, blue: 0.86)
    private static let end = Color(red: 0.45, green: 0.52, blue: 0.85)
}

#Preview("The month, in eight steps") {
    ScrollView {
        VStack(spacing: Spacing.lg) {
            NightSkyPanel {
                ZStack {
                    LunarCycleRing(cycleFraction: 0.21, diameter: 200)
                    MoonDisc(elongationDegrees: 76, diameter: 160)
                }
            }

            ForEach([0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0], id: \.self) { elongation in
                HStack(spacing: Spacing.md) {
                    NightSkyPanel {
                        MoonDisc(elongationDegrees: elongation, diameter: 70)
                    }
                    Text("\(Int(elongation))°")
                        .font(.caption.monospacedDigit())
                }
            }
        }
        .padding()
    }
}
