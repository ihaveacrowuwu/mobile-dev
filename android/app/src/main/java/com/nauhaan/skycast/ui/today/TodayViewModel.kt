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
import kotlinx.coroutines.flow.first
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
    private val observeTodayWeather: ObserveTodayWeatherUseCase,
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

    /**
     * Which saved place is on screen.
     *
     * Screen state, not stored state: the user's *primary* location is a persisted preference and
     * lives in the database, while "the page I happened to swipe to" belongs to this session only.
     * Seeded from the primary so the app opens where the user expects.
     */
    private val selectedIndex = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            selectedIndex.value = observeTodayWeather.primaryIndex().first()
        }
    }

    val uiState: StateFlow<TodayUiState> =
        combine(
            observeTodayWeather(),
            selectedIndex,
            bannerDismissed,
            manualRefreshInFlight,
            manualRefreshError,
        ) { board, index, dismissed, manualRefresh, refreshError ->
            TodayUiState(
                pages = board.entries,
                // Clamped rather than trusted: deleting the last location while it is on screen
                // would otherwise leave the index pointing past the end of the list.
                selectedIndex = index.coerceIn(0, (board.entries.size - 1).coerceAtLeast(0)),
                preferences = board.preferences,
                isLoading = board.entries.any { it.weather.isLoading },
                isRefreshing = board.entries.any { it.weather.isRefreshing } || manualRefresh,
                hasNoLocation = board.hasNoLocation,
                isBannerDismissed = dismissed,
                refreshError = refreshError,
            )
        }.stateIn(
            scope = viewModelScope,
            // WhileSubscribed with a 5 s grace period: survives a configuration change
            // without re-querying, but stops work when the screen is truly gone.
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = TodayUiState(isLoading = true),
        )

    /** Called when the user swipes the pager or picks a place from the menu. */
    fun selectPage(index: Int) {
        selectedIndex.value = index
    }

    /** Pull-to-refresh and the Retry button both land here. */
    fun refresh() {
        val location = uiState.value.location ?: return
        viewModelScope.launch {
            manualRefreshInFlight.value = true
            // A new attempt makes a dismissed banner relevant again.
            bannerDismissed.value = false
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
