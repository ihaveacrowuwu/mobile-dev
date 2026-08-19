import SwiftUI

/// The hero reading: place, condition badge, and one very large temperature.
///
/// Shared by the Home tab and the pushed location-detail screen. Extracted rather than
/// duplicated so the two can never drift, the detail screen showing a differently-rounded
/// temperature from the tab that pushed it would read as a bug.
///
/// The Android counterpart is `ui/common/CurrentConditionsHeader.kt`.
struct CurrentConditionsHero: View {
    let weather: Weather
    let unit: TemperatureUnit
    /// `false` where the surrounding screen already names the place, Home has a location
    /// switcher and the detail screen an identity block, and "London" twice within a hundred
    /// points reads as a mistake. The spoken announcement still includes it either way, since a
    /// VoiceOver user has no such visual context.
    var showsLocationName = true
    /// `nil` makes the block inert. The Home tab passes a closure to push the detail screen;
    /// the detail screen itself has nowhere further to go.
    var onTap: (() -> Void)?

    private var temperature: Int {
        Int(unit.convertFromCelsius(weather.temperatureCelsius).rounded())
    }

    private var feelsLike: Int {
        Int(unit.convertFromCelsius(weather.feelsLikeCelsius).rounded())
    }

    /// One combined announcement. Without this a VoiceOver user hears "London", "22", "°C",
    /// "Clear sky", "Feels like 21°C" as five disconnected fragments.
    private var announcement: String {
        "\(weather.locationName), \(temperature)\(unit.symbol), "
            + "\(weather.description), feels like \(feelsLike)\(unit.symbol)"
    }

    var body: some View {
        if let onTap {
            // A Button, not a tap gesture: it gives the block a keyboard focus ring, the
            // "Button" VoiceOver trait and the standard press feedback, none of which a bare
            // `onTapGesture` provides.
            Button(action: onTap) { reading }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(announcement)
                .accessibilityHint("Shows full conditions for this place")
        } else {
            reading
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(announcement)
        }
    }

    private var reading: some View {
        VStack(spacing: Spacing.sm) {
            if showsLocationName {
                Text(weather.locationName)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
            }

            ConditionBadge(condition: weather.condition, isDaytime: weather.isDaytime)

            HStack(alignment: .top, spacing: 0) {
                // Scales with Dynamic Type; see ScaledHeroTemperature.
                ScaledHeroTemperature(text: "\(temperature)")
                Text(unit.symbol)
                    .font(.title2)
                    .padding(.top, Spacing.md)
            }

            Text(weather.description)
                .font(.body)
                .foregroundStyle(.secondary)

            Text("Feels like \(feelsLike)\(unit.symbol)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        // No glass. This is the page's content, not a floating layer over it, and Liquid Glass is
        // for the latter. Two things made the card actively wrong
        // once the rest of Home came together: the toolbar's own glass now passes directly over it
        // as the page scrolls, which is glass on glass; and a pane around the largest text on the
        // screen boxes in the one element that should feel like it is simply *on* the sky.
        //
        // Vertical padding only. The horizontal inset comes from the page, so the reading stays
        // aligned with the strip and tiles beneath it rather than sitting in its own margin.
        // Sized so the pill-to-icon gap matches the icon-to-temperature one, which measures 44 points:
        // the icon and the temperature are only 8 apart in layout terms, and the rest of that gap is the
        // whitespace above a very large glyph. Matching it by eye needs real space here.
        .padding(.top, Spacing.xl)
        .padding(.bottom, Spacing.lg)
    }
}
