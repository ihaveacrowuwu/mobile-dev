package com.nauhaan.skycast.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.DayRange
import com.nauhaan.skycast.core.designsystem.component.TrendPoint
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns a [Forecast] into the shapes the chart and the day rows draw.
 *
 * Composable because every value here is a display decision that needs resources: the weekday
 * name, the unit symbol, the spoken description of a row. Keeping it out of the design system
 * leaves those components taking plain numbers and strings, so they can be previewed and reasoned
 * about without a `Forecast` anywhere near them.
 */
@Composable
fun Forecast.toTrendPoints(unit: TemperatureUnit): List<TrendPoint> {
    var lastDate: LocalDate? = null
    return days.flatMap { it.hourly }.map { hour ->
        val date = hour.time.atZone(zoneOffset).toLocalDate()
        val isNewDay = date != lastDate
        lastDate = date
        val value = unit.convertFromCelsius(hour.temperatureCelsius)
        TrendPoint(
            value = value,
            valueLabel = "${value.roundToInt()}°",
            // Only the first reading of each day is labelled; forty labels would hide the shape.
            dayLabel = if (isNewDay) date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else null,
        )
    }
}

/** A spoken summary of the chart, since its shape is the information and a picture cannot say it. */
@Composable
fun Forecast.trendDescription(unit: TemperatureUnit): String {
    val temperatures = days.flatMap { it.hourly }.map { unit.convertFromCelsius(it.temperatureCelsius) }
    if (temperatures.isEmpty()) return ""
    return pluralStringResource(
        R.plurals.detail_trend_description,
        days.size,
        "${temperatures.min().roundToInt()}${unit.symbol}",
        "${temperatures.max().roundToInt()}${unit.symbol}",
        days.size,
    )
}

/**
 * The forecast's days as comparable range bars.
 *
 * Fractions are positions within the **period's** range rather than each day's own, which is what
 * lets the column be read down: a cold day is a bar sitting to the left, not two numbers to
 * compare against the row above.
 */
@Composable
fun Forecast.toDayRanges(unit: TemperatureUnit): List<DayRange> {
    if (days.isEmpty()) return emptyList()

    val lows = days.map { it.minTemperatureCelsius }
    val highs = days.map { it.maxTemperatureCelsius }
    val floor = lows.min()
    val ceiling = highs.max()
    // A period with one flat temperature would otherwise divide by zero.
    val span = (ceiling - floor).takeIf { it > 0.0 } ?: 1.0

    val todayLabel = stringResource(R.string.detail_today)
    val today = LocalDate.now(zoneOffset)
    val dayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())

    return days.map { day ->
        val label = if (day.date == today) todayLabel else dayFormat.format(day.date)
        val low = unit.convertFromCelsius(day.minTemperatureCelsius)
        val high = unit.convertFromCelsius(day.maxTemperatureCelsius)
        val lowLabel = "${low.roundToInt()}°"
        val highLabel = "${high.roundToInt()}°"
        val rainPercent = (day.precipitationProbability * PERCENT).roundToInt()

        DayRange(
            dayLabel = label,
            condition = day.condition,
            // A row summarises a whole day, so daytime artwork is the honest choice.
            isDaytime = true,
            lowLabel = lowLabel,
            highLabel = highLabel,
            lowFraction = ((day.minTemperatureCelsius - floor) / span).toFloat(),
            highFraction = ((day.maxTemperatureCelsius - floor) / span).toFloat(),
            precipitationLabel = "$rainPercent%".takeIf { rainPercent > 0 },
            contentDescription = if (rainPercent > 0) {
                pluralStringResource(
                    R.plurals.detail_day_description_rain,
                    rainPercent,
                    label,
                    day.description,
                    "${low.roundToInt()}${unit.symbol}",
                    "${high.roundToInt()}${unit.symbol}",
                    rainPercent,
                )
            } else {
                stringResource(
                    R.string.detail_day_description,
                    label,
                    day.description,
                    "${low.roundToInt()}${unit.symbol}",
                    "${high.roundToInt()}${unit.symbol}",
                )
            },
        )
    }
}

private const val PERCENT = 100.0
