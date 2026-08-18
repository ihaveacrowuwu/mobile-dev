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
    /**
     * Exactly one saved location is the favourite.
     *
     * It does two things and no more: it decides which Home page the app opens on at launch, and it
     * supplies the background colour the other screens are painted with.
     */
    val isPrimary: Boolean = false,
) {
    /** e.g. "London, England, GB", falls back gracefully when [state] is absent. */
    val displayName: String
        get() = listOfNotNull(name, state, countryCode).joinToString(", ")

    /** e.g. "London, GB", for tight spaces such as a tab title. */
    val shortDisplayName: String get() = "$name, $countryCode"

    companion object {
        /**
         * How many places can be saved.
         *
         * A cap rather than no limit, for reasons that are about the app working rather than about
         * storage: every saved place is a page on Home and a live weather subscription, and the free
         * OpenWeather tier allows 60 calls a minute across the whole app. Ten places refreshing is
         * comfortable; fifty would be a rate-limit error the user cannot diagnose. Ten is also about as
         * many pages as anyone can usefully swipe through.
         */
        const val MAX_SAVED = 10

        /**
         * Whether another place can be saved when [currentCount] are already stored.
         *
         * A function rather than each caller writing `count < MAX_SAVED` itself. Two places need this
         * decision, the repository, which enforces it, and the Locations screen, which hides the Add
         * button, and a `<` in one with a `<=` in the other is a bug that shows up as a button that
         * leads only to an error. Here it is one comparison, tested once.
         */
        fun canSaveAnother(currentCount: Int): Boolean = currentCount < MAX_SAVED
    }
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
