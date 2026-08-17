package com.nauhaan.skycast.testing

import com.nauhaan.skycast.core.common.AppError
import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.core.common.NetworkMonitor
import com.nauhaan.skycast.domain.model.Forecast
import com.nauhaan.skycast.domain.model.LocationSearchResult
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.model.TemperatureUnit
import com.nauhaan.skycast.domain.model.ThemeMode
import com.nauhaan.skycast.domain.model.UserPreferences
import com.nauhaan.skycast.domain.model.Weather
import com.nauhaan.skycast.domain.model.WeatherCondition
import com.nauhaan.skycast.domain.model.WindSpeedUnit
import com.nauhaan.skycast.domain.repository.DataState
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset

/**
 * Hand-written test doubles.
 *
 * These are **fakes**, not mocks: each one is a tiny working implementation whose state
 * a test can drive directly. Fakes read better than `every { ... } returns ...` stacks
 * and they cannot drift out of sync with the interface, because the compiler checks them.
 *
 * They are also the payoff of the layering: because view models depend only on `domain`
 * interfaces, these fakes are all a view model test needs. No Robolectric, no device, no
 * network.
 */

class FakeWeatherRepository : WeatherRepository {
    val currentWeather = MutableStateFlow(DataState<Weather>())
    val forecast = MutableStateFlow(DataState<Forecast>())

    /** Returned by [refresh]; set to a non-null value to simulate a failed refresh. */
    var refreshError: AppError? = null

    var refreshCallCount = 0
        private set
    var clearCacheCallCount = 0
        private set

    override fun observeCurrentWeather(location: SavedLocation): Flow<DataState<Weather>> = currentWeather

    override fun observeForecast(location: SavedLocation): Flow<DataState<Forecast>> = forecast

    override suspend fun refresh(location: SavedLocation): AppError? {
        refreshCallCount++
        return refreshError
    }

    override suspend fun clearCache() {
        clearCacheCallCount++
    }
}

class FakeLocationRepository : LocationRepository {
    val savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val primaryLocation = MutableStateFlow<SavedLocation?>(null)

    /** Thrown by [search] when set, so error paths are testable. */
    var searchError: AppError? = null
    var searchResults: List<LocationSearchResult> = emptyList()

    override fun observeSavedLocations(): Flow<List<SavedLocation>> = savedLocations

    override fun observePrimaryLocation(): Flow<SavedLocation?> = primaryLocation

    override suspend fun getById(id: Long): SavedLocation? = savedLocations.value.firstOrNull { it.id == id }

    override suspend fun search(query: String): List<LocationSearchResult> {
        searchError?.let { throw it }
        return searchResults
    }

    override suspend fun save(result: LocationSearchResult): Long {
        val id = (savedLocations.value.maxOfOrNull { it.id } ?: 0L) + 1
        val location =
            SavedLocation(
                id = id,
                name = result.name,
                countryCode = result.countryCode,
                state = result.state,
                latitude = result.latitude,
                longitude = result.longitude,
                sortOrder = savedLocations.value.size,
                isPrimary = savedLocations.value.isEmpty(),
            )
        savedLocations.value = savedLocations.value + location
        if (location.isPrimary) primaryLocation.value = location
        return id
    }

    override suspend fun delete(location: SavedLocation) {
        savedLocations.value = savedLocations.value - location
        if (primaryLocation.value == location) {
            primaryLocation.value = savedLocations.value.firstOrNull()
        }
    }

    override suspend fun setPrimary(location: SavedLocation) {
        primaryLocation.value = location
    }

    override suspend fun reorder(orderedIds: List<Long>) {
        savedLocations.value = orderedIds.mapNotNull { id ->
            savedLocations.value.firstOrNull { it.id == id }
        }
    }
}

class FakeSettingsRepository : SettingsRepository {
    val preferences = MutableStateFlow(UserPreferences())

    override fun observePreferences(): Flow<UserPreferences> = preferences

    override suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        preferences.value = preferences.value.copy(temperatureUnit = unit)
    }

    override suspend fun setWindSpeedUnit(unit: WindSpeedUnit) {
        preferences.value = preferences.value.copy(windSpeedUnit = unit)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        preferences.value = preferences.value.copy(themeMode = mode)
    }

    override suspend fun setUseDynamicColour(enabled: Boolean) {
        preferences.value = preferences.value.copy(useDynamicColour = enabled)
    }

    override suspend fun reset() {
        preferences.value = UserPreferences()
    }
}

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online.map { it }
}

/** Routes every dispatcher to the test dispatcher so coroutines are deterministic. */
class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

// ── Sample data ────────────────────────────────────────────────────────────

/** A fixed instant, so no test depends on the wall clock. */
val TestInstant: Instant = Instant.parse("2026-08-04T12:00:00Z")

fun sampleLocation(id: Long = 1L, name: String = "London", isPrimary: Boolean = true): SavedLocation = SavedLocation(
    id = id,
    name = name,
    countryCode = "GB",
    state = "England",
    latitude = 51.5074,
    longitude = -0.1278,
    isPrimary = isPrimary,
)

fun sampleWeather(
    locationId: Long = 1L,
    temperatureCelsius: Double = 22.0,
    cachedAt: Instant = TestInstant,
    // Overridable so a test can assert that sunrise/sunset render in the *location's* zone
    // rather than the JVM's, which is the one thing a UTC-only fixture cannot distinguish.
    zoneOffset: ZoneOffset = ZoneOffset.UTC,
): Weather =
    Weather(
        locationId = locationId,
        locationName = "London",
        condition = WeatherCondition.CLEAR,
        description = "Clear sky",
        iconCode = "01d",
        temperatureCelsius = temperatureCelsius,
        feelsLikeCelsius = temperatureCelsius - 1,
        minTemperatureCelsius = temperatureCelsius - 4,
        maxTemperatureCelsius = temperatureCelsius + 3,
        humidityPercent = 60,
        pressureHpa = 1013,
        windSpeedMetresPerSecond = 4.5,
        windDirectionDegrees = 220,
        cloudinessPercent = 5,
        visibilityMetres = 10_000,
        sunrise = TestInstant.minusSeconds(6 * 60 * 60),
        sunset = TestInstant.plusSeconds(8 * 60 * 60),
        observedAt = TestInstant,
        cachedAt = cachedAt,
        zoneOffset = zoneOffset,
    )
