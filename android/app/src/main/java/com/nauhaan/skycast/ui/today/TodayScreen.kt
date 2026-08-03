package com.nauhaan.skycast.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.ui.common.toPresentation
import kotlin.math.roundToInt

/**
 * The Today tab.
 *
 * Two composables, deliberately:
 *
 * - [TodayScreen] is the **stateful** entry point. It obtains the view model and does
 *   nothing else.
 * - [TodayContent] is **stateless**, state in, lambdas out. That is what makes it
 *   previewable in every state and assertable in a Compose test without Hilt.
 *
 * Follow this split for every screen.
 */
@Composable
fun TodayScreen(
    onNavigateToLocationDetail: (Long) -> Unit,
    onNavigateToAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle, not collectAsState: stops collection when the
    // screen is not visible instead of doing work in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onDismissBanner = viewModel::dismissBanner,
        onOpenDetail = onNavigateToLocationDetail,
        onAddLocation = onNavigateToAddLocation,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayContent(
    uiState: TodayUiState,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Exactly one branch renders. The ordering encodes the offline-first rules from
    // The offline-first read algorithm: content wins over errors whenever a cache exists.
    when {
        uiState.showsFullScreenLoader ->
            LoadingView(
                modifier = modifier,
                message = stringResource(R.string.today_loading),
            )

        uiState.showsEmptyState ->
            EmptyStateView(
                title = stringResource(R.string.today_empty_title),
                message = stringResource(R.string.today_empty_message),
                icon = Icons.Filled.AddLocationAlt,
                actionLabel = stringResource(R.string.action_add_location),
                onAction = onAddLocation,
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

        else ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (uiState.showsStaleBanner) {
                        val message =
                            uiState.error
                                ?.let { stringResource(it.toPresentation().messageRes) }
                                ?: stringResource(R.string.banner_data_may_be_out_of_date)
                        StaleDataBanner(
                            message = message,
                            onRetry = onRefresh,
                            onDismiss = onDismissBanner,
                        )
                    }

                    uiState.weather?.let { weather ->
                        CurrentConditionsHeader(
                            locationName = weather.locationName,
                            description = weather.description,
                            temperature = weather.temperatureCelsius,
                            feelsLike = weather.feelsLikeCelsius,
                            unit = uiState.preferences.temperatureUnit,
                            // Tapping the hero block pushes the full detail screen, the
                            // push half of the navigation hierarchy, reachable from Today.
                            onClick = { onOpenDetail(weather.locationId) },
                            modifier = Modifier.padding(Spacing.md),
                        )

                        // TODO(nauhaan): replace with the real detail grid, humidity,
                        //  wind, pressure, visibility, sunrise/sunset. Tracked for the
                        //  Functionality criterion; the plumbing above is already final.
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md),
                        ) {
                            Text(
                                text = stringResource(R.string.today_details_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                    }
                }
            }
    }
}

/**
 * The hero reading: place, condition, and one very large temperature.
 *
 * `clearAndSetSemantics` merges the whole block into a single TalkBack announcement.
 * Without it a screen-reader user hears "London" … "22" … "degrees" … "feels like" as
 * four disconnected fragments. `onClickLabel` then describes the tap action, so the
 * merge does not hide the fact that the block is interactive.
 */
@Composable
private fun CurrentConditionsHeader(
    locationName: String,
    description: String,
    temperature: Double,
    feelsLike: Double,
    unit: TemperatureUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayed = unit.convertFromCelsius(temperature).roundToInt()
    val displayedFeelsLike = unit.convertFromCelsius(feelsLike).roundToInt()
    val announcement =
        stringResource(
            R.string.today_conditions_accessibility,
            locationName,
            displayed,
            unit.symbol,
            description,
            displayedFeelsLike,
        )
    val openDetailLabel = stringResource(R.string.today_open_detail_action)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openDetailLabel, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = announcement
                onClick(label = openDetailLabel) {
                    onClick()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = locationName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.Top) {
            Text(text = "$displayed", style = MaterialTheme.typography.displayLarge)
            Text(
                text = unit.symbol,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.today_feels_like, displayedFeelsLike, unit.symbol),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Previews: one per state, so every branch is reviewable without a device ──

@Preview(name = "Loading", showBackground = true)
@Composable
private fun TodayLoadingPreview() {
    SkyCastTheme {
        TodayContent(
            uiState = TodayUiState(isLoading = true),
            onRefresh = {},
            onDismissBanner = {},
            onOpenDetail = {},
            onAddLocation = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun TodayEmptyPreview() {
    SkyCastTheme {
        TodayContent(
            uiState = TodayUiState(hasNoLocation = true),
            onRefresh = {},
            onDismissBanner = {},
            onOpenDetail = {},
            onAddLocation = {},
        )
    }
}

@Preview(name = "Offline, no cache", showBackground = true)
@Composable
private fun TodayOfflinePreview() {
    SkyCastTheme {
        TodayContent(
            uiState = TodayUiState(error = com.nauhaan.skycast.core.common.AppError.Offline),
            onRefresh = {},
            onDismissBanner = {},
            onOpenDetail = {},
            onAddLocation = {},
        )
    }
}
