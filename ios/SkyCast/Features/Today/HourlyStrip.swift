import SwiftUI

/// A horizontally scrollable strip of three-hourly readings around the present moment.
///
/// A `ScrollView` supplies momentum, rubber-banding and VoiceOver's scroll actions. Past hours are
/// included, and the present is marked.
///
/// Times are rendered in the **forecast location's** zone, not the device's: a Maldivian forecast
/// read from London must still show Maldivian hours.
struct HourlyStrip: View {
    let hours: [HourlyForecast]
    let timeZone: TimeZone
    let unit: TemperatureUnit
    var now: Date = .now
    /// Overridable because the detail screen shows only what is ahead, where "Through the day"
    /// does not apply.
    var title = "Through the day"

    var body: some View {
        if hours.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, Spacing.md)

                ScrollView(.horizontal) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(hours) { hour in
                            HourColumn(hour: hour, timeZone: timeZone, unit: unit, now: now)
                        }
                    }
                    .padding(.horizontal, Spacing.md)
                    .scrollTargetLayout()
                }
                .scrollIndicators(.hidden)
            }
        }
    }
}

private struct HourColumn: View {
    let hour: HourlyForecast
    let timeZone: TimeZone
    let unit: TemperatureUnit
    let now: Date

    private var isPast: Bool {
        hour.time < now
    }

    /// Whether this reading covers the present moment. Readings are three hours apart, so "now" is
    /// the most recent one that has already started.
    private var isCurrent: Bool {
        hour.time <= now && now < hour.time.addingTimeInterval(Self.readingInterval)
    }

    private var label: String {
        if isCurrent {
            return "Now"
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = timeZone
        return formatter.string(from: hour.time)
    }

    private var temperature: Int {
        Int(unit.convertFromCelsius(hour.temperatureCelsius).rounded())
    }

    private var rainPercent: Int {
        Int((hour.precipitationProbability * 100).rounded())
    }

    /// The forecast carries no sunrise or sunset, so daylight is approximated by clock hour.
    /// Getting this wrong shows a sun at 3 am, the parity bug `WeatherConditionIconTests` guards.
    private var isDaytime: Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let hourOfDay = calendar.component(.hour, from: hour.time)
        return hourOfDay >= Self.dawnHour && hourOfDay < Self.duskHour
    }

    private var announcement: String {
        var text = "\(label), \(temperature)\(unit.symbol)"
        if rainPercent > 0 {
            text += ", \(rainPercent) percent chance of rain"
        }
        return text
    }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text(label)
                .font(.caption)
                .fontWeight(isCurrent ? .bold : .regular)
                // Past readings recede rather than disappear: still legible, clearly behind us.
                .foregroundStyle(isPast ? AnyShapeStyle(.secondary) : AnyShapeStyle(.primary))

            ConditionBadge(condition: hour.condition, isDaytime: isDaytime, size: badgeSize)

            Text("\(temperature)\(unit.symbol)")
                .font(.subheadline.weight(.semibold))

            // Only when there is something to say. An always-present "0%" is noise on nine days
            // out of ten.
            if rainPercent > 0 {
                Text("\(rainPercent)%")
                    .font(.caption2)
                    .foregroundStyle(WeatherPalette.humidity)
            }
        }
        .frame(width: columnWidth)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    private let columnWidth: CGFloat = 60
    private let badgeSize: CGFloat = 20
    private static let readingInterval: TimeInterval = 3 * 60 * 60
    private static let dawnHour = 6
    private static let duskHour = 20
}
