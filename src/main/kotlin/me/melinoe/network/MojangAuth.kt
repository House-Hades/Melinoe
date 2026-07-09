package me.melinoe.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import me.melinoe.Melinoe
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Gets and caches a mod token for the Melinoe API using Mojang's auth handshake.
 *
 * Asks the API for a challenge, proves the account to Mojang with joinServer, then trades the
 * challenge for a token. The token lasts ~1 hour and is only re-fetched when it expires, a
 * refresh is forced, or a request comes back 401
 */
object MojangAuth {
    
    private const val HOST = "https://melinoe.magnetite.dev"
    private const val CHALLENGE_URL = "$HOST/api/private/profile/challenge"
    private const val AUTH_URL = "$HOST/auth/mojang"
    
    // Refresh a bit before the 1h server expiry
    private const val TOKEN_LIFETIME_MS = 55 * 60 * 1000L

    // Wait before retrying the handshake after a 401, so a racing handshake can finish
    private const val RETRY_DELAY_MS = 750L
    
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
    
    private val gson = Gson()
    private val lock = Any()
    
    @Volatile private var token: String? = null
    @Volatile private var expiresAt: Long = 0L
    
    /** Returns a valid token, running the handshake if needed. forceRefresh ignores the cache. */
    fun getToken(forceRefresh: Boolean = false): String = synchronized(lock) {
        val cached = token
        if (!forceRefresh && cached != null && System.currentTimeMillis() < expiresAt) {
            return cached
        }
        
        val fresh = authenticate()
        token = fresh
        expiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MS
        fresh
    }
    
    /** Clears the cached token so the next getToken re-authenticates. Call after a 401. */
    fun invalidate() = synchronized(lock) {
        token = null
        expiresAt = 0L
    }
    
    private fun authenticate(): String {
        val firstAttempt = runHandshake()
        if (firstAttempt.statusCode() != 401) {
            return extractToken(firstAttempt)
        }

        // A 401 here usually means another handshake (e.g. the websocket's) overwrote our
        // joinServer record on Mojang's session server before the backend verified it —
        // Mojang only keeps the latest join per account. Retry once with a fresh challenge
        // after the racing handshake has settled.
        Melinoe.logger.warn("[MojangAuth] Token exchange got 401, retrying handshake once")
        Thread.sleep(RETRY_DELAY_MS)
        return extractToken(runHandshake())
    }

    /** Challenge -> joinServer -> token exchange. Returns the raw exchange response. */
    private fun runHandshake(): HttpResponse<String> {
        val mc = Minecraft.getInstance()
        val session = mc.user ?: throw ProfileException("You must be logged in to a Minecraft account.")

        // Ask the API for a challenge
        val challengeResp = post(CHALLENGE_URL, "")
        if (challengeResp.statusCode() != 200) {
            throw ProfileException("Auth challenge failed (HTTP ${challengeResp.statusCode()}).")
        }
        val challenge = gson.fromJson(challengeResp.body(), Challenge::class.java)
        val serverId = challenge?.serverId
            ?: throw ProfileException("Auth challenge returned no serverId.")

        // Prove the account to Mojang
        try {
            mc.services().sessionService.joinServer(session.profileId, session.accessToken, serverId)
        } catch (e: Exception) {
            Melinoe.logger.error("[MojangAuth] joinServer failed", e)
            throw ProfileException("Mojang authentication failed.")
        }

        // Trade the challenge for a token
        val body = JsonObject().apply {
            addProperty("username", session.name)
            addProperty("uuid", session.profileId.toString())
            addProperty("serverId", serverId)
        }
        return post(AUTH_URL, body.toString())
    }

    private fun extractToken(authResp: HttpResponse<String>): String {
        if (authResp.statusCode() != 200) {
            throw ProfileException("Token exchange failed (HTTP ${authResp.statusCode()}).")
        }
        return gson.fromJson(authResp.body(), TokenResponse::class.java)?.token
            ?: throw ProfileException("Token exchange returned no token.")
    }
    
    private fun post(url: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
    
    private data class Challenge(val serverId: String? = null, val expiresInMs: Long = 0)
    private data class TokenResponse(val token: String? = null, val expiresIn: String? = null)
}