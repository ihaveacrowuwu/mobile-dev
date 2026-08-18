import SwiftUI

/// One day of the forecast as a temperature *range*.
///
/// Fractions are positions within the **whole period's** range, not within this day, that is what
/// makes the bars comparable down the column, so a cold snap on Thursday is visible as a bar
/// sitting further left rather than as two numbers the reader has to hold in their head.
struct DayRange: Identifiable, Equatable {
    let date: Date
    let dayLabel: String
    let condition: WeatherCondition
    let lowLabel: String
    let highLabel: String
    let lowFraction: Double
    let highFraction: Double
    /// Already formatted, or `nil` when rain is not worth mentioning.
    var precipitationLabel: String?
    /// The whole row, spoken as one sentence.
    let announcement: String

    var id: Date {
        date
    }
}

/// The forecast's days, each as a labelled range bar.
struct DailyRangeList: View {
    let days: [DayRange]

    var body: some View {
        VStack(spacing: Spacing.sm) {
            ForEach(days) { day in
                DailyRangeRow(day: day)
            }
        }
    }
}

private struct DailyRangeRow: View {
    let day: DayRange

    var body: some View {
        HStack(spacing: Spacing.sm) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(day.dayLabel)
                    .font(.subheadline)
                if let precipitationLabel = day.precipitationLabel {
                    Text(precipitationLabel)
                        .font(.caption2)
                        .foregroundStyle(WeatherPalette.humidity)
                }
            }
            .frame(width: dayColumnWidth, alignment: .leading)

            ConditionBadge(
                condition: day.condition,
                // A row summarises a whole day, so daytime artwork is the honest choice.
                isDaytime: true,
                size: badgeSize
            )

            Text(day.lowLabel)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: temperatureColumnWidth, alignment: .trailing)

            RangeBar(lowFraction: day.lowFraction, highFraction: day.highFraction)

            Text(day.highLabel)
                .font(.subheadline)
                .frame(width: temperatureColumnWidth, alignment: .leading)
        }
        // One announcement per row; otherwise VoiceOver reads a weekday and three bare numbers as
        // four disconnected fragments.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(day.announcement)
    }

    private let dayColumnWidth: CGFloat = 88
    private let temperatureColumnWidth: CGFloat = 40
    private let badgeSize: CGFloat = 26
}

/// The segment of the period's range that this day covers.
private struct RangeBar: View {
    let lowFraction: Double
    let highFraction: Double

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.primary.opacity(trackOpacity))

                Capsule()
                    // Cool at the day's low end, warm at its high end, the same reading the two
                    // numbers either side give, in a form the eye takes in at a glance.
                    .fill(
                        .linearGradient(
                            colors: [WeatherPalette.humidity, WeatherPalette.sunset],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    // A day whose high equals its low still has to be visible.
                    .frame(width: max(minimumWidth, proxy.size.width * (highFraction - lowFraction)))
                    .offset(x: proxy.size.width * lowFraction)
            }
        }
        .frame(height: barHeight)
        .accessibilityHidden(true)
    }

    private let barHeight: CGFloat = 6
    private let trackOpacity: Double = 0.12
    private let minimumWidth: CGFloat = 6
}

#Preview {
    DailyRangeList(days: [
        DayRange(
            date: .now,
            dayLabel: "Today",
            condition: .clear,
            lowLabel: "19°",
            highLabel: "28°",
            lowFraction: 0.2,
            highFraction: 0.9,
            precipitationLabel: "20%",
            announcement: "Today, clear sky, low 19 degrees, high 28 degrees"
        ),
        DayRange(
            date: .now.addingTimeInterval(86_400),
            dayLabel: "Wednesday",
            condition: .rain,
            lowLabel: "17°",
            highLabel: "24°",
            lowFraction: 0,
            highFraction: 0.6,
            precipitationLabel: "100%",
            announcement: "Wednesday, rain, low 17 degrees, high 24 degrees"
        ),
    ])
    .padding()
}
