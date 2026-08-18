package com.nauhaan.skycast.ui.home

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.PageScrubber
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailGrid
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherSurfaceTint
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.usecase.TodayLocationWeather
import com.nauhaan.skycast.ui.common.CurrentConditionsHeader
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
    onNavigateToLocationDetail: (Long) -> Unit,
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
        onOpenDetail = onNavigateToLocationDetail,
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
    onOpenDetail: (Long) -> Unit,
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
            onOpenDetail = onOpenDetail,
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
    onOpenDetail: (Long) -> Unit,
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
            LocationMenu(
                uiState = uiState,
                onSelectPage = onSelectPage,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )

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
                        onOpenDetail = onOpenDetail,
                        onSelectPage = onSelectPage,
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
    onOpenDetail: (Long) -> Unit,
    onSelectPage: (Int) -> Unit,
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

        PlaceHeading(
            location = page.location,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )

        CurrentConditionsHeader(
            weather = weather,
            unit = uiState.preferences.temperatureUnit,
            showsLocationName = false,
            // Tapping the hero block pushes the full detail screen, the push half of the
            // navigation hierarchy, reachable from Home.
            onClick = { onOpenDetail(weather.locationId) },
            modifier = Modifier.padding(Spacing.md),
        )

        // Between the reading and the strip, centred: the indicator belongs to the pager, so it
        // sits directly under what paging changes rather than off in a corner of the chrome.
        if (uiState.showsPageIndicator) {
            PageScrubber(
                count = uiState.pages.size,
                selectedIndex = uiState.selectedIndex,
                onSelect = onSelectPage,
                contentDescription = stringResource(
                    R.string.home_showing_location,
                    page.location.name,
                    uiState.selectedIndex + 1,
                    uiState.pages.size,
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = Spacing.sm),
            )
        }

        HourlyStrip(
            // Every page draws its own strip, including the ones off-screen. The slice is cheap,
            // and skipping it made the section appear mid-swipe.
            hours = uiState.hourlyWindow(page),
            zoneOffset = weather.zoneOffset,
            unit = uiState.preferences.temperatureUnit,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        WeatherDetailGrid(
            // Six tiles here, eight on the detail screen: Home is a glance, and the derived
            // readings are why someone taps through.
            details = weather.toDetails(
                preferences = uiState.preferences,
                labels = rememberWeatherDetailLabels(),
            ),
            modifier = Modifier.padding(horizontal = Spacing.md),
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

/**
 * The place, as the page's heading.
 *
 * Large and at the top of the content, because it is the single most important word on the screen
 * and it spent a while as the smallest, a label inside a text button in the corner.
 *
 * The region line beneath it earns its space when two saved places share a name, which is common
 * enough (there are more than twenty Londons) that "London" alone can be genuinely ambiguous.
 */
@Composable
private fun PlaceHeading(location: SavedLocation, modifier: Modifier = Modifier) {
    val region = location.displayName
        .split(",")
        .drop(1)
        .joinToString(", ") { it.trim() }
        .takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(location.name, region).joinToString(". ")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = location.name,
            // The emphasized display role: this is the one place on Home that earns it, alongside
            // the temperature. Two per screen is the ceiling Material 3 sets.
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        if (region != null) {
            Text(
                text = region,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
            onOpenDetail = {},
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
            onOpenDetail = {},
            onAddLocation = {},
        )
    }
}
