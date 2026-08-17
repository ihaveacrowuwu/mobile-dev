package com.nauhaan.skycast.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the saved-locations list. */
data class LocationsUiState(val locations: List<SavedLocation> = emptyList(), val isLoading: Boolean = true) {
    val isEmpty: Boolean get() = locations.isEmpty() && !isLoading
}

/** State for the search-and-add screen. */
data class AddLocationUiState(
    val query: String = "",
    val results: List<LocationSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: AppError? = null,
    /** Set briefly after a save so the screen can pop itself. */
    val savedLocationName: String? = null,
) {
    /** "No matches" is only meaningful once a real query has actually been run. */
    val showsNoResults: Boolean
        get() = results.isEmpty() && !isSearching && error == null && query.trim().length >= MIN_QUERY

    val showsPrompt: Boolean get() = query.trim().length < MIN_QUERY && results.isEmpty()

    companion object {
        /** Matches the guard in `LocationRepositoryImpl.search`. */
        const val MIN_QUERY = 2
    }
}

/**
 * The Locations tab: the user's saved places.
 *
 * Reads straight from the Room-backed `Flow`, so adding a place on the search screen updates
 * this list, and the Today tab, with no manual invalidation.
 */
@HiltViewModel
class LocationsViewModel
@Inject
constructor(private val locationRepository: LocationRepository) : ViewModel() {
    val uiState: StateFlow<LocationsUiState> =
        locationRepository
            .observeSavedLocations()
            .map { locations -> LocationsUiState(locations = locations, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = LocationsUiState(),
            )

    fun delete(location: SavedLocation) = viewModelScope.launch {
        locationRepository.delete(location)
    }

    fun setPrimary(location: SavedLocation) = viewModelScope.launch {
        locationRepository.setPrimary(location)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Search-and-add.
 *
 * The query is **debounced** before it reaches the network. OpenWeather's free tier allows 60
 * calls a minute; searching on every keystroke would exhaust that in seconds, so this is a
 * quota constraint rather than a nicety.
 */
@HiltViewModel
class AddLocationViewModel
@Inject
constructor(private val locationRepository: LocationRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<LocationSearchResult>>(emptyList())
    private val isSearching = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val savedName = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AddLocationUiState> =
        combine(query, results, isSearching, error, savedName) { q, r, searching, err, saved ->
            AddLocationUiState(
                query = q,
                results = r,
                isSearching = searching,
                error = err,
                savedLocationName = saved,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = AddLocationUiState(),
        )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchJob = viewModelScope.launch {
        query
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .collect { raw -> runSearch(raw) }
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
        // Clear a stale error as soon as the user edits, so the message never outlives its cause.
        error.value = null
        if (newQuery.trim().length < AddLocationUiState.MIN_QUERY) {
            results.value = emptyList()
        }
    }

    fun save(result: LocationSearchResult) = viewModelScope.launch {
        try {
            locationRepository.save(result)
            savedName.value = result.name
        } catch (appError: AppError) {
            error.value = appError
        }
    }

    /** Called after the screen has acted on [AddLocationUiState.savedLocationName]. */
    fun consumeSavedEvent() {
        savedName.value = null
    }

    private suspend fun runSearch(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.length < AddLocationUiState.MIN_QUERY) {
            results.value = emptyList()
            isSearching.value = false
            return
        }
        isSearching.value = true
        try {
            results.value = locationRepository.search(trimmed)
            error.value = null
        } catch (appError: AppError) {
            results.value = emptyList()
            error.value = appError
        } finally {
            isSearching.value = false
        }
    }

    override fun onCleared() {
        searchJob.cancel()
        super.onCleared()
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /**
         * 400 ms is long enough that a fast typist triggers one request rather than eight, and
         * short enough that the list still feels responsive.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
