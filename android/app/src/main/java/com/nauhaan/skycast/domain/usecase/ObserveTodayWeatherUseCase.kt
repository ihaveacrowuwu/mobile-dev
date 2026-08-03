package com.nauhaan.skycast.domain.usecase

import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Everything the Today screen needs, as one stream.
 *
 * This use case exists because the Today screen genuinely composes three sources,
 * the primary location, its weather, and the unit preferences, and re-subscribing
 * the weather flow when the primary location changes is real orchestration logic.
 * Keeping it here rather than in the view model makes it unit-testable in isolation
 * and stops the same wiring being duplicated on the detail screen.
 *
 * Use cases are added only where there is logic like this. A use case that merely
 * forwards one call to one repository is ceremony, and we do not write them.
 */
class ObserveTodayWeatherUseCase
@Inject
constructor(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodayWeather> = locationRepository
        .observePrimaryLocation()
        // flatMapLatest, not combine: when the primary location changes the old
        // location's weather subscription must be cancelled, not merged.
        .flatMapLatest { location ->
            if (location == null) {
                flowOf(DataState<Weather>() to null)
            } else {
                weatherRepository
                    .observeCurrentWeather(location)
                    .combine(flowOf(location)) { state, loc -> state to loc }
            }
        }.combine(settingsRepository.observePreferences()) { (state, location), preferences ->
            TodayWeather(
                location = location,
                weather = state,
                preferences = preferences,
            )
        }
}

/** The composed result: weather state, the location it belongs to, and display units. */
data class TodayWeather(
    val location: SavedLocation?,
    val weather: DataState<Weather>,
    val preferences: UserPreferences,
) {
    /** True when the user has not added any location yet, an empty state, not an error. */
    val hasNoLocation: Boolean get() = location == null
}
