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
import com.nauhaan.skycast.domain.model.DisplayUnit
import com.nauhaan.skycast.domain.model.PressureUnit
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.VisibilityUnit
import com.nauhaan.skycast.domain.model.WindSpeedUnit

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
        onPressureUnitChange = viewModel::setPressureUnit,
        onVisibilityUnitChange = viewModel::setVisibilityUnit,
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
    onPressureUnitChange: (PressureUnit) -> Unit,
    onVisibilityUnitChange: (VisibilityUnit) -> Unit,
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

        // One generic group per unit type, driven by `DisplayUnit.displayName`. Reading the name
        // off the unit keeps the list and the UI in step by construction, at the cost of unit names
        // not being translatable.
        UnitGroup(
            title = stringResource(R.string.settings_temperature),
            units = TemperatureUnit.entries,
            selected = uiState.preferences.temperatureUnit,
            onSelect = onTemperatureUnitChange,
        )
        UnitGroup(
            title = stringResource(R.string.settings_wind_speed),
            units = WindSpeedUnit.entries,
            selected = uiState.preferences.windSpeedUnit,
            onSelect = onWindSpeedUnitChange,
        )
        UnitGroup(
            title = stringResource(R.string.settings_pressure),
            units = PressureUnit.entries,
            selected = uiState.preferences.pressureUnit,
            onSelect = onPressureUnitChange,
        )
        UnitGroup(
            title = stringResource(R.string.settings_visibility),
            units = VisibilityUnit.entries,
            selected = uiState.preferences.visibilityUnit,
            onSelect = onVisibilityUnitChange,
        )

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

/**
 * A labelled group of mutually exclusive unit choices.
 *
 * `selectableGroup()` tells TalkBack the rows are one choice, so it announces "2 of 5" rather than
 * reading five unrelated controls.
 */
@Composable
private fun <T> UnitGroup(
    title: String,
    units: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) where T : DisplayUnit {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.selectableGroup()) {
                units.forEach { unit ->
                    RadioRow(
                        label = unit.displayName,
                        selected = selected == unit,
                        onSelect = { onSelect(unit) },
                    )
                }
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

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SkyCastTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false),
            onTemperatureUnitChange = {},
            onWindSpeedUnitChange = {},
            onPressureUnitChange = {},
            onVisibilityUnitChange = {},
            onThemeModeChange = {},
            onDynamicColourChange = {},
            onClearCache = {},
            onNavigateToAbout = {},
        )
    }
}
