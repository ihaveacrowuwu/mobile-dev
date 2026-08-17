package com.nauhaan.skycast.ui.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.ui.common.toPresentation

/**
 * Search OpenWeather's geocoder and save a place.
 *
 * Pops itself once a save succeeds, so the user lands back on the list that now contains what
 * they just added, rather than having to press back and wonder whether it worked.
 */
@Composable
fun AddLocationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddLocationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedLocationName) {
        if (uiState.savedLocationName != null) {
            viewModel.consumeSavedEvent()
            onNavigateBack()
        }
    }

    AddLocationContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSelect = viewModel::save,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddLocationContent(
    uiState: AddLocationUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (LocationSearchResult) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_add_location)) },
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
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.add_location_field_label)) },
                placeholder = { Text(stringResource(R.string.add_location_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.add_location_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            )

            when {
                uiState.isSearching -> CentredMessage {
                    // Expressive's shape-morphing indicator, matching the rest of the app.
                    LoadingIndicator()
                }

                uiState.error != null -> {
                    val presentation = uiState.error.toPresentation()
                    CentredMessage {
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
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }

                uiState.showsPrompt -> CentredMessage {
                    Text(
                        text = stringResource(R.string.add_location_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                uiState.showsNoResults -> CentredMessage {
                    Text(
                        text = stringResource(R.string.add_location_no_results, uiState.query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn {
                    items(uiState.results, key = { it.id }) { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = { Text(result.displayName) },
                            modifier = Modifier.clickable { onSelect(result) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Shared centred block for the search screen's four non-list states. */
@Composable
private fun CentredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Preview(name = "Add location: results", showBackground = true)
@Composable
private fun AddLocationResultsPreview() {
    SkyCastTheme {
        AddLocationContent(
            uiState = AddLocationUiState(
                query = "Lond",
                results = listOf(
                    LocationSearchResult("London", "GB", "England", 51.5074, -0.1278),
                    LocationSearchResult("London", "CA", "Ontario", 42.9834, -81.233),
                ),
            ),
            onQueryChange = {},
            onSelect = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Add location: prompt", showBackground = true)
@Composable
private fun AddLocationPromptPreview() {
    SkyCastTheme {
        AddLocationContent(
            uiState = AddLocationUiState(),
            onQueryChange = {},
            onSelect = {},
            onNavigateBack = {},
        )
    }
}
