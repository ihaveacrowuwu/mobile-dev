package com.nauhaan.skycast.data.repository

import android.util.Log
import com.nauhaan.skycast.core.common.AppConfiguration
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds a couple of saved locations on first launch, **debug builds only**.
 *
 * ## Purpose
 *
 * Until the Locations feature lands there is no way to add a place through the UI, so a fresh
 * install shows only the "No locations yet" empty state and the data layer, repositories,
 * cache, mappers, offline banner, unit conversion, is unreachable in a running app despite
 * being fully implemented and tested.
 *
 * Seeding makes all of that demonstrable and screenshottable now. It is a development
 * convenience, not a feature.
 *
 * ## Delete this when the Locations feature ships
 *
 * Once a user can add a place, an app that invents locations for them is a bug. Remove this
 * class, its call site in `MainViewModel`, and the `debug_seed_*` strings.
 *
 * ## Safety
 *
 * Two independent guards:
 *
 * 1. [AppConfiguration.isDebug] wraps `BuildConfig.DEBUG`, a compile-time constant. In release
 *    the check is statically false and R8 strips the whole body.
 * 2. It only ever writes when the table is **empty**, so it cannot duplicate rows, cannot
 *    overwrite a location the user added, and is safe to call on every launch.
 */
@Singleton
class DebugLocationSeeder @Inject constructor(private val locationRepository: LocationRepository) {
    /**
     * Inserts [SAMPLE_LOCATIONS] if, and only if, no locations exist yet.
     *
     * A failure must never stop the app starting, since the empty state it falls back to is a
     * perfectly valid screen, but it is **logged loudly**.
     */
    suspend fun seedIfEmpty() {
        if (!AppConfiguration.isDebug) return

        try {
            val existing = locationRepository.observeSavedLocations().first()
            if (existing.isNotEmpty()) return

            SAMPLE_LOCATIONS.forEach { location ->
                val id = locationRepository.save(location)
                Log.i(TAG, "seedIfEmpty: saved ${location.name} as id=$id")
            }
        } catch (cancellation: CancellationException) {
            // Structured concurrency: cancellation must propagate, never be logged as a
            // failure. Caught first so the general handler below cannot swallow it.
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "seedIfEmpty failed", throwable)
        }
    }

    private companion object {
        const val TAG = "DebugLocationSeeder"

        /**
         * Chosen for contrast rather than convenience: a mid-latitude maritime climate and an
         * equatorial one, in different timezones and hemispheres of the prime meridian. That
         * exercises the location-timezone forecast grouping and the day/night icon logic, which
         * two nearby cities would not.
         *
         * The first entry becomes primary and is what the Today tab shows.
         */
        val SAMPLE_LOCATIONS = listOf(
            LocationSearchResult(
                name = "London",
                countryCode = "GB",
                state = "England",
                latitude = 51.5074,
                longitude = -0.1278,
            ),
            LocationSearchResult(
                name = "Malé",
                countryCode = "MV",
                state = null,
                latitude = 4.1748,
                longitude = 73.5089,
            ),
        )
    }
}
