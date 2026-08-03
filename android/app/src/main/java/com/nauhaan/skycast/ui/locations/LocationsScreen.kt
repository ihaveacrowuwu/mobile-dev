package com.nauhaan.skycast.ui.locations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.ui.common.PlaceholderScreen
import com.nauhaan.skycast.ui.common.PreviewLocationId

/**
 * The Locations tab, the user's saved places, reorderable and deletable.
 *
 * Reads from `LocationRepository.observeSavedLocations()`, which is already implemented
 * and Room-backed, so this screen is pure presentation work.
 *
 * Both onward routes are wired now so the navigation graph can be traversed end to end
 * by the UI test and captured for the README screenshots.
 */
@Composable
fun LocationsScreen(
    onNavigateToAddLocation: () -> Unit,
    onNavigateToLocationDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.tab_locations),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.placeholder_locations),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        OutlinedButton(
            onClick = onNavigateToAddLocation,
            modifier = Modifier.padding(top = Spacing.lg),
        ) {
            Text(stringResource(R.string.action_add_location))
        }
        OutlinedButton(
            onClick = { onNavigateToLocationDetail(PreviewLocationId) },
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            Text(stringResource(R.string.placeholder_action_open_location))
        }
    }
}

/**
 * Geocoding search, pushed from the Locations tab.
 *
 * When built, debounce the query by ~400 ms before calling
 * `LocationRepository.search()`, the free API tier allows 60 calls/minute and a
 * per-keystroke search would exhaust it in seconds.
 */
@Composable
fun AddLocationScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.action_add_location),
        plannedContent = stringResource(R.string.placeholder_add_location),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
