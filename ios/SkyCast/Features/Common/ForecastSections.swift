import SwiftUI

/// The forecast sections shared by Home and a place's detail screen.
///
/// Each draws nothing when its data cannot support it. A forecast can fail while the current
/// reading succeeds, since they are separate requests.
///
/// The Kotlin twin is `ui/common/ForecastSections.kt`.
struct TemperatureTrendSection: View {
    let forecast: Forecast
    let unit: TemperatureUnit

    var body: some View {
        let points = forecast.trendPoints(unit: unit)
        if points.count > 1 {
            SectionHeader("Temperature trend")
            TemperatureTrend(points: points, summary: forecast.trendSummary(unit: unit))
        }
    }
}

/// The five-day list, each row a button through to that day's hour-by-hour breakdown.
///
/// These rows are the only way into the day-detail screen from Home.
struct DailyRangesSection: View {
    let forecast: Forecast
    let unit: TemperatureUnit
    var onSelectDay: ((Date) -> Void)?

    var body: some View {
        let days = forecast.dayRanges(unit: unit)
        if !days.isEmpty {
            SectionHeader("Next days")
            DailyRangeList(days: days, onDaySelected: onSelectDay.map { action in
                { day in action(day.date) }
            })
        }
    }
}

/// A section title, styled once so the headings on either screen cannot drift apart.
struct SectionHeader: View {
    let title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// When the reading was taken.
///
/// Provenance for a screen full of numbers, and it belongs wherever those numbers are, which is now
/// Home as well as the detail screen.
struct ObservedAtFooter: View {
    let observedAt: Date

    var body: some View {
        Text("Observed \(observedAt.formatted(date: .abbreviated, time: .shortened))")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .center)
    }
}
