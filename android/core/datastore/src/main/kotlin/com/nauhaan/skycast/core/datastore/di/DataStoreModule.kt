package com.nauhaan.skycast.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nauhaan.skycast.data.preferences.UserPreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One DataStore, for the lifetime of the **process**.
 *
 * The `preferencesDataStore` delegate rather than `PreferenceDataStoreFactory.create`, and the
 * difference is not stylistic. The factory builds a *new* store on every call, and `@Singleton` only
 * promises one per Hilt component, which is one per *test method*, because Hilt rebuilds the singleton
 * component for each one while the process lives on. The second test to run therefore opened a second
 * store on the same file and DataStore threw:
 *
 * > There are multiple DataStores active for the same file … confirm that the scope is cancelled
 *
 * The visible symptom was nothing to do with storage: the Settings screen sat on its loading state
 * forever, because the flow it collects had failed, and four of the five instrumented tests reported
 * only that they gave up waiting for text.
 *
 * The delegate caches one instance per name for the process, so a rebuilt graph gets the same store.
 * Production behaviour is unchanged, there was only ever one component there.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPreferencesDataStore
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = UserPreferencesDataSource.DATA_STORE_NAME,
)
