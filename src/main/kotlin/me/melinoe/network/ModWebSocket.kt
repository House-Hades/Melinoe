package me.melinoe.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import me.melinoe.Melinoe
import net.minecraft.client.Minecraft
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ModWebSocket {
    @Volatile
    private var webSocket: WebSocket? = null
    private val httpClient = HttpClient.newHttpClient()
    private val isConnecting = AtomicBoolean(false)

    val activeModUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun connect() {
        if (webSocket != null || isConnecting.get()) return

        isConnecting.set(true)
        // Build and connect asynchronously
        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("wss://fig.magnetite.dev"), Listener())
            .thenAccept { ws ->
                this.webSocket = ws
                isConnecting.set(false)
            }.exceptionally {
                Melinoe.logger.error("Failed to connect to Mod WebSocket: ${it.message}")
                isConnecting.set(false)
                null
            }
    }
    
    fun disconnect() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting")
        webSocket = null
        activeModUsers.clear()
        isConnecting.set(false)
    }

    private class Listener : WebSocket.Listener {
        private val messageBuffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            messageBuffer.setLength(0)
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
                                    }
                                    webSocket.sendText(response.toString(), true)

                                } catch (e: Exception) {
                                    Melinoe.logger.error("Failed to authenticate with Mojang: ${e.message}")
                                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Auth Failed")
                                }
                            }
                        }
                        "sync" -> {
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
            if (ModWebSocket.webSocket === webSocket) {
                disconnect()
            }
            return super.onClose(webSocket, statusCode, reason)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            Melinoe.logger.error("WebSocket error: ${error.message}")
            if (ModWebSocket.webSocket === webSocket) {
                disconnect()
            }
            super.onError(webSocket, error)
        }
    }
}