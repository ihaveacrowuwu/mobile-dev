package com.nauhaan.skycast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.data.repository.DebugLocationSeeder
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The two preferences the theme needs before the first frame can be drawn. */
data class ThemeState(val themeMode: ThemeMode = ThemeMode.SYSTEM, val useDynamicColour: Boolean = true)

/**
 * Supplies the theme to [MainActivity] and gates the splash screen until the stored
 * preference has been read.
 *
 * Without the gate, DataStore's first read completes a frame or two after launch and a
 * user with dark mode selected sees a white flash. Holding the splash screen for those
 * few milliseconds is the standard fix.
 */
@HiltViewModel
class MainViewModel
@Inject
constructor(
    settingsRepository: SettingsRepository,
    debugLocationSeeder: DebugLocationSeeder,
) : ViewModel() {
    init {
        // Debug-only, and a no-op unless the locations table is empty. Called from here
        // rather than Application.onCreate so it uses viewModelScope instead of a
        // hand-rolled scope, and avoids the `@Inject lateinit var` field injection that
        // Application would require (which detekt's LateinitUsage rule rejects).
        // Delete together with DebugLocationSeeder when the Locations feature ships.
        viewModelScope.launch { debugLocationSeeder.seedIfEmpty() }
    }

    private val _isLoadingTheme = MutableStateFlow(true)
    val isLoadingTheme: StateFlow<Boolean> = _isLoadingTheme

    val themeState: StateFlow<ThemeState> =
        settingsRepository
            .observePreferences()
            .map { ThemeState(themeMode = it.themeMode, useDynamicColour = it.useDynamicColour) }
            .onEach { _isLoadingTheme.value = false }
            .stateIn(
                scope = viewModelScope,
                // Eagerly: this must be resolved before the first composition, not on
                // first subscription.
                started = SharingStarted.Eagerly,
                initialValue = ThemeState(),
            )
}
