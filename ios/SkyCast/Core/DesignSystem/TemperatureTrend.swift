import Charts
import SwiftUI

/// One point on the temperature trend.
///
/// `value` is already in the user's unit and `valueLabel` already formatted, the design system
/// plots and prints what it is given, exactly as ``WeatherDetail`` does, so unit choices stay in
/// one place in the feature layer.
struct TrendPoint: Identifiable, Equatable {
    let time: Date
    let value: Double
    let valueLabel: String

    var id: Date {
        time
    }
}

/// Temperature over the forecast period, as a filled line.
///
/// ## Swift Charts, not a hand-drawn path
///
/// Charts ships with the OS, so using it costs no dependency, and it brings axis marks, sensible
/// tick placement, right-to-left support and Audio Graphs, VoiceOver can *play* the series as a
/// tone that rises and falls, none of which a `Canvas` path would have.
///
/// The Android counterpart is drawn by hand on a `Canvas`, because Compose has no first-party
/// chart. Same reasoning from opposite directions: use what the platform gives you rather than
/// taking a dependency for something already solved.
///
/// Only the extremes are annotated. Labelling forty three-hourly readings would produce a wall of
/// numbers that hides the shape, which is the one thing a chart is for.
struct TemperatureTrend: View {
    let points: [TrendPoint]
    /// Spoken instead of the chart's contents, since a shape cannot be read aloud.
    let summary: String

    private var extremes: [TrendPoint] {
        guard let warmest = points.max(by: { $0.value < $1.value }),
              let coldest = points.min(by: { $0.value < $1.value })
        else { return [] }
        return warmest.id == coldest.id ? [warmest] : [warmest, coldest]
    }

    var body: some View {
        Chart {
            ForEach(points) { point in
                AreaMark(
                    x: .value("Time", point.time),
                    y: .value("Temperature", point.value)
                )
                .foregroundStyle(
                    .linearGradient(
                        colors: [Color.skyAccent.opacity(fillTopOpacity), .clear],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .interpolationMethod(.catmullRom)

                LineMark(
                    x: .value("Time", point.time),
                    y: .value("Temperature", point.value)
                )
                .foregroundStyle(Color.skyAccent)
                .lineStyle(StrokeStyle(lineWidth: lineWidth))
                .interpolationMethod(.catmullRom)
            }

            ForEach(extremes) { point in
                PointMark(
                    x: .value("Time", point.time),
                    y: .value("Temperature", point.value)
                )
                .foregroundStyle(WeatherPalette.sunset)
                .annotation(position: .top, spacing: Spacing.xxs) {
                    Text(point.valueLabel)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(WeatherPalette.sunset)
                }
            }
        }
        // No y-axis: the extremes are annotated on the line itself.
        .chartYAxis(.hidden)
        .chartXAxis {
            AxisMarks(values: .stride(by: .day)) { _ in
                AxisGridLine()
                AxisValueLabel(format: .dateTime.weekday(.abbreviated))
            }
        }
        // Headroom, so an annotated extreme is not clipped by the top of the plot area.
        .chartYScale(domain: .automatic(includesZero: false))
        .frame(height: chartHeight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary)
    }

    /// Tall enough for the shape to read, short enough to leave room for the day rows below.
    private let chartHeight: CGFloat = 140
    private let lineWidth: CGFloat = 2.5
    private let fillTopOpacity: Double = 0.28
}

#Preview {
    let start = Date()
    let temperatures = [18.0, 17, 19, 24, 27, 25, 21, 19, 18, 20, 26, 28]
    return TemperatureTrend(
        points: temperatures.enumerated().map { index, value in
            TrendPoint(
                time: start.addingTimeInterval(Double(index) * 3 * 3_600),
                value: value,
                valueLabel: "\(Int(value))°"
            )
        },
        summary: "Temperature from 17 to 28 degrees over five days"
    )
    .padding()
}
