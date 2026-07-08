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
    // Static fallbacks in case the online counts haven't been fetched yet
    val naServers: List<String> = listOf("Ashburn", "Bayou", "Cedar", "Dakota", "Eagleton", "Farrion", "Groveridge", "Nalwood", "Hub-1", "Hub-2", "Missions")
    val euServers: List<String> = listOf("Astra", "Balkan", "Creska", "Darkon", "Draskov", "Estenmoor", "Falkenburg", "Galla", "Harvenfeld", "Helmburg", "Holloway", "Inderfall", "Ivarn", "Jarnholm", "Jarnwald", "Krausenfeld", "Larpswood", "Lindenburg", "Hub-1", "Hub-2", "Hub-3", "Missions")
    val sgServers: List<String> = listOf("Asura", "Bayan", "Chantara", "Hub-1", "Missions")

    private const val ONLINE_COUNT_URL = "https://melinoe.magnetite.dev/api/telos/player/online/count"

    /** Region -> (realm -> player count), only containing realms that are currently online */
    @Volatile
    var onlineCounts: Map<String, Map<String, Int>> = emptyMap()
        private set

    @Volatile
    var maxPlayers: Int = 40
        private set

    /** Bumped on every successful fetch so open screens know to rebuild */
    @Volatile
    var onlineDataVersion: Int = 0
        private set

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
    
    fun fetchOnlineCounts() {
        CompletableFuture.runAsync {
            try {
                val response = authedGet(ONLINE_COUNT_URL)

                if (response.statusCode() == 200) {
                    val json = Gson().fromJson(response.body(), JsonObject::class.java)

                    if (json.has("maxPlayers")) maxPlayers = json.get("maxPlayers").asInt

                    if (json.has("regions")) {
                        // LinkedHashMap keeps the API's ordering (realms alphabetical, hubs last)
                        val parsed = LinkedHashMap<String, Map<String, Int>>()
                        for ((region, realmsElement) in json.getAsJsonObject("regions").entrySet()) {
                            val realms = LinkedHashMap<String, Int>()
                            for ((realm, count) in realmsElement.asJsonObject.entrySet()) {
                                realms[realm] = count.asInt
                            }
                            parsed[region] = realms
                        }
                        onlineCounts = parsed
                    }

                    onlineDataVersion++
                    Melinoe.logger.info("[RealmFetcher] Updated online realm counts (${onlineCounts.values.sumOf { it.size }} realms).")
                } else {
                    Melinoe.logger.warn("[RealmFetcher] Failed to fetch online counts. HTTP Status: ${response.statusCode()}")
                }
            } catch (e: Exception) {
                Melinoe.logger.error("[RealmFetcher] Error fetching online realm counts", e)
            }
        }
    }

    /** GETs [url] with the cached token, refreshing once on a 401. */
    private fun authedGet(url: String): HttpResponse<String> {
        val response = httpGet(url, MojangAuth.getToken())
        return if (response.statusCode() == 401) {
            MojangAuth.invalidate()
            httpGet(url, MojangAuth.getToken(forceRefresh = true))
        } else {
            response
        }
    }

    private fun httpGet(url: String, token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}