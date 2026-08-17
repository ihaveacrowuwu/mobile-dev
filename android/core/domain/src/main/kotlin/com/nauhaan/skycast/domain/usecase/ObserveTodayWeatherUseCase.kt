package com.nauhaan.skycast.domain.usecase

import com.nauhaan.skycast.domain.model.Forecast
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
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Everything the Today screen needs, as one stream.
 *
 * This use case exists because the Today screen genuinely composes several sources, every saved
 * location, each one's current weather and forecast, and the unit preferences, and re-subscribing
 * when the saved list changes is real orchestration logic. Keeping it here rather than in the view
 * model makes it unit-testable in isolation and stops the same wiring being duplicated.
 *
 * Use cases are added only where there is logic like this. A use case that merely forwards one call
 * to one repository is ceremony, and we do not write them.
 *
 * ## Why every location, not just the primary
 *
 * Today is swipeable: the user pages between their saved places. Loading only the visible one would
 * mean every swipe lands on a spinner. Observing them all costs nothing extra in practice, the
 * repository serves each from cache and honours its TTL, so a handful of locations produce a
 * handful of network calls per TTL window, not per swipe.
 */
class ObserveTodayWeatherUseCase
@Inject
constructor(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodayBoard> = locationRepository
        .observeSavedLocations()
        // flatMapLatest, not combine: when the saved list changes, the previous set of weather
        // subscriptions must be cancelled rather than merged with the new one.
        .flatMapLatest { locations ->
            if (locations.isEmpty()) {
                // `combine` of an empty list never emits, so the empty case needs its own branch,
                // otherwise the screen would sit on its initial loading state forever.
                flowOf(emptyList())
            } else {
                combine(locations.map { location -> observeOne(location) }) { it.toList() }
            }
        }.combine(settingsRepository.observePreferences()) { entries, preferences ->
            TodayBoard(entries = entries, preferences = preferences)
        }

    private fun observeOne(location: SavedLocation): Flow<TodayLocationWeather> = combine(
        weatherRepository.observeCurrentWeather(location),
        weatherRepository.observeForecast(location),
    ) { weather, forecast ->
        TodayLocationWeather(location = location, weather = weather, forecast = forecast)
    }

    /** The primary location's position in the saved list, or 0 when there is none. */
    fun primaryIndex(): Flow<Int> = locationRepository
        .observeSavedLocations()
        .map { locations -> locations.indexOfFirst { it.isPrimary }.coerceAtLeast(0) }
}

/** One saved location's current conditions and forecast. */
data class TodayLocationWeather(
    val location: SavedLocation,
    val weather: DataState<Weather>,
    val forecast: DataState<Forecast>,
)

/** Every saved location's weather, plus the units to render it in. */
data class TodayBoard(
    val entries: List<TodayLocationWeather> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
) {
    /** True when the user has not added any location yet, an empty state, not an error. */
    val hasNoLocation: Boolean get() = entries.isEmpty()
}
