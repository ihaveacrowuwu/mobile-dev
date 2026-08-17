package com.nauhaan.skycast.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.core.common.AppError
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

    /**
     * The error from the most recent **manual** refresh.
     *
     * Separate from the observed `DataState.error` because a pull-to-refresh that fails does not
     * necessarily make the stream re-emit: with a cache inside its TTL there is nothing new to
     * publish, so the failure would be swallowed and the user would see the spinner vanish with
     * no explanation. Cleared as soon as an attempt succeeds.
     */
    private val manualRefreshError = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<TodayUiState> =
        combine(
            observeTodayWeather().map { it },
            bannerDismissed,
            manualRefreshInFlight,
            manualRefreshError,
        ) { today, dismissed, manualRefresh, refreshError ->
            TodayUiState(
                location = today.location,
                weather = today.weather.data,
                preferences = today.preferences,
                isLoading = today.weather.isLoading,
                isRefreshing = today.weather.isRefreshing || manualRefresh,
                isStale = today.weather.isStale,
                // The stream's error wins when it has one; otherwise a failed manual attempt.
                error = today.weather.error ?: refreshError,
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
 iOS surfaced it; this
            // is the parity fix.
            manualRefreshError.value = weatherRepository.refresh(location)
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
