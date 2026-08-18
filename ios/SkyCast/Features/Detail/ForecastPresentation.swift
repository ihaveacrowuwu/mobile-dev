import Foundation

/// Turns a ``Forecast`` into the shapes the chart and the day rows draw.
///
/// Lives in `Features`, not the design system: every value here is a display decision, the
/// weekday name, the unit symbol, the spoken description of a row. Keeping it out leaves those
/// components taking plain numbers and strings, so they can be previewed and reasoned about
/// without a `Forecast` anywhere near them.
///
/// The Android counterpart is `ui/detail/ForecastPresentation.kt`.
extension Forecast {
    /// Every three-hourly reading, converted to the user's unit.
    func trendPoints(unit: TemperatureUnit) -> [TrendPoint] {
        days.flatMap(\.hourly).map { hour in
            let value = unit.convertFromCelsius(hour.temperatureCelsius)
            return TrendPoint(
                time: hour.time,
                value: value,
                valueLabel: "\(Int(value.rounded()))°"
            )
        }
    }

    /// A spoken summary of the chart, since its shape is the information and a picture cannot say
    /// it aloud.
    func trendSummary(unit: TemperatureUnit) -> String {
        let temperatures = days.flatMap(\.hourly).map { unit.convertFromCelsius($0.temperatureCelsius) }
        guard let coldest = temperatures.min(), let warmest = temperatures.max() else { return "" }
        return "Temperature from \(Int(coldest.rounded()))\(unit.symbol) "
            + "to \(Int(warmest.rounded()))\(unit.symbol) over the next \(days.count) days"
    }

    /// The forecast's days as comparable range bars.
    ///
    /// Fractions are positions within the **period's** range rather than each day's own, which is
    /// what lets the column be read down: a cold day is a bar sitting to the left, not two numbers
    /// to compare against the row above.
    func dayRanges(unit: TemperatureUnit, now: Date = .now) -> [DayRange] {
        guard !days.isEmpty else { return [] }

        let floor = days.map(\.minTemperatureCelsius).min() ?? 0
        let ceiling = days.map(\.maxTemperatureCelsius).max() ?? 0
        // A period at one flat temperature would otherwise divide by zero.
        let span = ceiling - floor > 0 ? ceiling - floor : 1

        var calendar = Calendar.current
        calendar.timeZone = timeZone

        return days.map { day in
            let isToday = calendar.isDate(day.date, inSameDayAs: now)
            let label = isToday ? "Today" : day.date.formatted(
                Date.FormatStyle(timeZone: timeZone).weekday(.wide)
            )
            let low = unit.convertFromCelsius(day.minTemperatureCelsius)
            let high = unit.convertFromCelsius(day.maxTemperatureCelsius)
            let rainPercent = Int((day.precipitationProbability * 100).rounded())

            var announcement = "\(label), \(day.description), "
                + "low \(Int(low.rounded()))\(unit.symbol), high \(Int(high.rounded()))\(unit.symbol)"
            if rainPercent > 0 {
                announcement += ", \(rainPercent) percent chance of rain"
            }

            return DayRange(
                date: day.date,
                dayLabel: label,
                condition: day.condition,
                lowLabel: "\(Int(low.rounded()))°",
                highLabel: "\(Int(high.rounded()))°",
                lowFraction: (day.minTemperatureCelsius - floor) / span,
                highFraction: (day.maxTemperatureCelsius - floor) / span,
                precipitationLabel: rainPercent > 0 ? "\(rainPercent)%" : nil,
                announcement: announcement
            )
        }
    }
}
