package com.nauhaan.skycast.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nauhaan.skycast.data.local.dao.MetarCacheDao
import com.nauhaan.skycast.data.local.dao.SavedLocationDao
import com.nauhaan.skycast.data.local.dao.WeatherCacheDao
import com.nauhaan.skycast.data.local.entity.CachedForecastReadingEntity
import com.nauhaan.skycast.data.local.entity.CachedMetarEntity
import com.nauhaan.skycast.data.local.entity.CachedWeatherEntity
import com.nauhaan.skycast.data.local.entity.SavedLocationEntity

/**
 * The app's SQLite database, via Room.
 *
 * ## Changing the schema
 *
 * `exportSchema = true` writes a JSON snapshot to `app/schemas/` on every build, and
 * those files are committed. When you change an entity you must:
 *
 * 1. bump [VERSION];
 * 2. add a `Migration` and register it in `DatabaseModule`;
 * 3. add a `MigrationTestHelper` test proving user data survives the upgrade.
 *
 * Never use `fallbackToDestructiveMigration()`. It silently deletes the user's saved locations on
 * upgrade.
 */
@Database(
    entities = [
        SavedLocationEntity::class,
        CachedWeatherEntity::class,
        CachedForecastReadingEntity::class,
        CachedMetarEntity::class,
    ],
    version = SkyCastDatabase.VERSION,
    exportSchema = true,
)
abstract class SkyCastDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao

    abstract fun weatherCacheDao(): WeatherCacheDao

    abstract fun metarCacheDao(): MetarCacheDao

    companion object {
        const val VERSION = 4
        const val NAME = "skycast.db"
    }
}
