package com.nauhaan.skycast.ui.metar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.MetarReport
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.MetarRepository
import com.nauhaan.skycast.ui.common.SelectedLocationStore
import com.nauhaan.skycast.ui.common.observeActiveLocation
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

/** Everything the METAR screen renders: one immutable value, one source of truth. */
data class MetarUiState(
    val location: SavedLocation? = null,
    val report: MetarReport? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: AppError? = null,
    val hasNoLocation: Boolean = false,
) {
    /** Blocking spinner **only** when there is genuinely nothing to render. */
    val showsFullScreenLoader: Boolean get() = isLoading && report == null && !hasNoLocation

    /** Blocking error **only** when no cached observation exists to fall back on. */
    val showsFullScreenError: Boolean get() = error != null && report == null && !hasNoLocation

    val showsEmptyState: Boolean get() = hasNoLocation && !isLoading

    val showsContent: Boolean get() = report != null

    /**
     * Non-blocking banner over an existing observation: the refresh failed, or the cache is past its
     * TTL and could not be updated.
     */
    val showsStaleBanner: Boolean get() = showsContent && (error != null || isStale)
}

/**
 * The METAR screen's state.
 *
 * Follows the **primary** location rather than paging like Home does. A METAR belongs to an airport,
 * not to a town, and the nearest airport to two saved places is often the same one, paging between
 * places that show an identical observation would be motion without information.
 */
@HiltViewModel
class MetarViewModel
@Inject
constructor(
    private val metarRepository: MetarRepository,
    locationRepository: LocationRepository,
    selectedLocationStore: SelectedLocationStore,
) : ViewModel() {
    /** See `HomeViewModel.manualRefreshError`: a failed manual refresh is otherwise silent. */
    private val manualRefreshError = MutableStateFlow<AppError?>(null)
    private val manualRefreshInFlight = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MetarUiState> = locationRepository
        // The place selected on Home, not the favourite. See ui/common/SelectedLocationStore.kt: following the
        // favourite meant swiping Home to Malé and still being shown London's airport here.
        .observeActiveLocation(selectedLocationStore)
        .flatMapLatest { location ->
            if (location == null) {
                flowOf(MetarUiState(hasNoLocation = true))
            } else {
                metarRepository.observeNearestMetar(location).combine(
                    manualRefreshInFlight.combine(manualRefreshError) { inFlight, error -> inFlight to error },
                ) { state, (inFlight, manualError) ->
                    MetarUiState(
                        location = location,
                        report = state.data,
                        isLoading = state.isLoading,
                        isRefreshing = state.isRefreshing || inFlight,
                        isStale = state.isStale,
                        error = state.error ?: manualError,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = MetarUiState(isLoading = true),
        )

    fun refresh() {
        val location = uiState.value.location ?: return
        viewModelScope.launch {
            manualRefreshInFlight.value = true
            manualRefreshError.value = metarRepository.refresh(location)
            manualRefreshInFlight.value = false
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
