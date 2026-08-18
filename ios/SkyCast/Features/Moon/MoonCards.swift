import SwiftUI

// The Moon tab's cards.
//
// Split out of `MoonScreen.swift` when that file passed SwiftLint's 500-line limit. The division is
// the useful one rather than an arbitrary cut: this file is all drawing, and `MoonScreen.swift` is all
// state, the screen's `MoonUiState`, its view model and the content that arranges these.
//
// `private` became internal in the move. That is a real (if small) loss of enclosure, and the reason
// to accept it is that these four views are meaningless outside the Moon tab, which their names say.

struct MoonHero: View {
    let snapshot: MoonSnapshot
    let timeZone: TimeZone

    /// Grows with the text size, so this scales with Dynamic Type rather than staying a fixed 160
    /// points while the labels around it double.
    @ScaledMetric(relativeTo: .largeTitle) private var discScale: CGFloat = 1

    /// Scaled, but capped.
    ///
    /// Uncapped, the accessibility sizes take this past 360 points and the hero fills the screen on
    /// its own, pushing the phase name, the thing a reader at that text size is *least* able to go
    /// hunting for, below the fold. Artwork should give way to text when text needs the room, so it
    /// keeps growing, just not without limit. Checked at AccessibilityXXXL.
    private var discDiameter: CGFloat {
        min(baseDiscDiameter * discScale, maximumDiscDiameter)
    }

    private let baseDiscDiameter: CGFloat = 160
    private let maximumDiscDiameter: CGFloat = 210

    var body: some View {
        NightSkyPanel {
            VStack(spacing: Spacing.md) {
                ZStack {
                    LunarCycleRing(
                        cycleFraction: snapshot.cycleFraction,
                        diameter: discDiameter * 1.28
                    )
                    MoonDisc(
                        elongationDegrees: snapshot.elongationDegrees,
                        diameter: discDiameter
                    )
                }
                .padding(.bottom, Spacing.xs)

                VStack(spacing: Spacing.xxs) {
                    Text(snapshot.phase.label)
                        .font(.title2.weight(.semibold))
                    Text("\(snapshot.illuminatedPercent)% lit · \(ageDescription)")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(secondaryOpacity))
                    if let countdown = fullMoonCountdown {
                        Text(countdown)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(tertiaryOpacity))
                            .padding(.top, Spacing.xxs)
                    }
                }
                // The panel is dark in both appearances, so its text is white in both, the one
                // place in the app that does not take its colour from the environment.
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.md)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    private var ageDescription: String {
        let days = snapshot.ageDays
        return days < 1
            ? "less than a day old"
            : "\(Int(days.rounded())) days old"
    }

    private var fullMoonCountdown: String? {
        guard let full = snapshot.nextFullMoon else { return nil }
        let days = Int((full.date.timeIntervalSince(snapshot.date) / 86_400).rounded())
        switch days {
        case 0: return "Full moon tonight"
        case 1: return "Full moon tomorrow"
        default: return "Full moon in \(days) days"
        }
    }

    /// One sentence, because VoiceOver reading "Waxing crescent", "37% lit", "6 days old" and a
    /// countdown as four stops makes the reader assemble the fact themselves.
    private var announcement: String {
        var parts = ["\(snapshot.phase.label), \(snapshot.illuminatedPercent) percent lit, \(ageDescription)"]
        if let countdown = fullMoonCountdown {
            parts.append(countdown)
        }
        return parts.joined(separator: ". ")
    }

    private let secondaryOpacity: Double = 0.85
    private let tertiaryOpacity: Double = 0.65
}

/// Moonrise to moonset, on the same arc the sun card uses.
struct MoonPathCard: View {
    let snapshot: MoonSnapshot
    let timeZone: TimeZone

    var body: some View {
        SkyPathCard(
            progress: progress,
            riseLabel: label(snapshot.moonrise),
            setLabel: label(snapshot.moonset),
            centreLabel: spanLabel,
            riseSymbol: "moonrise.fill",
            setSymbol: "moonset.fill",
            riseColour: WeatherPalette.onMoonContainer,
            setColour: WeatherPalette.pressure,
            markerColour: WeatherPalette.onMoonContainer,
            announcement: announcement
        )
    }

    /// 0 at moonrise, 1 at moonset, and outside 0…1 whenever the Moon is below the horizon, which
    /// hides the marker rather than parking it at an end.
    private var progress: Double {
        guard let rise = snapshot.moonrise, let span = snapshot.timeAboveHorizon, span > 0 else {
            return -1
        }
        return snapshot.date.timeIntervalSince(rise) / span
    }

    private var spanLabel: String {
        guard let span = snapshot.timeAboveHorizon else { return "" }
        let hours = Int(span) / 3_600
        let minutes = (Int(span) % 3_600) / 60
        return "\(hours)h \(minutes)m up"
    }

    private func label(_ date: Date?) -> String {
        guard let date else { return "" }
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = timeZone
        return formatter.string(from: date)
    }

    private var announcement: String {
        "Moon rises at \(label(snapshot.moonrise)), sets at \(label(snapshot.moonset)), \(spanLabel)"
    }
}

/// How far away the Moon is, and what that means.
struct MoonDistanceCard: View {
    let snapshot: MoonSnapshot

    var body: some View {
        HStack(spacing: Spacing.md) {
            // The same gauge the humidity and pressure tiles use, so "where in its range is this?"
            // looks the same wherever the app asks it.
            MetricGauge(fraction: snapshot.distanceFraction, colour: WeatherPalette.pressure)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(distanceLabel)
                    .font(.title3.weight(.semibold))
                    .monospacedDigit()
                Text(Self.description(for: snapshot.distanceBand))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Apparent width \(apparentWidth)°")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.top, Spacing.xxs)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Spacing.md)
        .frostedCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "Distance \(distanceLabel), \(Self.description(for: snapshot.distanceBand)). "
                + "Apparent width \(apparentWidth) degrees"
        )
    }

    /// The band, in words. A plain-language reading is the only way the number means anything to
    /// someone who does not already know the Moon's orbit is an ellipse.
    private static func description(for band: MoonDistanceBand) -> String {
        switch band {
        case .veryClose: "Unusually close, it will look large"
        case .closer: "Closer than average"
        case .average: "About average distance"
        case .further: "Further than average"
        case .veryFar: "Unusually far, it will look small"
        }
    }

    private var distanceLabel: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        let value = formatter.string(from: NSNumber(value: snapshot.distanceKm)) ?? ""
        return "\(value) km"
    }

    private var apparentWidth: String {
        String(format: "%.2f", snapshot.angularDiameterDegrees)
    }
}

/// The next four principal phases, each with the Moon drawn as it will look.
struct UpcomingPhasesCard: View {
    let phases: [PrincipalPhase]
    let timeZone: TimeZone

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(phases.enumerated()), id: \.element.id) { index, phase in
                if index > 0 {
                    Divider().padding(.leading, Spacing.xxl)
                }
                row(phase)
            }
        }
        .frostedCard()
    }

    private func row(_ phase: PrincipalPhase) -> some View {
        HStack(spacing: Spacing.md) {
            // Each row draws its own phase from that phase's own elongation, so the discs cannot
            // fall out of step with their labels.
            MoonDisc(
                elongationDegrees: phase.name.principalElongation ?? 0,
                diameter: discDiameter,
                showsDetail: false
            )
            .background(Circle().fill(Self.rowSky))

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(phase.name.label)
                    .font(.subheadline)
                Text(dateLabel(phase.date))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(relativeLabel(phase.date))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(Spacing.md)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(phase.name.label), \(dateLabel(phase.date)), \(relativeLabel(phase.date))")
    }

    private func dateLabel(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE d MMMM, HH:mm"
        formatter.timeZone = timeZone
        return formatter.string(from: date)
    }

    private func relativeLabel(_ date: Date) -> String {
        let days = Int((date.timeIntervalSince(.now) / 86_400).rounded())
        switch days {
        case ...0: return "today"
        case 1: return "tomorrow"
        default: return "in \(days) days"
        }
    }

    private let discDiameter: CGFloat = 34
    /// A scrap of night sky behind each small disc, for the same reason the hero has one: an unlit
    /// new moon on a light surface would otherwise be an invisible row.
    private static let rowSky = Color(red: 0.08, green: 0.09, blue: 0.17)
}
