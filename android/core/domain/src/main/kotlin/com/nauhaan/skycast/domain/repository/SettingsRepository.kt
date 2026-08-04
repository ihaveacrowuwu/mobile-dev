package com.nauhaan.skycast.domain.repository

import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import kotlinx.coroutines.flow.Flow

/**
 * User settings, backed by DataStore.
 *
 * Exposed as a [Flow], so changing the temperature unit re-renders every screen from the existing
 * cache with no network call, and works offline.
 */
interface SettingsRepository {
    fun observePreferences(): Flow<UserPreferences>

    suspend fun setTemperatureUnit(unit: TemperatureUnit)

    suspend fun setWindSpeedUnit(unit: WindSpeedUnit)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setUseDynamicColour(enabled: Boolean)

    /** Restores every preference to its default. */
    suspend fun reset()
}
