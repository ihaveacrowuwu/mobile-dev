package com.nauhaan.skycast.ui.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.EmptyStateView
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.domain.model.SavedLocation

/**
 * The Locations tab: the user's saved places.
 *
 * Same stateful/stateless split as [com.nauhaan.skycast.ui.today.TodayScreen]: this composable
 * only obtains the view model, [LocationsContent] does the rendering and is previewable.
 */
@Composable
fun LocationsScreen(
    onNavigateToAddLocation: () -> Unit,
    onNavigateToLocationDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LocationsContent(
        uiState = uiState,
        onAddLocation = onNavigateToAddLocation,
        onOpenDetail = onNavigateToLocationDetail,
        onSetPrimary = viewModel::setPrimary,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationsContent(
    uiState: LocationsUiState,
    onAddLocation: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onSetPrimary: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLocation) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add_location),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingView(modifier = Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyStateView(
                title = stringResource(R.string.locations_empty_title),
                message = stringResource(R.string.locations_empty_message),
                icon = Icons.Filled.MyLocation,
                actionLabel = stringResource(R.string.action_add_location),
                onAction = onAddLocation,
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(uiState.locations, key = { it.id }) { location ->
                    SavedLocationRow(
                        location = location,
                        canDelete = uiState.locations.size > 1,
                        onOpen = { onOpenDetail(location.id) },
                        onSetPrimary = { onSetPrimary(location) },
                        onDelete = { onDelete(location) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SavedLocationRow(
    location: SavedLocation,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(location.name) },
        supportingContent = { Text(location.displayName) },
        leadingContent = {
            IconButton(onClick = onSetPrimary, enabled = !location.isPrimary) {
                Icon(
                    imageVector = if (location.isPrimary) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (location.isPrimary) {
                            R.string.locations_is_primary
                        } else {
                            R.string.locations_set_primary
                        },
                        location.name,
                    ),
                    tint = if (location.isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingContent = {
            // The last location cannot be deleted: with none saved, Today has nothing to show
            // and the user is stranded on an empty state they did not ask for.
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.locations_delete, location.name),
                    )
                }
            }
        },
        modifier = modifier.clickable(onClick = onOpen),
    )
}

@Preview(name = "Locations, populated", showBackground = true)
@Composable
private fun LocationsContentPreview() {
    SkyCastTheme {
        LocationsContent(
            uiState = LocationsUiState(
                locations = listOf(
                    SavedLocation(
                        id = 1,
                        name = "London",
                        countryCode = "GB",
                        state = "England",
                        latitude = 51.5074,
                        longitude = -0.1278,
                        isPrimary = true,
                    ),
                    SavedLocation(
                        id = 2,
                        name = "Malé",
                        countryCode = "MV",
                        latitude = 4.1748,
                        longitude = 73.5089,
                    ),
                ),
                isLoading = false,
            ),
            onAddLocation = {},
            onOpenDetail = {},
            onSetPrimary = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Locations, empty", showBackground = true)
@Composable
private fun LocationsEmptyPreview() {
    SkyCastTheme {
        LocationsContent(
            uiState = LocationsUiState(isLoading = false),
            onAddLocation = {},
            onOpenDetail = {},
            onSetPrimary = {},
            onDelete = {},
        )
    }
}
