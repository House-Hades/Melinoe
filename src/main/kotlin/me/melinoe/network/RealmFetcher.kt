package me.melinoe.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import me.melinoe.Melinoe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object RealmFetcher {
    // Fallbacks in case they aren't fetched properly
    var naServers: List<String> = listOf("Ashburn", "Bayou", "Cedar", "Dakota", "Eagleton", "Farrion", "Groveridge", "Nalwood", "Hub-1", "Hub-2", "Missions")
    var euServers: List<String> = listOf("Astra", "Balkan", "Creska", "Darkon", "Draskov", "Estenmoor", "Falkenburg", "Galla", "Harvenfeld", "Helmburg", "Holloway", "Inderfall", "Ivarn", "Jarnholm", "Jarnwald", "Krausenfeld", "Larpswood", "Lindenburg", "Hub-1", "Hub-2", "Hub-3", "Missions")
    var sgServers: List<String> = listOf("Asura", "Bayan", "Chantara", "Hub-1", "Missions")
    
    private const val SERVERS_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/servers.json"

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
    
    fun fetchServers() {
        CompletableFuture.runAsync {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVERS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build()
                
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() == 200) {
                    val json = Gson().fromJson(response.body(), JsonObject::class.java)
                    
                    if (json.has("NA")) naServers = json.getAsJsonArray("NA").map { it.asString }
                    if (json.has("EU")) euServers = json.getAsJsonArray("EU").map { it.asString }
                    if (json.has("SG")) sgServers = json.getAsJsonArray("SG").map { it.asString }
                    
                    Melinoe.logger.info("[RealmFetcher] Successfully updated server lists from GitHub.")
                } else {
                    Melinoe.logger.warn("[RealmFetcher] Failed to fetch server lists. HTTP Status: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                Melinoe.logger.error("[RealmFetcher] Error fetching server lists from GitHub", e)
            }
        }
    }
}