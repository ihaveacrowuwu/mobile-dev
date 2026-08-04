package com.nauhaan.skycast.data.repository

import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.data.local.dao.SavedLocationDao
import com.nauhaan.skycast.data.mapper.WeatherMapper.toDomain
import com.nauhaan.skycast.data.mapper.WeatherMapper.toEntity
import com.nauhaan.skycast.data.remote.ErrorMapper
import com.nauhaan.skycast.data.remote.OpenWeatherApi
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved locations (Room) plus geocoding search (network).
 *
 * Unlike weather, search results are **not** cached: a place search is cheap, its
 * results are transient, and caching them would show the user stale matches for a
 * query they have since changed.
 */
@Singleton
class LocationRepositoryImpl
@Inject
constructor(
    private val dao: SavedLocationDao,
    private val api: OpenWeatherApi,
    private val dispatchers: DispatcherProvider,
) : LocationRepository {
    override fun observeSavedLocations(): Flow<List<SavedLocation>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observePrimaryLocation(): Flow<SavedLocation?> = dao.observePrimary().map { it?.toDomain() }

    override suspend fun getById(id: Long): SavedLocation? = withContext(dispatchers.io) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun search(query: String): List<LocationSearchResult> = withContext(dispatchers.io) {
        val trimmed = query.trim()
        // Guard here rather than in the view model so every caller is protected
        // from burning API quota on an empty or one-character query.
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()

        try {
            api.searchLocations(trimmed).map { it.toDomain() }
        } catch (throwable: Throwable) {
            throw ErrorMapper.map(throwable)
        }
    }

    override suspend fun save(result: LocationSearchResult): Long = withContext(dispatchers.io) {
        try {
            // The very first location the user adds becomes primary, so the Today tab
            // is never left with nothing to show.
            val isFirst = dao.count() == 0
            val id = dao.insert(result.toEntity(sortOrder = dao.nextSortOrder(), isPrimary = isFirst))
            if (isFirst) dao.setPrimaryExclusively(id)
            id
        } catch (throwable: Throwable) {
            throw ErrorMapper.map(throwable)
        }
    }

    override suspend fun delete(location: SavedLocation) = withContext(dispatchers.io) {
        // Cached weather rows cascade automatically via the entity's foreign key.
        dao.deleteAndReassignPrimary(location.toEntity())
    }

    override suspend fun setPrimary(location: SavedLocation) = withContext(dispatchers.io) {
        dao.setPrimaryExclusively(location.id)
    }

    override suspend fun reorder(orderedIds: List<Long>) = withContext(dispatchers.io) {
        dao.applyOrder(orderedIds)
    }

    private companion object {
        /** Below this length OpenWeather's geocoder returns noise, not matches. */
        const val MIN_QUERY_LENGTH = 2
    }
}
