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
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailGrid
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.ui.common.CurrentConditionsHeader
import com.nauhaan.skycast.ui.common.previewWeather
import com.nauhaan.skycast.ui.common.toDetails
import com.nauhaan.skycast.ui.common.toPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.nauhaan.skycast.core.designsystem.R as DesignSystemR

/** Full conditions for one saved location, pushed from Today or Locations. */
@Composable
fun LocationDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LocationDetailContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onNavigateBack = onNavigateBack,
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

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (uiState.showsStaleBanner) {
                    val message = uiState.error
                        ?.let { stringResource(it.toPresentation().messageRes) }
                        ?: stringResource(R.string.banner_data_may_be_out_of_date)
                    StaleDataBanner(message = message, onRetry = onRefresh)
                }

                // Identity first, and unconditionally. Which place this is comes from Room, so it
                // is known before any network call, hiding it behind a spinner would blank a
                // screen whose most important fact is already in hand. This is the same
                // offline-first rule the Today tab follows, applied to a pushed screen.
                uiState.location?.let { location ->
                    LocationIdentity(location = location, modifier = Modifier.padding(Spacing.md))
                }

                uiState.weather?.let { weather ->
                    // No onClick: this *is* the detail screen, so there is nowhere to push.
                    CurrentConditionsHeader(
                        weather = weather,
                        unit = uiState.preferences.temperatureUnit,
                        modifier = Modifier.padding(Spacing.md),
                    )

                    WeatherDetailGrid(
                        details = weather.toDetails(
                            preferences = uiState.preferences,
                            humidityLabel = stringResource(R.string.detail_humidity),
                            windLabel = stringResource(R.string.detail_wind),
                            pressureLabel = stringResource(R.string.detail_pressure),
                            visibilityLabel = stringResource(R.string.detail_visibility),
                            sunriseLabel = stringResource(R.string.detail_sunrise),
                            sunsetLabel = stringResource(R.string.detail_sunset),
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
                text = stringResource(R.string.today_loading),
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

/** When the reading was taken, the provenance that justifies a detail screen. */
@Composable
private fun ObservedAtFooter(observedAt: Instant, modifier: Modifier = Modifier) {
    val observed = stringResource(
        R.string.detail_observed_at,
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(observedAt),
    )

    Text(
        text = observed,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

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

@Preview(name = "Location detail, removed", showBackground = true)
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
