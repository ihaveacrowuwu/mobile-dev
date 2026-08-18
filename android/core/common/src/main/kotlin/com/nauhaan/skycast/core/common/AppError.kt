package com.nauhaan.skycast.core.common

/**
 * The **only** error type allowed to cross the repository boundary.
 *
 * Framework exceptions (`IOException`, `HttpException`, `SerializationException`,
 * `SQLiteException`) are caught inside the data layer and translated into one of
 * these cases. View models therefore never import a networking or database type,
 * which is what keeps them unit-testable without a device.
 *
 * A sealed class, not an enum, because some cases carry data.
 */
sealed class AppError : Exception() {
    /** No usable network connection at all. */
    data object Offline : AppError()

    /** The request was made but did not complete in time. */
    data object Timeout : AppError()

    /** The API accepted the request but has nothing for this location. */
    data object NotFound : AppError()

    /** Free-tier quota exceeded (HTTP 429). Back off and retry later. */
    data object RateLimited : AppError()

    /**
     * The API key is missing, invalid, or not yet activated (HTTP 401).
     * Treated as a *configuration* problem, not a transient failure, the UI shows
     * setup instructions rather than a Retry button.
     */
    data object Unauthorized : AppError()

    /** The service failed (HTTP 5xx). Retrying may work. */
    data class Server(val statusCode: Int) : AppError()

    /** The response arrived but did not match the expected shape. */
    data class Decoding(val detail: String) : AppError()

    /** Reading or writing the local database failed. */
    data class Storage(val detail: String) : AppError()

    /**
     * The user already has [com.nauhaan.skycast.domain.model.SavedLocation.MAX_SAVED] places saved.
     *
     * A typed case rather than a boolean returned from `save`, because that is what makes the compiler
     * point at every screen that has to say something about it. It also travels the same path every
     * other failure does, so the Add-location screen needs no special branch to display it.
     */
    data class LocationLimitReached(val limit: Int) : AppError()

    /** Anything we did not anticipate. Keeps the cause for logging. */
    data class Unknown(override val cause: Throwable?) : AppError()

    /**
     * Whether offering the user a Retry action makes sense.
     *
     * [Unauthorized] is excluded, because retrying a bad API key can never succeed.
     */
    val isRetryable: Boolean
        get() =
            when (this) {
                Offline, Timeout, RateLimited, is Server, is Unknown -> true
                // Retrying a full list never empties it; the user has to remove a place first.
                NotFound, Unauthorized, is Decoding, is Storage, is LocationLimitReached -> false
            }

    /** True when the cause is the user's connectivity rather than our service. */
    val isConnectivityRelated: Boolean
        get() = this is Offline || this is Timeout
}
