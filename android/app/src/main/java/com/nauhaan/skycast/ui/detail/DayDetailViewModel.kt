package com.nauhaan.skycast.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.ForecastDay
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import com.nauhaan.skycast.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/** State for the pushed day-detail screen. */
data class DayDetailUiState(
    val locationName: String = "",
    val date: LocalDate? = null,
    val day: ForecastDay? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /**
     * The forecast location's UTC offset, so the hourly rows read as that place's clock.
     * Defaults to UTC only for the initial, dataless state.
     */
    val zoneOffset: ZoneOffset = ZoneOffset.UTC,
    /** The requested day is not in the cached forecast: it rolled out of the 5-day window. */
    val isMissing: Boolean = false,
)

/**
 * The 3-hourly breakdown for one forecast day.
 *
 * Resolves the day out of the cached forecast rather than taking a `ForecastDay` through the back
 * stack, for the same reason [LocationDetailViewModel] takes an id: a value carried in the route is
 * a snapshot that silently goes stale.
 */
@HiltViewModel
class DayDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val route: Route.DayDetail = savedStateHandle.toRoute()
    private val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayDetailUiState> =
        flow { emit(locationRepository.getById(route.locationId)) }
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(DayDetailUiState(isLoading = false, isMissing = true))
                } else {
                    weatherRepository.observeForecast(location).combine(
                        settingsRepository.observePreferences(),
                    ) { state, preferences ->
                        val forecast = state.data
                        val day = forecast?.days?.firstOrNull { it.date == date }
                        DayDetailUiState(
                            locationName = forecast?.locationName ?: location.name,
                            date = date,
                            day = day,
                            preferences = preferences,
                            zoneOffset = forecast?.zoneOffset ?: ZoneOffset.UTC,
                            isLoading = state.isLoading,
                            // A refresh failure with a day already resolved is not worth
                            // surfacing here: the hourly readings for one day do not change
                            // materially, and the Forecast tab owns the retry affordance.
                            error = state.error.takeIf { day == null },
                            // Only "missing" once the forecast has actually arrived and does
                            // not contain the day; while loading, absence proves nothing.
                            isMissing = forecast != null && day == null,
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = DayDetailUiState(date = date),
            )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
