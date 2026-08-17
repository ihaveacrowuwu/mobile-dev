package com.nauhaan.skycast.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests, run against real SQLite.
 *
 * `MigrationTestHelper` reads the committed schema JSON from `core/database/schemas/`, which the
 * module's `build.gradle.kts` registers as an androidTest asset source.
 *
 * Each test asserts the thing that would break:
 *
 * - **Saved locations survive.** They are the user's own data.
 * - **The v3 rebuild really changes the key.** 2 → 3 rebuilds both cache tables to swap a surrogate
 *   `id` for the natural key. Asserting only that the migration "ran without throwing" would pass
 *   even if the new table came out with the old shape.
 */
@RunWith(AndroidJUnit4::class)
class SkyCastDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SkyCastDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2AddsTheTimeZoneColumnAndKeepsSavedLocations() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO saved_locations
                    (id, name, country_code, state, latitude, longitude, sort_order, is_primary)
                VALUES (1, 'London', 'GB', 'England', 51.5074, -0.1278, 0, 1)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        migrated.query("SELECT name, is_primary FROM saved_locations").use { cursor ->
            assertTrue("the user's saved location must survive the upgrade", cursor.moveToFirst())
            assertEquals("London", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        assertTrue(
            "cached_weather must gain timezone_offset_seconds",
            columnNames(migrated, "cached_weather").contains("timezone_offset_seconds"),
        )
        assertTrue(
            "cached_forecast_readings must gain timezone_offset_seconds",
            columnNames(migrated, "cached_forecast_readings").contains("timezone_offset_seconds"),
        )
    }

    @Test
    fun migrate2To3SwapsTheSurrogateKeyForTheNaturalOneAndKeepsCachedRows() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO saved_locations
                    (id, name, country_code, state, latitude, longitude, sort_order, is_primary)
                VALUES (1, 'London', 'GB', 'England', 51.5074, -0.1278, 0, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO cached_weather (
                    id, location_id, location_name, condition_id, description, icon_code,
                    temperature_celsius, feels_like_celsius, min_temperature_celsius,
                    max_temperature_celsius, humidity_percent, pressure_hpa, wind_speed_mps,
                    wind_direction_degrees, cloudiness_percent, visibility_metres, sunrise_epoch,
                    sunset_epoch, observed_at_epoch, cached_at_epoch, timezone_offset_seconds
                ) VALUES (
                    1, 1, 'London', 0, 'Clear sky', '01d',
                    22.0, 21.0, 18.0, 24.0, 60, 1013, 4.5, 220, 5, 10000, 1000, 2000, 1500, 1500,
                    3600
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            Migrations.MIGRATION_2_3,
        )

        // The surrogate key is gone and the natural one has taken its place. Without this
        // assertion the test would pass even if the rebuilt table kept the old shape, which is
        // the entire point of the migration.
        val columns = columnNames(migrated, "cached_weather")
        assertTrue("the surrogate id must be dropped", !columns.contains("id"))
        assertEquals(listOf("location_id"), primaryKeyColumns(migrated, "cached_weather"))
        assertEquals(
            listOf("location_id", "time_epoch"),
            primaryKeyColumns(migrated, "cached_forecast_readings"),
        )

        // And the cached row came across, offset intact.
        migrated
            .query("SELECT location_name, temperature_celsius, timezone_offset_seconds FROM cached_weather")
            .use { cursor ->
                assertTrue("the cached reading must be copied across", cursor.moveToFirst())
                assertEquals("London", cursor.getString(0))
                assertEquals(22.0, cursor.getDouble(1), 0.001)
                assertEquals(3_600, cursor.getInt(2))
            }
    }

    @Test
    fun migrate1To3RunsBothStepsInSequence() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO saved_locations
                    (id, name, country_code, state, latitude, longitude, sort_order, is_primary)
                VALUES (1, 'Malé', 'MV', NULL, 4.1748, 73.5089, 0, 1)
                """.trimIndent(),
            )
        }

        // A user upgrading from the first release skips v2 entirely, so the chain matters as much
        // as each individual step.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
        )

        migrated.query("SELECT name FROM saved_locations").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Non-ASCII intact: a rebuild that mangled the encoding would be invisible in an
            // ASCII-only fixture.
            assertEquals("Malé", cursor.getString(0))
        }
    }

    private fun columnNames(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }

    /** Primary-key columns, in key order, `pk` is 1-based and 0 for non-key columns. */
    private fun primaryKeyColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            buildList {
                while (cursor.moveToNext()) {
                    val position = cursor.getInt(pkIndex)
                    if (position > 0) add(position to cursor.getString(nameIndex))
                }
            }.sortedBy { it.first }.map { it.second }
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
