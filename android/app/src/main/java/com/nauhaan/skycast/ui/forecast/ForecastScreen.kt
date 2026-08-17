package com.nauhaan.skycast.ui.forecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherConditionBadge
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.ForecastDay
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.ui.common.toPresentation
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The Forecast tab, five days for the primary location, tappable through to a day detail. */
@Composable
fun ForecastScreen(
    onNavigateToDayDetail: (locationId: Long, epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForecastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ForecastContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onOpenDay = onNavigateToDayDetail,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForecastContent(
    uiState: ForecastUiState,
    onRefresh: () -> Unit,
    onOpenDay: (locationId: Long, epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.showsFullScreenLoader -> LoadingView(modifier = modifier)

        uiState.showsEmptyState -> EmptyStateView(
            title = stringResource(R.string.today_empty_title),
            message = stringResource(R.string.today_empty_message),
            icon = Icons.Filled.AddLocationAlt,
            modifier = modifier,
        )

        uiState.showsFullScreenError -> {
            val presentation = requireNotNull(uiState.error).toPresentation()
            ErrorView(
                title = stringResource(presentation.titleRes),
                message = stringResource(presentation.messageRes),
                icon = Icons.Filled.CloudOff,
                onRetry = onRefresh.takeIf { presentation.isRetryable },
                modifier = modifier,
            )
        }

        else -> PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn {
                if (uiState.showsStaleBanner) {
                    item {
                        val message = uiState.error
                            ?.let { stringResource(it.toPresentation().messageRes) }
                            ?: stringResource(R.string.banner_data_may_be_out_of_date)
                        StaleDataBanner(message = message, onRetry = onRefresh)
                    }
                }
                uiState.forecast?.let { forecast ->
                    item {
                        Text(
                            text = forecast.locationName,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                    items(forecast.days, key = { it.date.toEpochDay() }) { day ->
                        ForecastDayRow(
                            day = day,
                            unit = uiState.preferences.temperatureUnit,
                            onClick = { onOpenDay(forecast.locationId, day.date.toEpochDay()) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * One day of the forecast.
 *
 * `clearAndSetSemantics` merges the row into a single announcement, otherwise TalkBack reads
 * the date, two bare numbers and a percentage as four disconnected fragments.
 */
@Composable
private fun ForecastDayRow(
    day: ForecastDay,
    unit: TemperatureUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val high = unit.convertFromCelsius(day.maxTemperatureCelsius).roundToInt()
    val low = unit.convertFromCelsius(day.minTemperatureCelsius).roundToInt()
    val rain = (day.precipitationProbability * PERCENT).roundToInt()
    // Weekday first, and no year: the question a forecast row answers is "what about Thursday?",
    // so the weekday is the useful part and "2026" is noise. Matches the iOS row.
    val dayLabel = day.date.format(DayLabelFormat)
    val announcement = stringResource(
        R.string.forecast_day_accessibility,
        dayLabel,
        day.description,
        high,
        unit.symbol,
        low,
        rain,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = announcement }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherConditionBadge(
            condition = day.condition,
            // Forecast rows summarise a whole day, so daytime artwork is the honest choice.
            isDaytime = true,
            size = BadgeSize,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md),
        ) {
            Text(text = dayLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                text = day.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rain > 0) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$rain%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = Spacing.sm),
            )
        }
        Text(
            text = stringResource(R.string.forecast_high_low, high, low),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
        )
    }
}

private val DayLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM")

private const val PERCENT = 100
private val BadgeSize = 44.dp
