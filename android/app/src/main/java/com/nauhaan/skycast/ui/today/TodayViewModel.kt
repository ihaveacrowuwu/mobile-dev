package com.nauhaan.skycast.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.domain.repository.WeatherRepository
import com.nauhaan.skycast.domain.usecase.ObserveTodayWeatherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Today screen.
 *
 * Note what is **not** here: no `Context`, no Retrofit, no Room, no `Dispatchers`. Its
 * only collaborators are a use case and a repository interface, both from `domain`, so
 * `TodayViewModelTest` runs on the JVM in milliseconds with hand-written fakes.
 */
@HiltViewModel
class TodayViewModel
@Inject
constructor(
    observeTodayWeather: ObserveTodayWeatherUseCase,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {
    /** Transient, screen-local UI state that no repository owns. */
    private val bannerDismissed = MutableStateFlow(false)
    private val manualRefreshInFlight = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> =
        combine(
            observeTodayWeather().map { it },
            bannerDismissed,
            manualRefreshInFlight,
        ) { today, dismissed, manualRefresh ->
            TodayUiState(
                location = today.location,
                weather = today.weather.data,
                preferences = today.preferences,
                isLoading = today.weather.isLoading,
                isRefreshing = today.weather.isRefreshing || manualRefresh,
                isStale = today.weather.isStale,
                error = today.weather.error,
                hasNoLocation = today.hasNoLocation,
                isBannerDismissed = dismissed,
            )
        }.stateIn(
            scope = viewModelScope,
            // WhileSubscribed with a 5 s grace period: survives a configuration change
            // without re-querying, but stops work when the screen is truly gone.
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = TodayUiState(isLoading = true),
        )

    /** Pull-to-refresh and the Retry button both land here. */
    fun refresh() {
        val location = uiState.value.location ?: return
        viewModelScope.launch {
            manualRefreshInFlight.value = true
            // A new attempt makes a dismissed banner relevant again.
            bannerDismissed.value = false
            weatherRepository.refresh(location)
            manualRefreshInFlight.value = false
        }
    }

    fun dismissBanner() {
        bannerDismissed.value = true
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
