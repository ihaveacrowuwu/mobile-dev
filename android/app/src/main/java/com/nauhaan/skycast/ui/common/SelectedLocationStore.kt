package com.nauhaan.skycast.ui.common

import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which place the rest of the app is talking about, for this session.
 *
 * The favourite means exactly two things:
 *
 * 1. which Home page the app opens on at launch, and
 * 2. which place's weather colours the background on the other screens.
 *
 * The other tabs follow **the page you are looking at on Home**, held here. Swipe to Malé, open
 * METAR, and it is Malé's airport.
 *
 * Session state rather than a stored preference. `@Singleton` rather than a `ViewModel` because
 * three separately scoped view models read it, and the value has to outlive any one of them.
 */
@Singleton
class SelectedLocationStore @Inject constructor() {
    private val _selectedLocationId = MutableStateFlow<Long?>(null)

    /** `null` until Home has resolved its first page. */
    val selectedLocationId: StateFlow<Long?> = _selectedLocationId.asStateFlow()

    fun select(locationId: Long) {
        _selectedLocationId.value = locationId
    }
}

/**
 * The place the non-Home tabs should show.
 *
 * Falls back in this order:
 *
 * 1. the place selected on Home, when there is one;
 * 2. the favourite, which covers opening METAR or Moon before Home has been on screen to publish a
 *    selection;
 * 3. the first saved place, so a database with no favourite still shows something;
 * 4. `null`, meaning nothing is saved yet.
 */
fun LocationRepository.observeActiveLocation(store: SelectedLocationStore): Flow<SavedLocation?> =
    combine(observeSavedLocations(), store.selectedLocationId) { locations, selectedId ->
        locations.firstOrNull { it.id == selectedId }
            ?: locations.firstOrNull { it.isPrimary }
            ?: locations.firstOrNull()
    }
