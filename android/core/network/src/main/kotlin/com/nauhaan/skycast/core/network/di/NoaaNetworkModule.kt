package com.nauhaan.skycast.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nauhaan.skycast.data.remote.AviationWeatherApi
import com.nauhaan.skycast.data.remote.SpaceWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The two **keyless NOAA** services: aviation weather (METAR) and space weather (the Kp index).
 *
 * Separate from [NetworkModule] because these share a property that the OpenWeather stack does not
 * have, no API key, and that difference is a security boundary rather than a filing decision. See
 * [provideNoaaOkHttpClient].
 */
@Module
@InstallIn(SingletonComponent::class)
object NoaaNetworkModule {
    /**
     * One client for both NOAA services, and **not** the OpenWeather one.
     *
     * That client carries `ApiKeyInterceptor`, which appends `appid=<key>` to every request it
     * makes, so reusing it would send the OpenWeather key to a third-party host in a query string
     * on every METAR and Kp fetch. This client has no key interceptor, because neither endpoint
     * needs a key.
     *
     * It does carry a `User-Agent`, which NOAA's usage policy asks callers to supply.
     */
    @Provides
    @Singleton
    @Noaa
    fun provideNoaaOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient = OkHttpClient
        .Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", NOAA_USER_AGENT).build(),
            )
        }
        .addInterceptor(logging)
        .connectTimeout(HttpTimeouts.CONNECT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HttpTimeouts.READ_SECONDS, TimeUnit.SECONDS)
        .callTimeout(HttpTimeouts.CALL_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @Aviation
    fun provideAviationRetrofit(@Noaa client: OkHttpClient, json: Json): Retrofit =
        noaaRetrofit(AVIATION_BASE_URL, client, json)

    @Provides
    @Singleton
    @SpaceWeatherService
    fun provideSpaceWeatherRetrofit(@Noaa client: OkHttpClient, json: Json): Retrofit =
        noaaRetrofit(SPACE_WEATHER_BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideAviationWeatherApi(@Aviation retrofit: Retrofit): AviationWeatherApi =
        retrofit.create(AviationWeatherApi::class.java)

    @Provides
    @Singleton
    fun provideSpaceWeatherApi(@SpaceWeatherService retrofit: Retrofit): SpaceWeatherApi =
        retrofit.create(SpaceWeatherApi::class.java)

    /**
     * Two Retrofit instances over the *same* client.
     *
     * Retrofit binds exactly one base URL, and the two services live on different hosts. Everything
     * else about them is identical, so only the URL differs here.
     */
    private fun noaaRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    const val AVIATION_BASE_URL = "https://aviationweather.gov/api/data/"

    /** NOAA's Space Weather Prediction Center. Keyless and public domain, like the aviation service. */
    const val SPACE_WEATHER_BASE_URL = "https://services.swpc.noaa.gov/"

    /**
     * Identifies the caller to NOAA, whose usage policy asks for it.
     *
     * No version substitution: this module cannot see :app's BuildConfig, and a hardcoded name is
     * more useful to them than none.
     */
    private const val NOAA_USER_AGENT = "SkyCast/1.0 (student coursework; github.com/nauhaan)"
}
