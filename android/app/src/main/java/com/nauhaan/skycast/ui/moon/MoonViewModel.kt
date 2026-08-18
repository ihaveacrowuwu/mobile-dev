package com.nauhaan.skycast.ui.moon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nauhaan.skycast.domain.model.MoonCalculator
import com.nauhaan.skycast.domain.model.MoonSnapshot
import com.nauhaan.skycast.domain.model.SavedLocation
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Everything the Moon screen renders.
 *
 * The phase, the distance and the upcoming phases are computed from the clock, so there is **no
 * error case, no offline case and no stale case**.
 *
 * [location] is nullable: only moonrise and moonset depend on where you are, so the screen still
 * renders before any place has been saved and simply hides one card.
 */
data class MoonUiState(
    val snapshot: MoonSnapshot? = null,
    val location: SavedLocation? = null,
    /**
     * The place's own zone, so "moonrise 20:47" is 20:47 *there*, which is the rule the sun times follow.
     * Falls back to the device's zone until the cached weather carrying the offset has been read.
     */
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** True only in the moments before the first computation lands. */
    val showsLoader: Boolean get() = snapshot == null

    /** Rise and set need a place. Everything else does not. */
    val showsRiseAndSet: Boolean get() = location != null && snapshot?.moonrise != null
}

/**
 * The Moon screen's state.
 *
 * Recomputes every minute as well as on collection: the illuminated fraction visibly moves over an
 * evening, and a screen showing a stale sky is the kind of small wrongness this app has been careful
 * about elsewhere. A minute, not a second: the smallest quantity on screen is a whole minute of
 * moonrise, so a faster tick would burn battery redrawing identical pixels.
 */
@HiltViewModel
class MoonViewModel
@Inject
constructor(locationRepository: LocationRepository, weatherRepository: WeatherRepository) :
    ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MoonUiState> = locationRepository
        .observePrimaryLocation()
        .flatMapLatest { location ->
            if (location == null) {
                // Still worth a screen: everything but rise and set is location-independent.
                ticker().map { now -> snapshotFor(now, location = null, zone = ZoneId.systemDefault()) }
            } else {
                // The weather is read **only** for the location's UTC offset. The cache satisfies it
                // without a request, and a failure needs no handling: the fallback is the device's
                // zone, which is right for the common case of looking at the weather where you are.
                weatherRepository
                    .observeCurrentWeather(location)
                    .map { state -> state.data?.zoneOffset as ZoneId? ?: ZoneId.systemDefault() }
                    .combine(ticker()) { zone, now -> snapshotFor(now, location, zone) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = MoonUiState(),
        )

    private fun snapshotFor(now: Instant, location: SavedLocation?, zone: ZoneId): MoonUiState = MoonUiState(
        snapshot = MoonCalculator.snapshot(
            instant = now,
            // Greenwich when no place is saved: the phase, the distance and the upcoming phases do
            // not depend on the observer at all, and the two figures that do are hidden then.
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            zone = zone,
        ),
        location = location,
        zone = zone,
    )

    /** Emits immediately, then once a minute. */
    private fun ticker(): Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            kotlinx.coroutines.delay(TICK_INTERVAL)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        val TICK_INTERVAL = 1.minutes
    }
}
