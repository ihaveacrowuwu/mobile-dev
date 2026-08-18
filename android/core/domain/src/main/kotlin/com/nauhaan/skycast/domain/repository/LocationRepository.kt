package com.nauhaan.skycast.domain.repository

import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

/**
 * The user's saved places, and geocoding search to add new ones.
 *
 * Saved locations are the app's durable, user-owned state, the clearest
 * demonstration of the persistence requirement. They are read as a [Flow] so that
 * adding a location on the Locations tab immediately updates the Home tab without
 * any manual invalidation.
 */
interface LocationRepository {
    /** All saved locations, ordered by the user's arrangement. Emits on every change. */
    fun observeSavedLocations(): Flow<List<SavedLocation>>

    /** The location shown on the Home tab. `null` only before the user adds their first. */
    fun observePrimaryLocation(): Flow<SavedLocation?>

    suspend fun getById(id: Long): SavedLocation?

    /**
     * Searches OpenWeather's geocoding API.
     *
     * Throws [com.nauhaan.skycast.core.common.AppError] on failure, unlike the
     * weather flows, a search has no cache to fall back on, so a failure is total
     * and the caller must handle it.
     */
    suspend fun search(query: String): List<LocationSearchResult>

    /** Saves a search hit. Returns the new row id. The first location added becomes primary. */
    suspend fun save(result: LocationSearchResult): Long

    suspend fun delete(location: SavedLocation)

    suspend fun setPrimary(location: SavedLocation)

    /** Persists a user-driven reorder of the list. */
    suspend fun reorder(orderedIds: List<Long>)
}
