package com.nauhaan.skycast.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.HourlyForecast
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import com.nauhaan.skycast.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** State for the pushed location-detail screen. */
data class LocationDetailUiState(
    val location: SavedLocation? = null,
    val weather: Weather? = null,
    /**
     * The multi-day forecast for the same place.
     *
     * The detail screen is where someone goes when the Home card was not enough, so it carries
     * the hour-by-hour and day-by-day picture too. It is nullable and never blocks: the current
     * reading renders the moment it arrives, and the forecast sections appear when they do.
     */
    val forecast: Forecast? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: AppError? = null,
    /** The id resolved to no saved record: the location was removed while this screen was open. */
    val isMissing: Boolean = false,
) {
    val showsFullScreenLoader: Boolean get() = isLoading && weather == null && !isMissing
    val showsFullScreenError: Boolean get() = error != null && weather == null && !isMissing
    val showsStaleBanner: Boolean get() = weather != null && (error != null || isStale)

    /** Every 3-hourly reading the forecast holds, flattened for the trend chart. */
    val hourlyReadings: List<HourlyForecast>
        get() = forecast?.days?.flatMap { it.hourly }.orEmpty()

    /** The hours from now on, for the strip. Past readings belong on Home, not here. */
    fun upcomingHours(now: Instant = Instant.now()): List<HourlyForecast> =
        hourlyReadings.filter { !it.time.isBefore(now) }
}

/**
 * Full conditions for one saved location.
 *
 * Reads the location **id** from the navigation route, so the record is re-resolved on each entry
 * and can report that it is gone.
 *
 * The id arrives via [SavedStateHandle.toRoute], so the view model owns argument decoding and the
 * composable needs no `locationId` parameter to be testable.
 */
@HiltViewModel
class LocationDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val locationId: Long = savedStateHandle.toRoute<Route.LocationDetail>().locationId

    private val manualRefreshInFlight = MutableStateFlow(false)

    /** See `HomeViewModel.manualRefreshError`: a failed manual refresh is otherwise silent. */
    private val manualRefreshError = MutableStateFlow<AppError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LocationDetailUiState> =
        flow { emit(locationRepository.getById(locationId)) }
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(LocationDetailUiState(isLoading = false, isMissing = true))
                } else {
                    // One `combine` over five sources rather than a chain of pairs and triples.
                    // The chain worked, but every new source cost another destructuring step and
                    // adding the forecast would have made it a Pair of a Triple.
                    combine(
                        weatherRepository.observeCurrentWeather(location),
                        weatherRepository.observeForecast(location),
                        settingsRepository.observePreferences(),
                        manualRefreshInFlight,
                        manualRefreshError,
                    ) { weather, forecast, preferences, manualRefresh, refreshError ->
                        LocationDetailUiState(
                            location = location,
                            weather = weather.data,
                            forecast = forecast.data,
                            preferences = preferences,
                            isLoading = weather.isLoading,
                            isRefreshing = weather.isRefreshing || manualRefresh,
                            isStale = weather.isStale,
                            // A failed forecast hides its section rather than warning about the
                            // whole screen.
                            error = weather.error ?: refreshError,
                            isMissing = false,
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = LocationDetailUiState(),
            )

    fun refresh() {
        val location = uiState.value.location ?: return
        viewModelScope.launch {
            manualRefreshInFlight.value = true
            manualRefreshError.value = weatherRepository.refresh(location)
            manualRefreshInFlight.value = false
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
