package com.plexbooks.data.repository

import com.plexbooks.data.api.PlexAuthApi
import com.plexbooks.data.api.model.PlexPin
import com.plexbooks.data.api.model.PlexResource
import com.plexbooks.data.prefs.PlexPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexAuthRepository @Inject constructor(
    private val authApi: PlexAuthApi,
    private val prefs: PlexPreferences
) {
    suspend fun createPin(): PlexPin = authApi.createPin()

    fun buildAuthUrl(clientId: String, pinCode: String): String =
        "https://app.plex.tv/auth#?" +
            "clientID=$clientId" +
            "&code=$pinCode" +
            "&context[device][product]=PlexBooks" +
            "&context[device][environment]=bundled" +
            "&context[device][layout]=desktop"

    /** Polls until the user approves the PIN or timeout is reached. Returns token or null. */
    suspend fun pollForToken(pinId: Long, pinCode: String, timeoutMs: Long = 300_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val pin = authApi.checkPin(pinId, pinCode)
            if (!pin.authToken.isNullOrBlank()) {
                prefs.saveAuthToken(pin.authToken)
                return pin.authToken
            }
            delay(2_000)
        }
        return null
    }

    suspend fun getServers(): List<PlexResource> =
        authApi.getResources().filter { it.provides.contains("server") }

    suspend fun selectServer(resource: PlexResource) {
        val conn = resource.connections
            .sortedWith(compareBy({ it.relay }, { it.local }))
            .firstOrNull() ?: return
        prefs.saveServer(
            uri = conn.uri,
            token = resource.accessToken ?: prefs.authToken.first() ?: "",
            name = resource.name
        )
    }

    suspend fun logout() = prefs.clearAll()

    val authToken get() = prefs.authToken
    val serverUri get() = prefs.serverUri
    val serverToken get() = prefs.serverToken
    val serverName get() = prefs.serverName
}
