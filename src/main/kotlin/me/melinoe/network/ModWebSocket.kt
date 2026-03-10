package me.melinoe.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.melinoe.Melinoe
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

object ModWebSocket : WebSocket.Listener {
    private var webSocket: WebSocket? = null
    private val httpClient = HttpClient.newHttpClient()
    
    val activeModUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    
    fun connect() {
        // Build and connect asynchronously
        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("wss://fig.magnetite.dev"), this)
            .thenAccept { ws ->
                this.webSocket = ws
            }.exceptionally {
                Melinoe.logger.error("Failed to connect to Mod WebSocket: ${it.message}")
                null
            }
    }
    
    fun disconnect() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting")
        webSocket = null
        activeModUsers.clear()
    }
    
    // Called whenever the server sends us a message
    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        val json = JsonParser.parseString(data.toString()).asJsonObject
        val action = json.get("action").asString

        when (action) {
            "auth_request" -> {
                val serverId = json.get("serverId").asString
                val client = Minecraft.getInstance()
                val session = client.user

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
                    println("Failed to authenticate with Mojang: ${e.message}")
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Auth Failed")
                }
            }
            "sync" -> {
                activeModUsers.clear()
                json.getAsJsonArray("names").forEach { activeModUsers.add(it.asString) }
            }
            "add" -> activeModUsers.add(json.get("name").asString)
            "remove" -> activeModUsers.remove(json.get("name").asString)
        }
        return super.onText(webSocket, data, last)
    }
}