package com.nauhaan.skycast

import com.nauhaan.skycast.core.common.AppConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [AppConfig] implementation, reading this module's generated `BuildConfig`.
 *
 * It lives in `:app` because `BuildConfig` does: it is generated per application module from
 * `local.properties` (see `app/build.gradle.kts`). Everything below `:app` depends on the
 * [AppConfig] interface in `:core:common` instead, so no secret and no build-variant knowledge
 * leaks into the lower layers, and a test can substitute any configuration it likes.
 */
@Singleton
class BuildConfigAppConfig @Inject constructor() : AppConfig {
    override val apiKey: String = BuildConfig.OPEN_WEATHER_API_KEY
    override val baseUrl: String = BuildConfig.OPEN_WEATHER_BASE_URL
    override val isDebug: Boolean = BuildConfig.DEBUG
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppConfigModule {
    @Binds
    @Singleton
    abstract fun bindAppConfig(impl: BuildConfigAppConfig): AppConfig
}
