import SwiftUI

/// How strongly a weather background reads.
///
/// Today gets the full treatment because it is the screen about the weather right now. Everything
/// else gets a whisper of the same hue: enough that the app feels like one place, not so much that
/// a list of saved cities competes with the forecast for attention.
enum BackgroundIntensity {
    case full
    case subtle

    var topOpacity: Double {
        switch self {
        case .full: 0.30
        case .subtle: 0.14
        }
    }

    var midOpacity: Double {
        switch self {
        case .full: 0.10
        case .subtle: 0.05
        }
    }

    var glowOpacity: Double {
        switch self {
        case .full: 0.22
        case .subtle: 0.10
        }
    }

    /// What the wash settles to at the very bottom of the screen, rather than fading to nothing.
    var floorOpacity: Double {
        switch self {
        case .full: 0.06
        case .subtle: 0.03
        }
    }
}

/// A background that reflects the current condition and time of day.
///
/// Paints the system grouped background and lays a **low-opacity wash of the condition's hue** over
/// it, so the base is always the colour the system guarantees text contrast against and the
/// condition shifts the mood rather than replacing the palette.
///
/// The colour also gives Liquid Glass something to sample: a flat white page renders glass as a
/// faint grey rectangle.
///
/// The wash drifts slowly, at roughly half a minute per cycle, and stops entirely under **Reduce
/// Motion**.
///
/// The Android counterpart is `core/designsystem/component/WeatherBackground.kt`.
struct WeatherBackground: View {
    let condition: WeatherCondition
    let isDaytime: Bool
    var intensity: BackgroundIntensity = .full

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift: Double = WeatherBackground.driftMidpoint

    private var tint: Color {
        WeatherPalette.tint(for: condition, isDaytime: isDaytime)
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.skyBackground

                // Runs the full height, not to `.center`, so there is no hard edge across the page
                // and the area behind the glass tab bar is not flat background. The floor opacity is
                // non-zero for the same reason: glass needs something behind it to sample.
                LinearGradient(
                    stops: [
                        .init(color: tint.opacity(intensity.topOpacity), location: 0),
                        .init(color: tint.opacity(intensity.midOpacity), location: 0.45),
                        .init(color: tint.opacity(intensity.floorOpacity), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )

                // A soft glow standing in for where the light is coming from. Both its position
                // and its strength move: position alone was imperceptible, changing any given
                // pixel by about one value in 255, which is not an animation but a rounding error.
                RadialGradient(
                    colors: [
                        tint.opacity(intensity.glowOpacity * (Self.glowOpacityMin + drift * Self.glowOpacityTravel)),
                        .clear,
                    ],
                    center: UnitPoint(
                        x: Self.glowXMin + drift * Self.glowXTravel,
                        y: Self.glowY
                    ),
                    startRadius: 0,
                    endRadius: proxy.size.width * Self.glowRadiusRatio
                )
            }
        }
        .ignoresSafeArea()
        // Decorative by definition: it carries mood, and every fact it hints at is stated in text
        // elsewhere on the screen.
        .accessibilityHidden(true)
        .onAppear(perform: startDrift)
        .onChange(of: reduceMotion) { _, _ in startDrift() }
    }

    private func startDrift() {
        guard !reduceMotion else {
            drift = Self.driftMidpoint
            return
        }
        withAnimation(.linear(duration: Self.driftDuration).repeatForever(autoreverses: true)) {
            drift = 1
        }
    }

    /// Slow enough to read as changing light rather than as something moving.
    private static let driftDuration: TimeInterval = 18
    private static let driftMidpoint = 0.5
    private static let glowXMin = 0.15
    private static let glowXTravel = 0.7
    private static let glowY = 0.1

    /// Tighter than the full width, so the glow reads as a source of light rather than a haze.
    private static let glowRadiusRatio = 0.6

    /// The glow breathes between 70% and 130% of its base strength as it travels.
    private static let glowOpacityMin = 0.7
    private static let glowOpacityTravel = 0.6
}

extension View {
    /// Places a weather background behind this view.
    func weatherBackground(
        condition: WeatherCondition,
        isDaytime: Bool,
        intensity: BackgroundIntensity = .full
    )
        -> some View
    {
        background {
            WeatherBackground(condition: condition, isDaytime: isDaytime, intensity: intensity)
        }
        // Published to the content in front, not just painted behind it: the detail tiles and any
        // other surface on the page mix a little of it into their own fill.
        .environment(\.weatherTint, WeatherPalette.tint(for: condition, isDaytime: isDaytime))
    }
}
