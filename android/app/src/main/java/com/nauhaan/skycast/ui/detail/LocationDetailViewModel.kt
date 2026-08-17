package com.nauhaan.skycast.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.repository.DataState
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
import javax.inject.Inject

/** State for the pushed location-detail screen. */
data class LocationDetailUiState(
    val location: SavedLocation? = null,
    val weather: Weather? = null,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LocationDetailUiState> =
        flow { emit(locationRepository.getById(locationId)) }
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(DataState<Weather>() to null)
                } else {
                    weatherRepository
                        .observeCurrentWeather(location)
                        .combine(flowOf(location)) { state, loc -> state to loc }
                }
            }
            .combine(settingsRepository.observePreferences()) { (state, location), preferences ->
                Triple(state, location, preferences)
            }
            .combine(manualRefreshInFlight) { (state, location, preferences), manualRefresh ->
                LocationDetailUiState(
                    location = location,
                    weather = state.data,
                    preferences = preferences,
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing || manualRefresh,
                    isStale = state.isStale,
                    error = state.error,
                    isMissing = location == null,
                )
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
            weatherRepository.refresh(location)
            manualRefreshInFlight.value = false
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
