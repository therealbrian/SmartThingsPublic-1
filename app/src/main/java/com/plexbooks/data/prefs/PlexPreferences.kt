package com.plexbooks.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "plex_prefs")

@Singleton
class PlexPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val AUTH_TOKEN = stringPreferencesKey("auth_token")
    private val CLIENT_ID = stringPreferencesKey("client_id")
    private val SERVER_URI = stringPreferencesKey("server_uri")
    private val SERVER_TOKEN = stringPreferencesKey("server_token")
    private val SERVER_NAME = stringPreferencesKey("server_name")
    private val SKIP_BACK_SECS = intPreferencesKey("skip_back_secs")
    private val SKIP_FORWARD_SECS = intPreferencesKey("skip_forward_secs")

    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN] }
    val clientId: Flow<String> = context.dataStore.data.map {
        it[CLIENT_ID] ?: UUID.randomUUID().toString().also { id -> saveClientId(id) }
    }
    val serverUri: Flow<String?> = context.dataStore.data.map { it[SERVER_URI] }
    val serverToken: Flow<String?> = context.dataStore.data.map { it[SERVER_TOKEN] }
    val serverName: Flow<String?> = context.dataStore.data.map { it[SERVER_NAME] }
    val skipBackSecs: Flow<Int> = context.dataStore.data.map { it[SKIP_BACK_SECS] ?: 15 }
    val skipForwardSecs: Flow<Int> = context.dataStore.data.map { it[SKIP_FORWARD_SECS] ?: 30 }

    suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { it[AUTH_TOKEN] = token }
    }

    private suspend fun saveClientId(id: String) {
        context.dataStore.edit { it[CLIENT_ID] = id }
    }

    suspend fun ensureClientId(): String {
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[CLIENT_ID] ?: UUID.randomUUID().toString().also { prefs[CLIENT_ID] = it }
        }
        return id
    }

    suspend fun saveServer(uri: String, token: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URI] = uri
            prefs[SERVER_TOKEN] = token
            prefs[SERVER_NAME] = name
        }
    }

    suspend fun setSkipBackSecs(secs: Int) {
        context.dataStore.edit { it[SKIP_BACK_SECS] = secs }
    }

    suspend fun setSkipForwardSecs(secs: Int) {
        context.dataStore.edit { it[SKIP_FORWARD_SECS] = secs }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
