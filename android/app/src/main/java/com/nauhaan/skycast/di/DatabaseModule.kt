package com.nauhaan.skycast.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nauhaan.skycast.data.local.SkyCastDatabase
import com.nauhaan.skycast.data.local.dao.SavedLocationDao
import com.nauhaan.skycast.data.local.dao.WeatherCacheDao
import com.nauhaan.skycast.data.preferences.UserPreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        // Deliberately NO fallbackToDestructiveMigration(): losing the user's saved
        // locations on an app update would be a persistence failure. Real migrations
        // go here as the schema evolves. See SkyCastDatabase's KDoc.
        .build()

    @Provides
    fun provideSavedLocationDao(database: SkyCastDatabase): SavedLocationDao = database.savedLocationDao()

    @Provides
    fun provideWeatherCacheDao(database: SkyCastDatabase): WeatherCacheDao = database.weatherCacheDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(UserPreferencesDataSource.DATA_STORE_NAME) },
        )
}
