package com.nauhaan.skycast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The weather the whole app is painted with.
 *
 * The favourite location supplies the background behind every screen that has no weather of its
 * own: METAR, Locations and Settings. Home paints its own background per page and draws over this
 * one, and the Moon tab has a night sky instead.
 *
 * A view model rather than a CompositionLocal, because the background is drawn by the shell above
 * the navigation graph, so no screen is in a position to provide it, and it is read once for the
 * whole app.
 */
@HiltViewModel
class AppBackgroundViewModel
@Inject
constructor(
    locationRepository: LocationRepository,
    weatherRepository: WeatherRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val background: StateFlow<AppBackground> = locationRepository
        .observePrimaryLocation()
        .flatMapLatest { favourite ->
            if (favourite == null) {
                flowOf(AppBackground())
            } else {
                // Cached weather satisfies this without a request, since Home is already fetching it, and a
                // failure needs no handling here. `AppBackground()` is a neutral wash, not an error.
                weatherRepository.observeCurrentWeather(favourite).map { state ->
                    val weather = state.data
                    AppBackground(
                        condition = weather?.condition ?: WeatherCondition.UNKNOWN,
                        isDaytime = weather?.isDaytime ?: true,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = AppBackground(),
        )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** The favourite's condition, or a neutral default before one is known. */
data class AppBackground(val condition: WeatherCondition = WeatherCondition.UNKNOWN, val isDaytime: Boolean = true)
