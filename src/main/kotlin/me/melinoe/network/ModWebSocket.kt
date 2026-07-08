package me.melinoe.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import me.melinoe.Melinoe
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.TabListUtils
import net.minecraft.client.Minecraft
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Presence WebSocket client for the Melinoe API.
 *
 * All reconnection logic lives here.
 *
 * Reconnect strategy:
 *  - Transient drops (1006, network errors): exponential backoff 1s..30s with +/-20% jitter.
 *  - Planned restart (1012): short fixed delay (2s..4.5s) so we come back promptly.
 *  - Permanent failures (1008 policy, 1013 rate-limit, Mojang auth fail): aggressive 30s+ backoff
 *    so we don't hammer the server on something retrying won't fix.
 * The backoff resets once a connection reaches auth_success.
 */
object ModWebSocket {
    private const val WS_URL = "wss://melinoe.magnetite.dev"

    // Transient backoff: starts ~1s, doubles, caps at ~30s.
    private const val INITIAL_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 30_000L

    // Permanent backoff: starts at 30s, doubles, caps at 5m.
    private const val PERMANENT_BACKOFF_MS = 30_000L
    private const val PERMANENT_MAX_BACKOFF_MS = 300_000L

    // Planned-restart (1012) delay window.
    private const val PLANNED_RESTART_MIN_MS = 2_000L
    private const val PLANNED_RESTART_MAX_MS = 4_500L

    private const val JITTER_FACTOR = 0.2

    // Close codes not exposed as constants by java.net.http.
    private const val NO_STATUS_CODE = 1005
    private const val POLICY_VIOLATION_CODE = 1008
    private const val SERVICE_RESTART_CODE = 1012
    private const val RATE_LIMITED_CODE = 1013

    private val httpClient = HttpClient.newHttpClient()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Melinoe-WS-Reconnect").apply { isDaemon = true }
    }

    @Volatile
    private var webSocket: WebSocket? = null

    /** Guards against two overlapping build attempts. */
    private val isConnecting = AtomicBoolean(false)

    /** Set when the client itself is tearing the connection down; suppresses reconnects. */
    private val intentionalClose = AtomicBoolean(false)

    /** Bumped on every open/teardown so stale listener callbacks can be ignored. */
    private val connectionGeneration = AtomicInteger(0)

    /** The single pending reconnect timer, if any. */
    @Volatile
    private var pendingReconnect: ScheduledFuture<*>? = null

    /** Current transient backoff; reset to INITIAL once a connection authenticates. */
    @Volatile
    private var backoffMs = INITIAL_BACKOFF_MS

    val activeModUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private enum class CloseCategory { PLANNED, PERMANENT, TRANSIENT }

    /** Begin connecting. Marks the connection as wanted and clears any prior backoff. */
    fun connect() {
        intentionalClose.set(false)
        if (webSocket != null || isConnecting.get()) return
        resetBackoff()
        openConnection()
    }

    /** Tear the connection down for good (logout / leaving Telos / shutdown). No reconnect follows. */
    fun disconnect() {
        intentionalClose.set(true)
        // Invalidate any in-flight build and any listener callbacks.
        connectionGeneration.incrementAndGet()
        cancelPendingReconnect()

        val ws = webSocket
        webSocket = null
        activeModUsers.clear()
        isConnecting.set(false)

        ws?.let {
            try {
                it.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting")
            } catch (e: Exception) {
                try { it.abort() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Informs the api of a realm switch.
     */
    fun sendLocationUpdate(serverName: String) {
        val ws = webSocket ?: return
        sendLocationUpdateOn(ws, serverName)
    }

    private fun sendLocationUpdateOn(ws: WebSocket, serverName: String) {
        CompletableFuture.runAsync {
            try {
                val request = JsonObject().apply {
                    addProperty("action", "location_update")
                    addProperty("server", serverName)
                }
                ws.sendText(request.toString(), true)
            } catch (e: Exception) {
                Melinoe.logger.error("Failed to send WebSocket location update: ${e.message}")
            }
        }
    }

    /**
     * Opens a fresh socket, guaranteeing the single-connection invariant first: cancel any pending
     * reconnect and abort any lingering socket so we never run two connections at once.
     */
    private fun openConnection() {
        if (intentionalClose.get()) return
        if (!isConnecting.compareAndSet(false, true)) return

        cancelPendingReconnect()
        abortExistingSocket()

        val generation = connectionGeneration.incrementAndGet()
        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(WS_URL), Listener(generation))
            .thenAccept { ws ->
                // A newer attempt started, or we were torn down, while this one was building.
                if (generation != connectionGeneration.get() || intentionalClose.get()) {
                    try { ws.abort() } catch (_: Exception) {}
                    isConnecting.set(false)
                    return@thenAccept
                }
                webSocket = ws
                isConnecting.set(false)
            }
            .exceptionally { error ->
                Melinoe.logger.warn("Failed to connect to Mod WebSocket: ${error.message}")
                isConnecting.set(false)
                if (generation == connectionGeneration.get()) {
                    scheduleReconnect(CloseCategory.TRANSIENT)
                }
                null
            }
    }

    private fun scheduleReconnect(category: CloseCategory) {
        if (intentionalClose.get()) return
        cancelPendingReconnect()

        val delay = when (category) {
            CloseCategory.PLANNED -> ThreadLocalRandom.current().nextLong(PLANNED_RESTART_MIN_MS, PLANNED_RESTART_MAX_MS + 1)
            CloseCategory.PERMANENT -> {
                val base = backoffMs.coerceAtLeast(PERMANENT_BACKOFF_MS)
                backoffMs = (base * 2).coerceAtMost(PERMANENT_MAX_BACKOFF_MS)
                withJitter(base)
            }
            CloseCategory.TRANSIENT -> {
                val base = backoffMs
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                withJitter(base)
            }
        }

        pendingReconnect = scheduler.schedule({ openConnection() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun cancelPendingReconnect() {
        pendingReconnect?.cancel(false)
        pendingReconnect = null
    }

    private fun abortExistingSocket() {
        val ws = webSocket
        webSocket = null
        if (ws != null) {
            try { ws.abort() } catch (_: Exception) {}
        }
    }

    /** Drops the live socket reference and the now-stale presence list. */
    private fun clearConnectionState() {
        webSocket = null
        activeModUsers.clear()
    }

    private fun resetBackoff() {
        backoffMs = INITIAL_BACKOFF_MS
    }

    private fun withJitter(baseMs: Long): Long {
        val jitter = (baseMs * JITTER_FACTOR).toLong()
        if (jitter <= 0) return baseMs
        val offset = ThreadLocalRandom.current().nextLong(-jitter, jitter + 1)
        return (baseMs + offset).coerceAtLeast(0)
    }

    /**
     * @param beforeAuth whether the connection closed before reaching auth_success. A normal/no-status
     *   close in that window means Mojang auth was rejected, which retrying fast won't fix.
     */
    private fun categorize(statusCode: Int, beforeAuth: Boolean): CloseCategory = when {
        statusCode == SERVICE_RESTART_CODE -> CloseCategory.PLANNED
        statusCode == POLICY_VIOLATION_CODE || statusCode == RATE_LIMITED_CODE -> CloseCategory.PERMANENT
        beforeAuth && (statusCode == WebSocket.NORMAL_CLOSURE || statusCode == NO_STATUS_CODE) -> CloseCategory.PERMANENT
        else -> CloseCategory.TRANSIENT
    }

    private class Listener(private val generation: Int) : WebSocket.Listener {
        private val messageBuffer = StringBuilder()

        /** True from open until auth_success; lets the close handler spot a failed handshake. */
        @Volatile
        private var awaitingAuthSuccess = true

        private fun isCurrent() = generation == connectionGeneration.get()

        override fun onOpen(webSocket: WebSocket) {
            messageBuffer.setLength(0)
            awaitingAuthSuccess = true
            super.onOpen(webSocket)
        }

        // Called whenever the server sends us a message
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            messageBuffer.append(data)
            if (last) {
                val message = messageBuffer.toString()
                messageBuffer.setLength(0)

                try {
                    val reader = JsonReader(StringReader(message))
                    reader.strictness = Strictness.LENIENT
                    val json = JsonParser.parseReader(reader).asJsonObject
                    val action = json.get("action").asString

                    when (action) {
                        "auth_request" -> {
                            // Use THIS connection's serverId; a previous connection's is dead.
                            val serverId = json.get("serverId").asString
                            val client = Minecraft.getInstance()
                            val session = client.user

                            CompletableFuture.runAsync {
                                try {
                                    client.services().sessionService.joinServer(
                                        session.profileId,
                                        session.accessToken,
                                        serverId
                                    )

                                    // Tell backend we finished signing
                                    val response = JsonObject().apply {
                                        addProperty("action", "auth_response")
                                        addProperty("name", session.name)
                                        addProperty("uuid", session.profileId.toString())
                                        addProperty("version", Melinoe.version.toString())
                                        addProperty("server", TabListUtils.getServer() ?: "Unknown")
                                    }
                                    webSocket.sendText(response.toString(), true)

                                } catch (e: Exception) {
                                    Melinoe.logger.error("Failed to authenticate with Mojang: ${e.message}")
                                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Auth Failed")
                                }
                            }
                        }
                        "auth_success" -> {
                            awaitingAuthSuccess = false
                            // We made it: a healthy connection clears the backoff.
                            resetBackoff()
                            // The server wiped onlineUsers on restart, so re-announce our realm if we
                            // know it, rather than letting presence read Unknown until the next change.
                            val realm = LocalAPI.getCurrentCharacterWorld()
                            if (realm.isNotEmpty()) {
                                sendLocationUpdateOn(webSocket, realm)
                            }
                        }
                        "sync" -> {
                            // Repopulate strictly from the fresh list; the old one is gone.
                            activeModUsers.clear()
                            json.getAsJsonArray("names").forEach { activeModUsers.add(it.asString) }
                        }
                        "add" -> activeModUsers.add(json.get("name").asString)
                        "remove" -> activeModUsers.remove(json.get("name").asString)
                    }
                } catch (e: Exception) {
                    Melinoe.logger.error("Error processing WebSocket message: '${message}'", e)
                }
            }
            return super.onText(webSocket, data, last)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (!isCurrent()) return super.onClose(webSocket, statusCode, reason)

            clearConnectionState()

            // We initiated this close (logout / leaving / shutdown): stay down.
            if (intentionalClose.get()) return super.onClose(webSocket, statusCode, reason)

            val category = categorize(statusCode, awaitingAuthSuccess)
            when (category) {
                CloseCategory.PLANNED ->
                    Melinoe.logger.info("Mod WebSocket: server restarting (1012), reconnecting shortly")
                CloseCategory.PERMANENT ->
                    Melinoe.logger.warn("Mod WebSocket closed, backing off (code=$statusCode, reason='$reason')")
                CloseCategory.TRANSIENT ->
                    Melinoe.logger.info("Mod WebSocket dropped (code=$statusCode, reason='$reason'), reconnecting")
            }
            scheduleReconnect(category)
            return super.onClose(webSocket, statusCode, reason)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (!isCurrent()) {
                super.onError(webSocket, error)
                return
            }
            Melinoe.logger.warn("Mod WebSocket error: ${error.message}")
            clearConnectionState()
            // A raw transport error is a transient drop; back off gently and retry.
            if (!intentionalClose.get()) {
                scheduleReconnect(CloseCategory.TRANSIENT)
            }
            super.onError(webSocket, error)
        }
    }
}
