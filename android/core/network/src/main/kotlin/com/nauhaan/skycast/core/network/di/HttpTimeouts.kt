package com.nauhaan.skycast.core.network.di

/**
 * Timeouts shared by every client in the app.
 *
 * Short, because the offline-first read path falls back to the cache with a retry rather than
 * waiting. Defined here so the OpenWeather and NOAA clients cannot drift apart.
 */
internal object HttpTimeouts {
    const val CONNECT_SECONDS = 15L
    const val READ_SECONDS = 20L
    const val CALL_SECONDS = 30L
}
