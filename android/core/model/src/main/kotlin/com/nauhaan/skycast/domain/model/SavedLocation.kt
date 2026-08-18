package com.nauhaan.skycast.domain.model

/**
 * A place the user has chosen to track.
 *
 * Saved locations are user-owned data: they are never evicted by a cache policy
 * and they survive reinstall-free app restarts, which is the core of the
 * persistence requirement.
 */
data class SavedLocation(
    val id: Long,
    val name: String,
    val countryCode: String,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
    /** Position in the user's list; lets them reorder without touching other fields. */
    val sortOrder: Int = 0,
    /** Exactly one saved location is primary, it is what the Home tab shows. */
    val isPrimary: Boolean = false,
) {
    /** e.g. "London, England, GB", falls back gracefully when [state] is absent. */
    val displayName: String
        get() = listOfNotNull(name, state, countryCode).joinToString(", ")

    /** e.g. "London, GB", for tight spaces such as a tab title. */
    val shortDisplayName: String get() = "$name, $countryCode"
}

/** A geocoding search hit, before the user commits to saving it. */
data class LocationSearchResult(
    val name: String,
    val countryCode: String,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String
        get() = listOfNotNull(name, state, countryCode).joinToString(", ")

    /**
     * Stable identity for list keys. Coordinates identify a place uniquely; the geocoding API
     * returns no id for search hits, and the name alone is not unique, "London, GB" and
     * "London, CA" both come back for the query "London".
     *
     * Mirrors `LocationSearchResult.id` on iOS.
     */
    val id: String get() = "$latitude,$longitude"
}
