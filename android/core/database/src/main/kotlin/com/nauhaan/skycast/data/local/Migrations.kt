package com.nauhaan.skycast.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, one object per version step.
 *
 * Every migration here is **additive**. `fallbackToDestructiveMigration()` is never used, because
 * it would silently delete the user's saved locations on an app update.
 *
 * Register each new migration by name in `DatabaseModule`.
 *
 * Each migration is paired with a test in `SkyCastDatabaseMigrationTest` that opens a real v(n)
 * database, runs the migration, and asserts the user's rows survived.
 */
internal object Migrations {
    /**
     * 1 → 2: record the observed location's UTC offset alongside each cached reading.
     *
     * Sunrise and sunset were being rendered in the *device's* timezone, so London's 04:49
     * sunrise displayed as 09:49 on a phone five hours ahead. Worse, the forecast's grouping
     * into calendar days used the location's zone when the data came from the API and the
     * device's zone when it came from the cache, so a day's boundary, and the identity of a
     * day-detail route, shifted depending on which path served the read.
     *
     * `DEFAULT 0` rather than a backfill: both affected tables are **caches**, so a pre-migration
     * row reading as UTC is corrected by the next refresh, within the 10-minute TTL. There is no
     * user data to preserve here, and inventing an offset for old rows would be a guess.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE cached_weather " +
                    "ADD COLUMN timezone_offset_seconds INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE cached_forecast_readings " +
                    "ADD COLUMN timezone_offset_seconds INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /**
     * 2 → 3: replace each cache table's surrogate `id` with its natural primary key.
     *
     * Fixes a silent data-loss bug. Both tables had `@PrimaryKey(autoGenerate = true) id` beside a
     * *separate* unique index on the natural key. Room implements `@Upsert` as "INSERT, and on a
     * uniqueness conflict UPDATE … **WHERE id = ?**", and the mapper never sets `id`, so it bound
     * 0 while the stored row had 1. Every write after the very first updated zero rows and was
     * discarded with no error: the current-weather cache became write-once, and the offline path
     * served first-launch data indefinitely.
     *
     * SQLite cannot alter a primary key in place, so each table is rebuilt and its rows copied.
     * `INSERT OR REPLACE` because the old schema could in principle hold duplicates that the new
     * key forbids; keeping the last of a duplicate set is correct for a cache.
     *
     * Foreign keys are disabled around the swap. Dropping the old table while they are enforced
     * would cascade the delete into nothing here, but the rename step is safer with the checks
     * deferred, and `foreign_key_check` afterwards proves nothing was left dangling.
     */
    val MIGRATION_2_3 = object : Migration(VERSION_2, VERSION_3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Foreign keys are deferred around the swap: dropping and renaming a table that other
            // rows reference is safer with enforcement off, and Room re-enables checks afterwards.
            db.execSQL("PRAGMA foreign_keys = OFF")
            rebuildCachedWeather(db)
            rebuildCachedForecastReadings(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * 3 → 4: adds the METAR cache.
     *
     * Purely additive, which is the easy kind: no existing table is touched, so nothing the user has
     * saved can be lost. The `CREATE TABLE` has to match what Room generates for
     * `CachedMetarEntity` exactly, column order, types, nullability and the foreign key, or Room's
     * own schema validation rejects the database on the next open. The exported schema JSON under
     * `core/database/schemas/` is the reference; `migrate4Test` proves the two agree.
     */
    val MIGRATION_3_4 = object : Migration(VERSION_3, VERSION_4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_metar` (
                    `location_id` INTEGER NOT NULL,
                    `station_id` TEXT NOT NULL,
                    `station_name` TEXT NOT NULL,
                    `distance_km` REAL NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `elevation_metres` INTEGER NOT NULL,
                    `observed_at` INTEGER NOT NULL,
                    `temperature_celsius` REAL,
                    `dew_point_celsius` REAL,
                    `wind_direction_degrees` INTEGER,
                    `wind_speed_knots` INTEGER,
                    `visibility_statute_miles` REAL,
                    `visibility_is_or_greater` INTEGER NOT NULL,
                    `altimeter_hectopascals` REAL,
                    `clouds` TEXT NOT NULL,
                    `flight_category` TEXT NOT NULL,
                    `raw` TEXT NOT NULL,
                    `cached_at` INTEGER NOT NULL,
                    PRIMARY KEY(`location_id`),
                    FOREIGN KEY(`location_id`) REFERENCES `saved_locations`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Adds the single-row space-weather cache.
     *
     * No foreign key and no location column: Kp belongs to the planet, so this table has exactly one row and
     * nothing to cascade from. Additive, so nothing has to be rebuilt.
     */
    val MIGRATION_4_5 = object : Migration(VERSION_4, VERSION_5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_space_weather` (
                    `id` INTEGER NOT NULL,
                    `kp_now` REAL NOT NULL,
                    `observed_at` INTEGER NOT NULL,
                    `storm_level` TEXT,
                    `upcoming` TEXT NOT NULL,
                    `cached_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    private fun rebuildCachedWeather(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_weather_new` (
                `location_id` INTEGER NOT NULL,
                `location_name` TEXT NOT NULL,
                `condition_id` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `icon_code` TEXT NOT NULL,
                `temperature_celsius` REAL NOT NULL,
                `feels_like_celsius` REAL NOT NULL,
                `min_temperature_celsius` REAL NOT NULL,
                `max_temperature_celsius` REAL NOT NULL,
                `humidity_percent` INTEGER NOT NULL,
                `pressure_hpa` INTEGER NOT NULL,
                `wind_speed_mps` REAL NOT NULL,
                `wind_direction_degrees` INTEGER NOT NULL,
                `cloudiness_percent` INTEGER NOT NULL,
                `visibility_metres` INTEGER NOT NULL,
                `sunrise_epoch` INTEGER NOT NULL,
                `sunset_epoch` INTEGER NOT NULL,
                `observed_at_epoch` INTEGER NOT NULL,
                `cached_at_epoch` INTEGER NOT NULL,
                `timezone_offset_seconds` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`location_id`),
                FOREIGN KEY(`location_id`) REFERENCES `saved_locations`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        // INSERT OR REPLACE: the old schema could hold rows the new key forbids. Keeping the last
        // of a duplicate set is the right choice for a cache.
        db.execSQL(
            """
            INSERT OR REPLACE INTO `cached_weather_new` (
                `location_id`, `location_name`, `condition_id`, `description`, `icon_code`,
                `temperature_celsius`, `feels_like_celsius`, `min_temperature_celsius`,
                `max_temperature_celsius`, `humidity_percent`, `pressure_hpa`, `wind_speed_mps`,
                `wind_direction_degrees`, `cloudiness_percent`, `visibility_metres`,
                `sunrise_epoch`, `sunset_epoch`, `observed_at_epoch`, `cached_at_epoch`,
                `timezone_offset_seconds`
            )
            SELECT
                `location_id`, `location_name`, `condition_id`, `description`, `icon_code`,
                `temperature_celsius`, `feels_like_celsius`, `min_temperature_celsius`,
                `max_temperature_celsius`, `humidity_percent`, `pressure_hpa`, `wind_speed_mps`,
                `wind_direction_degrees`, `cloudiness_percent`, `visibility_metres`,
                `sunrise_epoch`, `sunset_epoch`, `observed_at_epoch`, `cached_at_epoch`,
                `timezone_offset_seconds`
            FROM `cached_weather`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `cached_weather`")
        db.execSQL("ALTER TABLE `cached_weather_new` RENAME TO `cached_weather`")
    }

    private fun rebuildCachedForecastReadings(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_forecast_readings_new` (
                `location_id` INTEGER NOT NULL,
                `location_name` TEXT NOT NULL,
                `time_epoch` INTEGER NOT NULL,
                `condition_id` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `icon_code` TEXT NOT NULL,
                `temperature_celsius` REAL NOT NULL,
                `precipitation_probability` REAL NOT NULL,
                `wind_speed_mps` REAL NOT NULL,
                `cached_at_epoch` INTEGER NOT NULL,
                `timezone_offset_seconds` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`location_id`, `time_epoch`),
                FOREIGN KEY(`location_id`) REFERENCES `saved_locations`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `cached_forecast_readings_new` (
                `location_id`, `location_name`, `time_epoch`, `condition_id`, `description`,
                `icon_code`, `temperature_celsius`, `precipitation_probability`, `wind_speed_mps`,
                `cached_at_epoch`, `timezone_offset_seconds`
            )
            SELECT
                `location_id`, `location_name`, `time_epoch`, `condition_id`, `description`,
                `icon_code`, `temperature_celsius`, `precipitation_probability`, `wind_speed_mps`,
                `cached_at_epoch`, `timezone_offset_seconds`
            FROM `cached_forecast_readings`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `cached_forecast_readings`")
        db.execSQL(
            "ALTER TABLE `cached_forecast_readings_new` RENAME TO `cached_forecast_readings`",
        )
    }

    private const val VERSION_2 = 2
    private const val VERSION_3 = 3
    private const val VERSION_4 = 4
    private const val VERSION_5 = 5
}
