package com.nauhaan.skycast.core.network.di

import javax.inject.Qualifier

/**
 * Marks the OkHttp client and Retrofit instance for the aviation API.
 *
 * Without a qualifier Hilt cannot tell two `OkHttpClient` bindings apart, and the wrong one being
 * injected here would silently append our OpenWeather key to every request to a third-party host.
 * See `NetworkModule.provideAviationOkHttpClient`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Aviation
