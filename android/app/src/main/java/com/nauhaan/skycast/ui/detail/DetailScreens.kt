package com.nauhaan.skycast.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nauhaan.skycast.R
import com.nauhaan.skycast.ui.common.PlaceholderScreen

/**
 * Pushed detail destinations.
 *
 * These exist from the start because the *Navigation* criterion asks for a real
 * hierarchy, tabs **and** push navigation with correct back behaviour. Both are
 * demonstrable, screenshottable and UI-testable before their content is written.
 */

/** Full conditions for one saved location, pushed from Today or Locations. */
@Composable
fun LocationDetailScreen(locationId: Long, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.detail_location_title),
        plannedContent = stringResource(R.string.placeholder_location_detail, locationId),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/** The 3-hourly breakdown for one forecast day, pushed from the Forecast tab. */
@Composable
fun DayDetailScreen(locationId: Long, epochDay: Long, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.detail_day_title),
        plannedContent = stringResource(R.string.placeholder_day_detail, epochDay, locationId),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
