package com.nauhaan.skycast.core.network.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nauhaan.skycast.core.common.AppConfig
import com.nauhaan.skycast.core.common.NetworkMonitor
import com.nauhaan.skycast.data.remote.ApiKeyInterceptor
import com.nauhaan.skycast.data.remote.AviationWeatherApi
import com.nauhaan.skycast.data.remote.ConnectivityNetworkMonitor
import com.nauhaan.skycast.data.remote.OpenWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Connectivity, provided here rather than in :app because this is the module that owns
     * the ConnectivityManager-backed implementation. The interface it satisfies lives in
     * pure-Kotlin :core:common so :core:data can depend on it without seeing Android.
     */
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        ConnectivityNetworkMonitor(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // OpenWeather adds fields over time; ignoring unknowns means a new field
        // cannot break a released build.
        ignoreUnknownKeys = true
        // Treat an explicit JSON null as "use the Kotlin default" rather than failing.
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(appConfig: AppConfig): HttpLoggingInterceptor {
        // OpenWeather takes the API key as a *query parameter*, so every logged request
        // line would otherwise contain the secret in plain text. OkHttp 4's interceptor
        // has no query redaction (that arrived in OkHttp 5), so we redact in a custom
        // logger before anything reaches logcat.
        val redactingLogger =
            HttpLoggingInterceptor.Logger { message ->
                HttpLoggingInterceptor.Logger.DEFAULT.log(
                    message.replace(API_KEY_PATTERN, "appid=***"),
                )
            }

        return HttpLoggingInterceptor(redactingLogger).apply {
            // Never log in release: even redacted, request logging on a user's device
            // is noise at best and a leak at worst.
            level = if (appConfig.isDebug) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(logging: HttpLoggingInterceptor, appConfig: AppConfig): OkHttpClient = OkHttpClient
        .Builder()
        .addInterceptor(ApiKeyInterceptor(appConfig.apiKey))
        .addInterceptor(logging)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // A single retry covers a dropped connection without making a genuinely
        // offline device wait through several attempts.
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, appConfig: AppConfig): Retrofit = Retrofit
        .Builder()
        .baseUrl(appConfig.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideOpenWeatherApi(retrofit: Retrofit): OpenWeatherApi = retrofit.create(OpenWeatherApi::class.java)

    /**
     * A separate client for the aviation API, and the separation is not tidiness.
     *
     * The client above carries [ApiKeyInterceptor], which appends `appid=<our key>` to every request
     * it makes. Reusing it would send our OpenWeather key to a third-party host on every METAR
     * fetch, in a query string, in plain text, to a server that has no use for it. This client has
     * no key interceptor because the endpoint needs no key.
     *
     * It does carry a `User-Agent`. NOAA asks callers to identify themselves so they can contact
     * whoever is generating traffic rather than blocking an anonymous one.
     */
    @Provides
    @Singleton
    @Aviation
    fun provideAviationOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient = OkHttpClient
        .Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", AVIATION_USER_AGENT).build(),
            )
        }
        .addInterceptor(logging)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @Aviation
    fun provideAviationRetrofit(@Aviation client: OkHttpClient, json: Json): Retrofit = Retrofit
        .Builder()
        .baseUrl(AVIATION_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAviationWeatherApi(@Aviation retrofit: Retrofit): AviationWeatherApi =
        retrofit.create(AviationWeatherApi::class.java)

    /** Matches `appid=<anything up to the next & or end of line>`. */
    private val API_KEY_PATTERN = Regex("appid=[^&\\s]*")

    const val AVIATION_BASE_URL = "https://aviationweather.gov/api/data/"

    /**
     * Identifies the caller to NOAA, whose usage policy asks for it.
     *
     * No version substitution: this module cannot see :app's BuildConfig, and a hardcoded name is
     * more useful to them than none.
     */
    private const val AVIATION_USER_AGENT = "SkyCast/1.0 (student coursework; github.com/nauhaan)"

    /** Short enough that a stalled request surfaces as an error the user can retry. */
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 30L
}
