package com.nauhaan.skycast.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nauhaan.skycast.data.local.entity.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations WHERE is_primary = 1 LIMIT 1")
    fun observePrimary(): Flow<SavedLocationEntity?>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getById(id: Long): SavedLocationEntity?

    @Query("SELECT COUNT(*) FROM saved_locations")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM saved_locations")
    suspend fun nextSortOrder(): Int

    /**
     * The unique (latitude, longitude) index makes re-adding the same place a no-op
     * update rather than a duplicate row or a crash.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity): Long

    @Upsert
    suspend fun upsert(location: SavedLocationEntity)

    @Delete
    suspend fun delete(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations")
    suspend fun deleteAll()

    @Query("UPDATE saved_locations SET is_primary = 0")
    suspend fun clearPrimaryFlags()

    @Query("UPDATE saved_locations SET is_primary = 1 WHERE id = :id")
    suspend fun markPrimary(id: Long)

    @Query("UPDATE saved_locations SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    /**
     * Makes [id] the only primary location.
     *
     * `@Transaction` is essential: without it a crash between the two statements
     * would leave the database with zero primary locations and a permanently empty
     * Today screen.
     */
    @Transaction
    suspend fun setPrimaryExclusively(id: Long) {
        clearPrimaryFlags()
        markPrimary(id)
    }

    /** Applies a user reorder atomically so the list never renders half-sorted. */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    /**
     * Ensures exactly one primary location survives a deletion: if the deleted row
     * was primary, the first remaining location is promoted.
     */
    @Transaction
    suspend fun deleteAndReassignPrimary(location: SavedLocationEntity) {
        delete(location)
        if (!location.isPrimary) return
        firstByOrder()?.let { setPrimaryExclusively(it.id) }
    }

    @Query("SELECT * FROM saved_locations ORDER BY sort_order ASC, name ASC LIMIT 1")
    suspend fun firstByOrder(): SavedLocationEntity?
}
