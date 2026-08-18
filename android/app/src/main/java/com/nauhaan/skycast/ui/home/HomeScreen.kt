package com.nauhaan.skycast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.GoldenHourCard
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.PageScrubber
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailGrid
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherSurfaceTint
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.usecase.TodayLocationWeather
import com.nauhaan.skycast.ui.common.CurrentConditionsHeader
import com.nauhaan.skycast.ui.common.DailyRangesSection
import com.nauhaan.skycast.ui.common.ObservedAtFooter
import com.nauhaan.skycast.ui.common.SectionHeader
import com.nauhaan.skycast.ui.common.SunPathSection
import com.nauhaan.skycast.ui.common.TemperatureTrendSection
import com.nauhaan.skycast.ui.common.goldenHourReading
import com.nauhaan.skycast.ui.common.rememberWeatherDetailLabels
import com.nauhaan.skycast.ui.common.toDetails
import com.nauhaan.skycast.ui.common.toPresentation

/**
 * The Home tab.
 *
 * - [HomeScreen] is the **stateful** entry point. It obtains the view model and does nothing else.
 * - [HomeContent] is **stateless**: state in, lambdas out, so it is previewable in every state and
 *   assertable in a Compose test without Hilt.
 */
@Composable
fun HomeScreen(
    onNavigateToDayDetail: (Long, Long) -> Unit,
    onNavigateToAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onWeatherTintChanged: (Color?) -> Unit = {},
) {
    // collectAsStateWithLifecycle, not collectAsState: stops collection when the
    // screen is not visible instead of doing work in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Reported upwards so the navigation bar can share this page's mood. The bar is a sibling of
    // the content rather than a descendant, so the CompositionLocal the tiles read cannot reach
    // it, state goes up by lambda and the colour comes back down as a parameter.
    val weather = uiState.weather
    val tint = weather?.let { weatherSurfaceTint(it.condition, it.isDaytime) }
    LaunchedEffect(tint) { onWeatherTintChanged(tint) }

    HomeContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onDismissBanner = viewModel::dismissBanner,
        onSelectPage = viewModel::selectPage,
        onOpenDayDetail = onNavigateToDayDetail,
        onAddLocation = onNavigateToAddLocation,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenDayDetail: (Long, Long) -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Exactly one branch renders. The ordering encodes the offline-first rules from
    // The offline-first read algorithm: content wins over errors whenever a cache exists.
    when {
        uiState.showsFullScreenLoader ->
            LoadingView(modifier = modifier, message = stringResource(R.string.home_loading))

        uiState.showsEmptyState ->
            EmptyStateView(
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
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

        else -> HomePager(
            uiState = uiState,
            onRefresh = onRefresh,
            onDismissBanner = onDismissBanner,
            onSelectPage = onSelectPage,
            onOpenDayDetail = onOpenDayDetail,
            modifier = modifier,
        )
    }
}

/**
 * The saved places, one per page.
 *
 * A pager rather than a switcher alone: swiping between places is how weather apps on both
 * platforms work, and the gesture feels immediate in a way a menu does not. The menu above it
 * stays, because a gesture with no visible affordance is undiscoverable in the other direction,
 * someone who never swipes would never learn there is more than one page.
 *
 * Every page's data is already in memory (see `ObserveTodayWeatherUseCase`), so a swipe reveals a
 * loaded screen rather than a spinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePager(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenDayDetail: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedIndex,
        pageCount = { uiState.pages.size },
    )

    // Two-way binding between the pager and the view model: a swipe reports the new page, and
    // choosing from the menu animates the pager to it. `snapshotFlow` on `settledPage` rather than
    // a callback, so the report happens once the page has come to rest instead of on every frame
    // of the drag.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onSelectPage)
    }
    LaunchedEffect(uiState.selectedIndex) {
        if (uiState.selectedIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(uiState.selectedIndex)
        }
    }

    // The background follows the place on screen, so swiping from a clear Malé to an overcast
    // London visibly changes the weather of the whole screen, not just the numbers on it.
    val selected = uiState.selected?.weather?.data

    WeatherBackground(
        condition = selected?.condition ?: WeatherCondition.UNKNOWN,
        isDaytime = selected?.isDaytime ?: true,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned above the pages, so the place name stays legible while a page scrolls under it
            // and the dots keep a fixed position.
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    uiState.location?.let { location ->
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                        )
                    }
                    if (uiState.showsPageIndicator) {
                        PageScrubber(
                            count = uiState.pages.size,
                            selectedIndex = uiState.selectedIndex,
                            onSelect = onSelectPage,
                            contentDescription = stringResource(
                                R.string.home_showing_location,
                                uiState.location?.name.orEmpty(),
                                uiState.selectedIndex + 1,
                                uiState.pages.size,
                            ),
                        )
                    }
                }

                LocationMenu(
                    uiState = uiState,
                    onSelectPage = onSelectPage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(state = pagerState) { page ->
                    HomePage(
                        page = uiState.pages[page],
                        uiState = uiState,
                        isSelected = page == uiState.selectedIndex,
                        onRefresh = onRefresh,
                        onDismissBanner = onDismissBanner,
                        onOpenDayDetail = onOpenDayDetail,
                    )
                }
            }
        }
    }
}

/** One page: the hero reading, the hourly strip and the detail tiles for a single place. */
@Composable
private fun HomePage(
    page: TodayLocationWeather,
    uiState: HomeUiState,
    isSelected: Boolean,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenDayDetail: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather = page.weather.data

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // The banner belongs to the page on screen: a stale reading for Malé should not warn the
        // user while they are looking at London.
        if (isSelected && uiState.showsStaleBanner) {
            val message = uiState.error
                ?.let { stringResource(it.toPresentation().messageRes) }
                ?: stringResource(R.string.banner_data_may_be_out_of_date)
            StaleDataBanner(message = message, onRetry = onRefresh, onDismiss = onDismissBanner)
        }

        if (weather == null) {
            // A page reached by swiping ahead of its data.
            LoadingView(message = stringResource(R.string.home_loading))
            return@Column
        }

        CurrentConditionsHeader(
            weather = weather,
            unit = uiState.preferences.temperatureUnit,
            showsLocationName = false,
            modifier = Modifier.padding(Spacing.md),
        )

        HourlyStrip(
            // Every page draws its own strip, including the ones off-screen. The slice is cheap,
            // and skipping it made the section appear mid-swipe.
            hours = uiState.hourlyWindow(page),
            zoneOffset = weather.zoneOffset,
            unit = uiState.preferences.temperatureUnit,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        // The forecast picture. Both sections draw nothing when the forecast has not arrived, so a
        // page whose current reading loaded first is not left with empty chart frames.
        page.forecast.data?.let { forecast ->
            TemperatureTrendSection(
                forecast = forecast,
                unit = uiState.preferences.temperatureUnit,
            )
            DailyRangesSection(
                forecast = forecast,
                unit = uiState.preferences.temperatureUnit,
                onDaySelected = { epochDay -> onOpenDayDetail(weather.locationId, epochDay) },
            )
        }

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

        // Below the sun's arc, because it is the end of the same story the arc tells. Absent at latitudes
        // and dates where there is no such window; see `SolarCalculator`.
        goldenHourReading(page.location, weather.zoneOffset)?.let { reading ->
            GoldenHourCard(
                reading = reading,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }

        WeatherDetailGrid(
            // All eight tiles, including the derived dew point and length of day.
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
}

/**
 * A menu of the saved places, for jumping straight to one.
 *
 * Icon-only, and only present when there is more than one place.
 */
@Composable
private fun LocationMenu(uiState: HomeUiState, onSelectPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (!uiState.showsPageIndicator) return
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { isMenuOpen = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.home_select_location),
            )
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            uiState.pages.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(entry.location.displayName) },
                    // A tick beside the place on screen; a column of identical rows cannot say
                    // which one you are looking at.
                    trailingIcon = {
                        if (index == uiState.selectedIndex) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        onSelectPage(index)
                        isMenuOpen = false
                    },
                )
            }
        }
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun TodayLoadingPreview() {
    SkyCastTheme {
        HomeContent(
            uiState = HomeUiState(isLoading = true),
            onRefresh = {},
            onDismissBanner = {},
            onSelectPage = {},
            onOpenDayDetail = { _, _ -> },
            onAddLocation = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun TodayEmptyPreview() {
    SkyCastTheme {
        HomeContent(
            uiState = HomeUiState(hasNoLocation = true),
            onRefresh = {},
            onDismissBanner = {},
            onSelectPage = {},
            onOpenDayDetail = { _, _ -> },
            onAddLocation = {},
        )
    }
}
