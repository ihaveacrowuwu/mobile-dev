package com.nauhaan.skycast.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.ErrorView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.component.StaleDataBanner
import com.nauhaan.skycast.core.designsystem.component.WeatherBackground
import com.nauhaan.skycast.core.designsystem.component.WeatherDetailGrid
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.core.designsystem.theme.weatherTint
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.usecase.TodayLocationWeather
import com.nauhaan.skycast.ui.common.CurrentConditionsHeader
import com.nauhaan.skycast.ui.common.rememberWeatherDetailLabels
import com.nauhaan.skycast.ui.common.toDetails
import com.nauhaan.skycast.ui.common.toPresentation

/**
 * The Today tab.
 *
 * Two composables, deliberately:
 *
 * - [TodayScreen] is the **stateful** entry point. It obtains the view model and does nothing else.
 * - [TodayContent] is **stateless**, state in, lambdas out. That is what makes it previewable in
 *   every state and assertable in a Compose test without Hilt.
 *
 * Follow this split for every screen.
 */
@Composable
fun TodayScreen(
    onNavigateToLocationDetail: (Long) -> Unit,
    onNavigateToAddLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
    onWeatherTintChanged: (Color?) -> Unit = {},
) {
    // collectAsStateWithLifecycle, not collectAsState: stops collection when the
    // screen is not visible instead of doing work in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Reported upwards so the navigation bar can share this page's mood. The bar is a sibling of
    // the content rather than a descendant, so the CompositionLocal the tiles read cannot reach
    // it, state goes up by lambda and the colour comes back down as a parameter.
    val weather = uiState.weather
    val tint = weather?.let { weatherTint(it.condition, it.isDaytime) }
    LaunchedEffect(tint) { onWeatherTintChanged(tint) }

    TodayContent(
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
internal fun TodayContent(
    uiState: TodayUiState,
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
            LoadingView(modifier = modifier, message = stringResource(R.string.today_loading))

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

        else -> TodayPager(
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
private fun TodayPager(
    uiState: TodayUiState,
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
            LocationSwitcher(
                uiState = uiState,
                onSelectPage = onSelectPage,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(state = pagerState) { page ->
                    TodayPage(
                        page = uiState.pages[page],
                        uiState = uiState,
                        isSelected = page == uiState.selectedIndex,
                        onRefresh = onRefresh,
                        onDismissBanner = onDismissBanner,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
    }
}

/** One page: the hero reading, the hourly strip and the detail tiles for a single place. */
@Composable
private fun TodayPage(
    page: TodayLocationWeather,
    uiState: TodayUiState,
    isSelected: Boolean,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenDetail: (Long) -> Unit,
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
            LoadingView(message = stringResource(R.string.today_loading))
            return@Column
        }

        CurrentConditionsHeader(
            weather = weather,
            unit = uiState.preferences.temperatureUnit,
            showsLocationName = false,
            // Tapping the hero block pushes the full detail screen, the push half of the
            // navigation hierarchy, reachable from Today.
            onClick = { onOpenDetail(weather.locationId) },
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

        WeatherDetailGrid(
            // Six tiles here, eight on the detail screen: Today is a glance, and the derived
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
 * The place currently shown, with a menu of the others and a page indicator.
 *
 * The dots are not interactive, they report position, and the menu beside them is the control.
 * Making six-pixel dots a tap target would fail the 48 dp minimum for no gain.
 */
@Composable
private fun LocationSwitcher(uiState: TodayUiState, onSelectPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val current = uiState.location ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            TextButton(onClick = { isMenuOpen = true }) {
                Text(text = current.name, style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.today_select_location),
                )
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                uiState.pages.forEachIndexed { index, entry ->
                    DropdownMenuItem(
                        text = { Text(entry.location.displayName) },
                        onClick = {
                            onSelectPage(index)
                            isMenuOpen = false
                        },
                    )
                }
            }
        }

        if (uiState.showsPageIndicator) {
            PageDots(
                count = uiState.pages.size,
                selectedIndex = uiState.selectedIndex,
                currentName = current.name,
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, selectedIndex: Int, currentName: String, modifier: Modifier = Modifier) {
    val announcement = stringResource(
        R.string.today_showing_location,
        currentName,
        selectedIndex + 1,
        count,
    )

    Row(
        modifier = modifier
            .padding(end = Spacing.sm)
            // One announcement for the whole indicator; individual dots mean nothing aloud.
            .clearAndSetSemantics { contentDescription = announcement },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(if (isSelected) SelectedDotSize else DotSize)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

private val DotSize = 6.dp
private val SelectedDotSize = 8.dp

// ── Previews: one per state, so every branch is reviewable without a device ──

@Preview(name = "Loading", showBackground = true)
@Composable
private fun TodayLoadingPreview() {
    SkyCastTheme {
        TodayContent(
            uiState = TodayUiState(isLoading = true),
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
        TodayContent(
            uiState = TodayUiState(hasNoLocation = true),
            onRefresh = {},
            onDismissBanner = {},
            onSelectPage = {},
            onOpenDetail = {},
            onAddLocation = {},
        )
    }
}
