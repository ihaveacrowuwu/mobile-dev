package com.nauhaan.skycast.data.repository

import com.nauhaan.skycast.data.preferences.UserPreferencesDataSource
import com.nauhaan.skycast.domain.model.PressureUnit
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.VisibilityUnit
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin pass-through to [UserPreferencesDataSource].
 *
 * The indirection is deliberate: `domain` owns the [SettingsRepository] interface, so
 * nothing above the data layer knows DataStore exists. Swapping to encrypted storage
 * later would touch only this file and its data source.
 */
@Singleton
class SettingsRepositoryImpl
@Inject
constructor(private val dataSource: UserPreferencesDataSource) :
    SettingsRepository {
    override fun observePreferences(): Flow<UserPreferences> = dataSource.preferences

    override suspend fun setTemperatureUnit(unit: TemperatureUnit) = dataSource.setTemperatureUnit(unit)

    override suspend fun setWindSpeedUnit(unit: WindSpeedUnit) = dataSource.setWindSpeedUnit(unit)

    override suspend fun setPressureUnit(unit: PressureUnit) = dataSource.setPressureUnit(unit)

    override suspend fun setVisibilityUnit(unit: VisibilityUnit) = dataSource.setVisibilityUnit(unit)

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setUseDynamicColour(enabled: Boolean) = dataSource.setUseDynamicColour(enabled)

    override suspend fun reset() = dataSource.clear()
}
