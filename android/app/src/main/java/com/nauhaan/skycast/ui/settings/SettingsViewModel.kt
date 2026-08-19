package com.nauhaan.skycast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.domain.model.PressureUnit
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.VisibilityUnit
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.domain.repository.MetarRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.SpaceWeatherRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Settings tab. */
data class SettingsUiState(val preferences: UserPreferences = UserPreferences(), val isLoading: Boolean = true)

/**
 * View model for Settings.
 *
 * Every setter writes straight through to DataStore and the change comes back via the
 * observed flow: there is no local mutable copy of the preferences to drift out of
 * sync. That round trip is the persistence guarantee: kill the app after toggling a
 * unit and the choice is still there on relaunch.
 */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val weatherRepository: WeatherRepository,
    private val metarRepository: MetarRepository,
    private val spaceWeatherRepository: SpaceWeatherRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        settingsRepository
            .observePreferences()
            .map { SettingsUiState(preferences = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

    fun setTemperatureUnit(unit: TemperatureUnit) = viewModelScope.launch {
        settingsRepository.setTemperatureUnit(unit)
    }

    fun setWindSpeedUnit(unit: WindSpeedUnit) = viewModelScope.launch {
        settingsRepository.setWindSpeedUnit(unit)
    }

    fun setPressureUnit(unit: PressureUnit) = viewModelScope.launch {
        settingsRepository.setPressureUnit(unit)
    }

    fun setVisibilityUnit(unit: VisibilityUnit) = viewModelScope.launch {
        settingsRepository.setVisibilityUnit(unit)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setUseDynamicColour(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setUseDynamicColour(enabled)
    }

    /**
     * Clears cached weather but **not** saved locations.
     *
     * The distinction matters: cache is disposable, the user's locations are not.
     */
    fun clearCache() = viewModelScope.launch {
        weatherRepository.clearCache()
        // Every cache, or the button is a half-truth: the METAR and the Kp reading live in their own
        // tables and would survive a "clear cache" that only touched the weather one.
        metarRepository.clearCache()
        spaceWeatherRepository.clearCache()
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
