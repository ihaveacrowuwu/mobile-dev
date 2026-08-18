package com.nauhaan.skycast.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.core.designsystem.component.BackgroundIntensity
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailGrid
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.ui.common.CurrentConditionsHeader
import com.nauhaan.skycast.ui.common.DailyRangesSection
import com.nauhaan.skycast.ui.common.ObservedAtFooter
import com.nauhaan.skycast.ui.common.SectionHeader
import com.nauhaan.skycast.ui.common.SunPathSection
import com.nauhaan.skycast.ui.common.TemperatureTrendSection
import com.nauhaan.skycast.ui.common.previewWeather
import com.nauhaan.skycast.ui.common.rememberWeatherDetailLabels
import com.nauhaan.skycast.ui.common.toDetails
import com.nauhaan.skycast.ui.common.toPresentation
import com.nauhaan.skycast.ui.home.HourlyStrip
import com.nauhaan.skycast.core.designsystem.R as DesignSystemR

/** Full conditions for one saved location, pushed from Home or Locations. */
@Composable
fun LocationDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationDetailViewModel = hiltViewModel(),
    onNavigateToDayDetail: (Long, Long) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LocationDetailContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onNavigateBack = onNavigateBack,
        onDaySelected = { epochDay ->
            uiState.location?.let { onNavigateToDayDetail(it.id, epochDay) }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationDetailContent(
    uiState: LocationDetailUiState,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDaySelected: (Long) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.location?.name ?: stringResource(R.string.detail_location_title))
                },
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
        if (uiState.isMissing) {
            EmptyStateView(
                title = stringResource(R.string.detail_missing_title),
                message = stringResource(R.string.detail_missing_message),
                icon = Icons.Filled.WrongLocation,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        WeatherBackground(
            condition = uiState.weather?.condition ?: WeatherCondition.UNKNOWN,
            isDaytime = uiState.weather?.isDaytime ?: true,
            intensity = BackgroundIntensity.SUBTLE,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (uiState.showsStaleBanner) {
                        val message = uiState.error
                            ?.let { stringResource(it.toPresentation().messageRes) }
                            ?: stringResource(R.string.banner_data_may_be_out_of_date)
                        StaleDataBanner(message = message, onRetry = onRefresh)
                    }

                    // Identity first, and unconditionally. Which place this is comes from Room, so it
                    // is known before any network call, and hiding it behind a spinner would blank a
                    // screen whose most important fact is already in hand. This is the same
                    // offline-first rule the Home tab follows, applied to a pushed screen.
                    uiState.location?.let { location ->
                        LocationIdentity(location = location, modifier = Modifier.padding(Spacing.md))
                    }

                    uiState.weather?.let { weather ->
                        // No onClick: this *is* the detail screen, so there is nowhere to push.
                        CurrentConditionsHeader(
                            weather = weather,
                            unit = uiState.preferences.temperatureUnit,
                            // The identity block above already names the place.
                            showsLocationName = false,
                            modifier = Modifier.padding(Spacing.md),
                        )

                        ForecastSections(uiState = uiState, onDaySelected = onDaySelected)

                        SectionHeader(
                            title = stringResource(R.string.detail_section_conditions),
                            modifier = Modifier.padding(
                                start = Spacing.md,
                                end = Spacing.md,
                                top = Spacing.md,
                                bottom = Spacing.sm,
                            ),
                        )

                        SunPathSection(
                            weather = weather,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        )

                        WeatherDetailGrid(
                            // Eight tiles rather than Home's six: dew point and length of day are
                            // derived readings, and this is the screen someone opens because the
                            // glance was not enough.
                            details = weather.toDetails(
                                preferences = uiState.preferences,
                                labels = rememberWeatherDetailLabels(),
                                includeDerived = true,
                            ),
                            modifier = Modifier.padding(horizontal = Spacing.md),
                        )

                        ObservedAtFooter(
                            observedAt = weather.observedAt,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }

                    // Inline, not full-screen: the identity block above is real content, so a
                    // full-screen loader or error over the top of it would be a lie.
                    if (uiState.weather == null) {
                        WeatherStatusNotice(
                            error = uiState.error,
                            onRetry = onRefresh,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The hour-by-hour and day-by-day picture, when the forecast has arrived.
 *
 * Silent when it has not, since the forecast and the current reading are separate requests.
 */
@Composable
private fun ForecastSections(
    uiState: LocationDetailUiState,
    modifier: Modifier = Modifier,
    onDaySelected: (Long) -> Unit = {},
) {
    val forecast = uiState.forecast ?: return
    val unit = uiState.preferences.temperatureUnit
    val upcoming = uiState.upcomingHours().take(HOURS_ON_STRIP)

    Column(modifier = modifier) {
        if (upcoming.isNotEmpty()) {
            HourlyStrip(
                hours = upcoming,
                zoneOffset = forecast.zoneOffset,
                unit = unit,
                title = stringResource(R.string.detail_section_hourly),
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }

        // Shared with Home, which now shows the same sections. See ui/common/ForecastSections.kt.
        TemperatureTrendSection(forecast = forecast, unit = unit)
        DailyRangesSection(forecast = forecast, unit = unit, onDaySelected = onDaySelected)
    }
}

/** Place name, full display name and coordinates, all known from the local database. */
@Composable
private fun LocationIdentity(location: SavedLocation, modifier: Modifier = Modifier) {
    val coordinates = stringResource(
        R.string.detail_coordinates,
        location.latitude,
        location.longitude,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One announcement: two lines identifying a single place.
            .clearAndSetSemantics {
                contentDescription = "${location.displayName}. $coordinates"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = location.displayName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = coordinates,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The weather half's loading or error state, rendered **inline** beneath the identity block.
 *
 * @param error `null` means a fetch is simply still in flight.
 */
@Composable
private fun WeatherStatusNotice(error: AppError?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (error == null) {
            LoadingIndicator()
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val presentation = error.toPresentation()
        Text(
            text = stringResource(presentation.titleRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(presentation.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (presentation.isRetryable) {
            // DesignSystemR: `action_retry` belongs to :core:designsystem, which owns the
            // state views that normally render it, which avoids duplicating it in :app.
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(DesignSystemR.string.action_retry))
            }
        }
    }
}

/** Enough to fill the strip without turning the top of the screen into a second forecast tab. */
private const val HOURS_ON_STRIP = 8

@Preview(name = "Location detail", showBackground = true)
@Composable
private fun LocationDetailPreview() {
    SkyCastTheme {
        LocationDetailContent(
            uiState = LocationDetailUiState(
                location = SavedLocation(
                    id = 1,
                    name = "London",
                    countryCode = "GB",
                    state = "England",
                    latitude = 51.5074,
                    longitude = -0.1278,
                    isPrimary = true,
                ),
                weather = previewWeather(),
                isLoading = false,
            ),
            onRefresh = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Location detail: removed", showBackground = true)
@Composable
private fun LocationDetailMissingPreview() {
    SkyCastTheme {
        LocationDetailContent(
            uiState = LocationDetailUiState(isLoading = false, isMissing = true),
            onRefresh = {},
            onNavigateBack = {},
        )
    }
}
