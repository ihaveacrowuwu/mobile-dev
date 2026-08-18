package com.nauhaan.skycast.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.nauhaan.skycast.R
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Navigation Compose's `@Serializable` route objects give typed arguments, so the compiler finds
 * every call site when a route changes and a typo is a compile error instead of a runtime crash.
 */
sealed interface Route {
    // ── Top-level tab destinations ─────────────────────────────────────────

    @Serializable
    data object Home : Route

    @Serializable
    data object Metar : Route

    @Serializable
    data object Moon : Route

    @Serializable
    data object Locations : Route

    @Serializable
    data object Settings : Route

    // ── Pushed destinations (arguments are typed properties) ───────────────

    /** Detail for one saved location, pushed from the Locations tab. */
    @Serializable
    data class LocationDetail(val locationId: Long) : Route

    /** One day of the forecast, pushed from a place's detail screen. */
    @Serializable
    data class DayDetail(val locationId: Long, val epochDay: Long) : Route

    /** Geocoding search, pushed from the Locations tab. */
    @Serializable
    data object AddLocation : Route

    /** Attribution and dependency licences, pushed from Settings. See MO4. */
    @Serializable
    data object About : Route
}

/**
 * The five bottom-bar destinations.
 *
 * Selected and unselected icons differ (filled vs outlined) because a filled-only bar
 * gives no visual affordance for which tab is active.
 */
enum class TopLevelDestination(
    val route: Route,
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /**
     * Stable handle for UI tests.
     *
     * Necessary because tab labels are not unique on screen: "METAR" appears both in
     * the bottom bar and as the screen's own heading, so `onNodeWithText("METAR")`
     * matches two nodes and fails. A test tag identifies the bar item unambiguously and
     * survives copy changes.
     */
    val testTag: String,
) {
    HOME(
        route = Route.Home,
        labelRes = R.string.tab_home,
        contentDescriptionRes = R.string.tab_home_description,
        selectedIcon = Icons.Filled.WbSunny,
        unselectedIcon = Icons.Outlined.WbSunny,
        testTag = "tab_home",
    ),
    METAR(
        route = Route.Metar,
        labelRes = R.string.tab_metar,
        contentDescriptionRes = R.string.tab_metar_description,
        selectedIcon = Icons.Filled.FlightTakeoff,
        unselectedIcon = Icons.Outlined.FlightTakeoff,
        testTag = "tab_metar",
    ),
    MOON(
        route = Route.Moon,
        labelRes = R.string.tab_moon,
        contentDescriptionRes = R.string.tab_moon_description,
        selectedIcon = Icons.Filled.Bedtime,
        unselectedIcon = Icons.Outlined.Bedtime,
        testTag = "tab_moon",
    ),
    LOCATIONS(
        route = Route.Locations,
        labelRes = R.string.tab_locations,
        contentDescriptionRes = R.string.tab_locations_description,
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn,
        testTag = "tab_locations",
    ),
    SETTINGS(
        route = Route.Settings,
        labelRes = R.string.tab_settings,
        contentDescriptionRes = R.string.tab_settings_description,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "tab_settings",
    ),
}
