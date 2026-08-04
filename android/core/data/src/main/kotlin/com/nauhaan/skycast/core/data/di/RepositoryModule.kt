package com.nauhaan.skycast.core.data.di

import com.nauhaan.skycast.core.common.DefaultDispatcherProvider
import com.nauhaan.skycast.core.common.DispatcherProvider
import com.nauhaan.skycast.data.repository.LocationRepositoryImpl
import com.nauhaan.skycast.data.repository.SettingsRepositoryImpl
import com.nauhaan.skycast.data.repository.WeatherRepositoryImpl
import com.nauhaan.skycast.domain.repository.LocationRepository
import com.nauhaan.skycast.domain.repository.SettingsRepository
import com.nauhaan.skycast.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Binds each `domain` interface to its `data` implementation.
 *
 * This module is the only place in the app where a concrete repository type is named.
 * Everything else depends on the interface, which is what lets tests substitute fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    /**
     * Time is injected so cache-TTL behaviour can be tested with a fixed clock instead
     * of `Thread.sleep`. Nothing in the app calls `Instant.now()` directly.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideDefaultDispatcherProvider(): DefaultDispatcherProvider = DefaultDispatcherProvider()
}
