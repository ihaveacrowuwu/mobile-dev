package com.nauhaan.skycast.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nauhaan.skycast.data.local.entity.CachedSpaceWeatherEntity
import kotlinx.coroutines.flow.Flow

/**
 * The single cached space-weather row.
 *
 * No location parameter anywhere, which is the whole difference from [MetarCacheDao]: Kp is a property of the
 * planet, not of a place.
 */
@Dao
interface SpaceWeatherCacheDao {
    @Query("SELECT * FROM cached_space_weather WHERE id = :id LIMIT 1")
    fun observe(id: Int = CachedSpaceWeatherEntity.SINGLETON_ID): Flow<CachedSpaceWeatherEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedSpaceWeatherEntity)

    @Query("DELETE FROM cached_space_weather")
    suspend fun clear()
}
