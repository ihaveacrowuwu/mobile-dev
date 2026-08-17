package com.nauhaan.skycast.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Forecast tab.
 *
 * Same derived-property approach as `TodayUiState`: the offline-first display rules live on the
 * state, so the composable never combines flags itself.
 */
data class ForecastUiState(
    val location: SavedLocation? = null,
    val forecast: Forecast? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: AppError? = null,
    val hasNoLocation: Boolean = false,
) {
    val showsFullScreenLoader: Boolean get() = isLoading && forecast == null
    val showsFullScreenError: Boolean get() = error != null && forecast == null && !hasNoLocation
    val showsEmptyState: Boolean get() = hasNoLocation && !isLoading
    val showsContent: Boolean get() = forecast != null && forecast.days.isNotEmpty()
    val showsStaleBanner: Boolean get() = showsContent && (error != null || isStale)
}

/**
 * View model for the Forecast tab.
 *
 * Mirrors `TodayViewModel`: resubscribes when the primary location changes, and combines the
 * unit preferences in so switching °C→°F re-renders from cache with no network call.
 */
@HiltViewModel
class ForecastViewModel
@Inject
constructor(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val manualRefreshInFlight = MutableStateFlow(false)

    /**
     * The error from the most recent **manual** refresh. See `TodayViewModel` for why this is
     * kept apart from the observed `DataState.error`: a failed pull-to-refresh does not
     * necessarily make the stream re-emit, so without this the failure is silent.
     */
    private val manualRefreshError = MutableStateFlow<AppError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ForecastUiState> =
        locationRepository
            .observePrimaryLocation()
            // flatMapLatest, not combine: a changed primary location must cancel the previous
            // location's forecast subscription rather than merge with it.
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(DataState<Forecast>() to null)
                } else {
                    weatherRepository
                        .observeForecast(location)
                        .combine(flowOf(location)) { state, loc -> state to loc }
                }
            }
            .combine(settingsRepository.observePreferences()) { (state, location), preferences ->
                Triple(state, location, preferences)
            }
            .combine(manualRefreshInFlight) { triple, manualRefresh -> triple to manualRefresh }
            .combine(manualRefreshError) { (triple, manualRefresh), refreshError ->
                val (state, location, preferences) = triple
                ForecastUiState(
                    location = location,
                    forecast = state.data,
                    preferences = preferences,
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing || manualRefresh,
                    isStale = state.isStale,
                    error = state.error ?: refreshError,
                    hasNoLocation = location == null,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = ForecastUiState(isLoading = true),
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
