package com.nauhaan.skycast.core.network.di

import javax.inject.Qualifier

/**
 * Marks the **keyless** HTTP client, used for NOAA's services.
 *
 * Without a qualifier Hilt cannot tell two `OkHttpClient` bindings apart, and injecting the wrong
 * one here would append the OpenWeather key to every request to a third-party host.
 *
 * Shared by both NOAA services the app uses, aviation weather for METAR and the Space Weather
 * Prediction Center for Kp: no key, a `User-Agent` that identifies the caller as their usage policy
 * asks, and the same timeouts. Only the base URL differs, and Retrofit is per-base-URL, so there are
 * two `Retrofit` instances over this one client.
 *
 * See `NoaaNetworkModule.provideNoaaOkHttpClient`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Noaa

/** Marks the Retrofit instance for NOAA's Space Weather Prediction Center. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpaceWeatherService
