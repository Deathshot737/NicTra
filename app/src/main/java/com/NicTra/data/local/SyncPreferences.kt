package com.nictra.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

class SyncPreferences(private val context: Context) {

    companion object {
        private val FEED_VERSION = stringPreferencesKey("feed_version")
        private val ULTIMA_ACTUALIZACION = stringPreferencesKey("ultima_actualizacion")
        private val LAST_FULL_SYNC_TIME = longPreferencesKey("last_full_sync_time")
    }

    val feedVersion: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[FEED_VERSION]
        }

    val ultimaActualizacion: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ULTIMA_ACTUALIZACION]
        }

    val lastFullSyncTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_FULL_SYNC_TIME] ?: 0L
        }

    suspend fun saveSyncInfo(version: String, fecha: String, fullSync: Boolean = false) {
        context.dataStore.edit { preferences ->
            preferences[FEED_VERSION] = version
            preferences[ULTIMA_ACTUALIZACION] = fecha
            if (fullSync) {
                preferences[LAST_FULL_SYNC_TIME] = System.currentTimeMillis()
            }
        }
    }
}
