package com.nauhaan.skycast.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nauhaan.skycast.data.local.entity.CachedMetarEntity
import kotlinx.coroutines.flow.Flow

/** The nearest airport's METAR per saved location, one row each. */
@Dao
interface MetarCacheDao {
    @Query("SELECT * FROM cached_metar WHERE location_id = :locationId")
    fun observe(locationId: Long): Flow<CachedMetarEntity?>

    /**
     * Replaces the row for this location.
     *
     * `@Upsert` with `location_id` as the primary key, which is the shape that took two attempts to
     * get right for the weather cache: with a surrogate key the generated UPDATE matched on an id
     * that was always 0, so every write after the first was silently dropped. Keyed by the natural
     * key there is nothing to get wrong.
     */
    @Upsert
    suspend fun upsert(metar: CachedMetarEntity)

    @Query("DELETE FROM cached_metar")
    suspend fun clear()
}
