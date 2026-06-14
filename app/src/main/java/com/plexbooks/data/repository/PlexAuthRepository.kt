package com.plexbooks.data.repository

import com.plexbooks.data.api.PlexAuthApi
import com.plexbooks.data.api.model.PlexConnection
import com.plexbooks.data.api.model.PlexPin
import com.plexbooks.data.api.model.PlexResource
import com.plexbooks.data.prefs.PlexPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Socket
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
        // Probe candidates in priority order: non-relay IPv4 first, then relay
        val candidates = resource.connections
            .filter { !it.address.contains(':') }   // drop IPv6
            .sortedWith(compareBy(
                { it.relay },                        // non-relay before relay
                { !it.local }                        // local before external
            ))

        val best = probeFirst(candidates) ?: candidates.firstOrNull() ?: return

        prefs.saveServer(
            uri = best.uri,
            token = resource.accessToken ?: prefs.authToken.first() ?: "",
            name = resource.name
        )
    }

    /**
     * Races TCP probes against all candidates in parallel, returns the first
     * one that accepts a connection within 3 seconds.
     */
    private suspend fun probeFirst(connections: List<PlexConnection>): PlexConnection? =
        coroutineScope {
            connections.map { conn ->
                async {
                    val reachable = withTimeoutOrNull(3_000) {
                        runCatching {
                            Socket().use { s ->
                                s.connect(java.net.InetSocketAddress(conn.address, conn.port), 3_000)
                                true
                            }
                        }.getOrDefault(false)
                    } ?: false
                    if (reachable) conn else null
                }
            }.mapNotNull { it.await() }.firstOrNull()
        }

    suspend fun logout() = prefs.clearAll()

    val authToken get() = prefs.authToken
    val serverUri get() = prefs.serverUri
    val serverToken get() = prefs.serverToken
    val serverName get() = prefs.serverName
}
