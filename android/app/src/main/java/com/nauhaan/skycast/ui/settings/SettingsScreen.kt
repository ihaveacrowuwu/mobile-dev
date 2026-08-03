package com.nauhaan.skycast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.component.LoadingView
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.ui.common.PlaceholderScreen

/**
 * The Settings tab.
 *
 * Fully implemented from the start because it is the clearest demonstration of the
 * persistence requirement: change a unit, kill the app, relaunch, and the choice
 * survives, and every other screen re-renders from cache with no network call.
 */
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onTemperatureUnitChange = viewModel::setTemperatureUnit,
        onWindSpeedUnitChange = viewModel::setWindSpeedUnit,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColourChange = viewModel::setUseDynamicColour,
        onClearCache = { viewModel.clearCache() },
        onNavigateToAbout = onNavigateToAbout,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onWindSpeedUnitChange: (WindSpeedUnit) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColourChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        LoadingView(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.sm),
    ) {
        SectionHeader(stringResource(R.string.settings_section_units))

        // selectableGroup() tells TalkBack these radio buttons are one choice, so it
        // announces "1 of 2" rather than reading two unrelated controls.
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            Column(modifier = Modifier.selectableGroup()) {
                TemperatureUnit.entries.forEach { unit ->
                    RadioRow(
                        label = stringResource(
                            when (unit) {
                                TemperatureUnit.CELSIUS -> R.string.unit_celsius
                                TemperatureUnit.FAHRENHEIT -> R.string.unit_fahrenheit
                            },
                        ),
                        selected = uiState.preferences.temperatureUnit == unit,
                        onSelect = { onTemperatureUnitChange(unit) },
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                WindSpeedUnit.entries.forEach { unit ->
                    RadioRow(
                        label = unit.symbol,
                        selected = uiState.preferences.windSpeedUnit == unit,
                        onSelect = { onWindSpeedUnitChange(unit) },
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.settings_section_appearance))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        selected = uiState.preferences.themeMode == mode,
                        onSelect = { onThemeModeChange(mode) },
                    )
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_colour)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_dynamic_colour_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.preferences.useDynamicColour,
                            onCheckedChange = onDynamicColourChange,
                        )
                    },
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_section_storage))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_clear_cache_summary))
                    },
                    modifier = Modifier.selectable(selected = false, onClick = onClearCache),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about)) },
                    modifier = Modifier.selectable(selected = false, onClick = onNavigateToAbout),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = Spacing.md + Spacing.sm,
            top = Spacing.md,
            bottom = Spacing.sm,
        ),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            // onClick = null: the whole row is the target, so the radio itself must not
            // be separately focusable or TalkBack would announce it twice.
            RadioButton(selected = selected, onClick = null)
        },
        modifier = modifier.selectable(
            selected = selected,
            onClick = onSelect,
            role = Role.RadioButton,
        ),
    )
}

/** Dependency licences and attribution, see `docs/licensing.md` (MO4). */
@Composable
fun AboutScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_about),
        plannedContent = stringResource(R.string.placeholder_about),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SkyCastTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false),
            onTemperatureUnitChange = {},
            onWindSpeedUnitChange = {},
            onThemeModeChange = {},
            onDynamicColourChange = {},
            onClearCache = {},
            onNavigateToAbout = {},
        )
    }
}
