package com.nauhaan.skycast.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nauhaan.skycast.domain.model.PressureUnit
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.VisibilityUnit
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes settings via Jetpack DataStore, the Android counterpart to `UserDefaults` on
 * iOS.
 *
 * DataStore rather than Room, because these are a handful of independent scalars with no
 * relationships.
 */
@Singleton
class UserPreferencesDataSource
@Inject
constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> =
        dataStore.data
            // A corrupt or unreadable file must not crash the app on launch: fall back to
            // defaults and let the user carry on.
            .catch { throwable ->
                if (throwable is IOException) emit(emptyPreferences()) else throw throwable
            }.map { prefs ->
                UserPreferences(
                    temperatureUnit = prefs.readEnum(Keys.TEMPERATURE_UNIT, TemperatureUnit.CELSIUS),
                    windSpeedUnit = prefs.readEnum(Keys.WIND_SPEED_UNIT, WindSpeedUnit.METRES_PER_SECOND),
                    pressureUnit = prefs.readEnum(Keys.PRESSURE_UNIT, PressureUnit.HECTOPASCALS),
                    visibilityUnit = prefs.readEnum(Keys.VISIBILITY_UNIT, VisibilityUnit.KILOMETRES),
                    themeMode = prefs.readEnum(Keys.THEME_MODE, ThemeMode.SYSTEM),
                    useDynamicColour = prefs[Keys.USE_DYNAMIC_COLOUR] ?: true,
                )
            }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) = edit(Keys.TEMPERATURE_UNIT, unit.name)

    suspend fun setWindSpeedUnit(unit: WindSpeedUnit) = edit(Keys.WIND_SPEED_UNIT, unit.name)

    suspend fun setPressureUnit(unit: PressureUnit) = edit(Keys.PRESSURE_UNIT, unit.name)

    suspend fun setVisibilityUnit(unit: VisibilityUnit) = edit(Keys.VISIBILITY_UNIT, unit.name)

    suspend fun setThemeMode(mode: ThemeMode) = edit(Keys.THEME_MODE, mode.name)

    suspend fun setUseDynamicColour(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_DYNAMIC_COLOUR] = enabled }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    /**
     * Reads an enum by name, falling back to [default] if the stored value is absent or is not a
     * valid constant, which happens when an enum case is renamed in a later app version.
     */
    private inline fun <reified E : Enum<E>> Preferences.readEnum(key: Preferences.Key<String>, default: E): E {
        val stored = this[key] ?: return default
        return runCatching { enumValueOf<E>(stored) }.getOrDefault(default)
    }

    private object Keys {
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WIND_SPEED_UNIT = stringPreferencesKey("wind_speed_unit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val VISIBILITY_UNIT = stringPreferencesKey("visibility_unit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOUR = booleanPreferencesKey("use_dynamic_colour")
    }

    companion object {
        /** File name under `<app>/datastore/`. */
        const val DATA_STORE_NAME = "skycast_settings"
    }
}
