package com.nauhaan.skycast.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nauhaan.skycast.data.local.Migrations
import com.nauhaan.skycast.data.local.SkyCastDatabase
import com.nauhaan.skycast.data.local.dao.MetarCacheDao
import com.nauhaan.skycast.data.local.dao.SavedLocationDao
import com.nauhaan.skycast.data.local.dao.SpaceWeatherCacheDao
import com.nauhaan.skycast.data.local.dao.WeatherCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room bindings, provided by the module that owns the database.
 *
 * Each module provides its own bindings rather than :app providing everything. That keeps
 * Room an `implementation` detail of :core:database:app does not depend on Room at all
 * and could not accidentally reference a DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SkyCastDatabase = Room
        .databaseBuilder(context, SkyCastDatabase::class.java, SkyCastDatabase.NAME)
        // WAL keeps reads from blocking on writes, so observing a Flow of saved
        // locations never stutters while a cache write is in flight.
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        // NO fallbackToDestructiveMigration(): it would delete the user's saved locations on an
        // app update. See Migrations.
        .addMigrations(
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
            Migrations.MIGRATION_3_4,
            Migrations.MIGRATION_4_5,
        )
        .build()

    @Provides
    fun provideSavedLocationDao(database: SkyCastDatabase): SavedLocationDao = database.savedLocationDao()

    @Provides
    fun provideWeatherCacheDao(database: SkyCastDatabase): WeatherCacheDao = database.weatherCacheDao()

    @Provides
    fun provideMetarCacheDao(database: SkyCastDatabase): MetarCacheDao = database.metarCacheDao()

    @Provides
    fun provideSpaceWeatherCacheDao(database: SkyCastDatabase): SpaceWeatherCacheDao = database.spaceWeatherCacheDao()
}
