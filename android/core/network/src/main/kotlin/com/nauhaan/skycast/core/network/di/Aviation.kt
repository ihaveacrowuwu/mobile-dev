package com.nauhaan.skycast.core.network.di

import javax.inject.Qualifier

/**
 * Marks the Retrofit instance for the aviation weather API.
 *
 * The **client** it runs over is marked [Noaa] and shared with the space-weather service; only the
 * base URL is specific to aviation, and Retrofit binds one base URL per instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Aviation
