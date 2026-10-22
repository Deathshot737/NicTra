package com.nictra.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

class SyncPreferences(private val context: Context) {

    companion object {
        private val FEED_VERSION = stringPreferencesKey("feed_version")
        private val ULTIMA_ACTUALIZACION = stringPreferencesKey("ultima_actualizacion")
    }

    val feedVersion: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[FEED_VERSION]
        }

    val ultimaActualizacion: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ULTIMA_ACTUALIZACION]
        }

    suspend fun saveSyncInfo(version: String, fecha: String) {
        context.dataStore.edit { preferences ->
            preferences[FEED_VERSION] = version
            preferences[ULTIMA_ACTUALIZACION] = fecha
        }
    }
}
