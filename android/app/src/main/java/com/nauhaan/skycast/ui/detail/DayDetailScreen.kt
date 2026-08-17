package com.nauhaan.skycast.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.WeatherConditionBadge
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.ForecastDay
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.ui.common.toPresentation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The 3-hourly breakdown for one forecast day, pushed from the Forecast tab. */
@Composable
fun DayDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DayDetailContent(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailContent(uiState: DayDetailUiState, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    // Same format as the forecast row that pushed this screen. A different one there and here
    // reads as two different days.
    val title = uiState.date?.format(DAY_TITLE_FORMAT) ?: stringResource(R.string.detail_day_title)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isMissing -> EmptyStateView(
                title = stringResource(R.string.day_detail_missing_title),
                message = stringResource(R.string.day_detail_missing_message),
                icon = Icons.Filled.EventBusy,
                modifier = Modifier.padding(innerPadding),
            )

            uiState.day == null && uiState.error != null -> {
                val presentation = uiState.error.toPresentation()
                ErrorView(
                    title = stringResource(presentation.titleRes),
                    message = stringResource(presentation.messageRes),
                    icon = Icons.Filled.CloudOff,
                    // No retry: the Forecast tab owns refreshing, and this screen has no
                    // location to refresh against until one resolves.
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.day == null -> LoadingView(modifier = Modifier.padding(innerPadding))

            else -> LazyColumn(modifier = Modifier.padding(innerPadding)) {
                item {
                    DaySummary(
                        locationName = uiState.locationName,
                        day = uiState.day,
                        unit = uiState.preferences.temperatureUnit,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.day_detail_hourly_heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            start = Spacing.md,
                            end = Spacing.md,
                            bottom = Spacing.sm,
                        ),
                    )
                }
                items(uiState.day.hourly, key = { it.time.epochSecond }) { hour ->
                    HourlyRow(
                        hour = hour,
                        zoneOffset = uiState.zoneOffset,
                        temperatureUnit = uiState.preferences.temperatureUnit,
                        windUnit = uiState.preferences.windSpeedUnit,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The day's headline: condition, description and high/low. */
@Composable
private fun DaySummary(locationName: String, day: ForecastDay, unit: TemperatureUnit, modifier: Modifier = Modifier) {
    val high = unit.convertFromCelsius(day.maxTemperatureCelsius).roundToInt()
    val low = unit.convertFromCelsius(day.minTemperatureCelsius).roundToInt()
    val highLow = stringResource(R.string.day_detail_high_low, high, low, unit.symbol)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$locationName. ${day.description}. $highLow"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = locationName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeatherConditionBadge(
            condition = day.condition,
            // A day summary covers a whole day, so daytime artwork is the honest choice.
            isDaytime = true,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        Text(
            text = day.description,
            // Emphasized type, on one element only: the screen's anchor.
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(text = highLow, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One 3-hourly reading. */
@Composable
private fun HourlyRow(
    hour: HourlyForecast,
    zoneOffset: ZoneOffset,
    temperatureUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    modifier: Modifier = Modifier,
) {
    // The forecast location's clock, not the device's, otherwise a Maldivian forecast read from
    // London lists its afternoon readings as morning ones.
    val localTime = hour.time.atOffset(zoneOffset)
    val time = TIME_FORMAT.format(localTime)
    val temperature = temperatureUnit.convertFromCelsius(hour.temperatureCelsius).roundToInt()
    val wind = windUnit.convertFromMetresPerSecond(hour.windSpeedMetresPerSecond)
    val windText = stringResource(
        R.string.day_detail_wind,
        (wind * ONE_DECIMAL_SCALE).roundToInt() / ONE_DECIMAL_SCALE,
        windUnit.symbol,
    )
    val rain = (hour.precipitationProbability * PERCENT).roundToInt()

    // The forecast carries no sunrise/sunset, so daylight is approximated by clock hour. Getting
    // this wrong shows a sun at 3 am, the exact parity bug WeatherConditionIconTest guards
    // against, so it is worth being explicit rather than passing isDaytime = true.
    val isDaytime = localTime.hour in DAWN_HOUR until DUSK_HOUR

    val announcement = buildString {
        append(stringResource(R.string.day_detail_hour_accessibility, time, temperature, temperatureUnit.symbol))
        append(", ")
        append(windText)
        if (rain > 0) {
            append(", ")
            append(stringResource(R.string.day_detail_rain_accessibility, rain))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = announcement }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            // A fixed width keeps the temperature column aligned down the list.
            modifier = Modifier.width(TimeColumnWidth),
        )
        WeatherConditionBadge(condition = hour.condition, isDaytime = isDaytime, size = HourBadgeSize)
        Text(
            text = "$temperature${temperatureUnit.symbol}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.md),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = windText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rain > 0) {
                Text(
                    text = "$rain%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM")
private const val PERCENT = 100
private const val ONE_DECIMAL_SCALE = 10.0
private const val DAWN_HOUR = 6
private const val DUSK_HOUR = 20
private val TimeColumnWidth = 56.dp
private val HourBadgeSize = 36.dp

/** Fixed date for the previews below: the longest day, so the hourly list is at its fullest. */
private val PreviewDate: LocalDate = LocalDate.parse("2026-06-21")

@Preview(name = "Day detail", showBackground = true)
@Composable
private fun DayDetailPreview() {
    SkyCastTheme {
        DayDetailContent(
            uiState = DayDetailUiState(
                locationName = "London",
                date = PreviewDate,
                day = previewDay(),
                isLoading = false,
            ),
            onNavigateBack = {},
        )
    }
}

private fun previewDay(): ForecastDay = ForecastDay(
    date = PreviewDate,
    condition = WeatherCondition.CLOUDS,
    description = "Scattered clouds",
    iconCode = "03d",
    minTemperatureCelsius = 14.0,
    maxTemperatureCelsius = 23.0,
    precipitationProbability = 0.2,
    hourly = listOf(
        HourlyForecast(
            time = Instant.parse("2026-06-21T03:00:00Z"),
            condition = WeatherCondition.CLEAR,
            iconCode = "01n",
            temperatureCelsius = 14.5,
            precipitationProbability = 0.0,
            windSpeedMetresPerSecond = 2.1,
        ),
        HourlyForecast(
            time = Instant.parse("2026-06-21T12:00:00Z"),
            condition = WeatherCondition.CLOUDS,
            iconCode = "03d",
            temperatureCelsius = 22.8,
            precipitationProbability = 0.2,
            windSpeedMetresPerSecond = 4.6,
        ),
    ),
)
