package com.nauhaan.skycast.core.common

/**
 * Build-time configuration, as an injectable interface.
 *
 * `BuildConfig` belongs to `:app`, so `:core:data` and `:core:network` cannot see it. The interface
 * lives here in pure-Kotlin `:core:common`; `:app` supplies the implementation that reads its own
 * `BuildConfig`. Tests supply whatever they need.
 */
interface AppConfig {
    /** The OpenWeather API key, or an empty string when none was supplied at build time. */
    val apiKey: String

    val baseUrl: String

    val isDebug: Boolean

    /**
     * False when no key was supplied.
     *
     * The build still succeeds without one, since CI has no secret, so the app checks this at
     * runtime and shows setup instructions rather than failing every request with an opaque 401.
     */
    val isApiKeyConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != PLACEHOLDER_KEY

    companion object {
        /** The value in `local.properties.example`; treated as "not configured". */
        const val PLACEHOLDER_KEY = "your_openweather_api_key_here"
    }
}
