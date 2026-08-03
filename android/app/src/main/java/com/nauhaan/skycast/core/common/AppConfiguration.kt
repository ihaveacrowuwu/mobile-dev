package com.nauhaan.skycast.core.common

import com.nauhaan.skycast.BuildConfig

/**
 * Build-time configuration, read from `BuildConfig` so no secret is ever a
 * source-code literal.
 *
 * The API key comes from `local.properties` (gitignored) or the
 * `OPEN_WEATHER_API_KEY` environment variable, see `app/build.gradle.kts`.
 */
object AppConfiguration {
    val apiKey: String = BuildConfig.OPEN_WEATHER_API_KEY

    val baseUrl: String = BuildConfig.OPEN_WEATHER_BASE_URL

    val isDebug: Boolean = BuildConfig.DEBUG

    /**
     * False when no key was supplied at build time.
     *
     * The build deliberately still succeeds in that case (CI has no secret), so the
     * app checks this at runtime and shows setup instructions instead of failing
     * every request with an opaque 401.
     */
    val isApiKeyConfigured: Boolean get() = apiKey.isNotBlank()
}
