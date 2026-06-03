package me.melinoe.network

import me.melinoe.Melinoe
import me.melinoe.utils.data.TelosData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Fetches the Telos data sets from GitHub on launch so item/boss/dungeon/portal data can be
 * updated without releasing a new mod build.
 */
object TelosDataFetcher {
    
    private const val ITEMS_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/items.json"
    private const val BOSSES_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/bosses.json"
    private const val DUNGEONS_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/dungeons.json"
    private const val PORTALS_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/portals.json"

    private val urls: Map<TelosData.Type, String> = mapOf(
        TelosData.Type.ITEMS to ITEMS_URL,
        TelosData.Type.BOSSES to BOSSES_URL,
        TelosData.Type.DUNGEONS to DUNGEONS_URL,
        TelosData.Type.PORTALS to PORTALS_URL,
    )

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    /** Fetches every data set in the background and applies it to the registries */
    fun fetchAll() {
        for ((type, url) in urls) {
            if (url.isBlank()) continue // No URL set, keep the baseline/cache
            CompletableFuture.runAsync { fetch(type, url) }
        }
    }

    private fun fetch(type: TelosData.Type, url: String) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                if (TelosData.reload(type, response.body())) {
                    Melinoe.logger.info("[TelosDataFetcher] Updated $type from GitHub.")
                } else {
                    Melinoe.logger.warn("[TelosDataFetcher] Rejected invalid $type payload; keeping existing data.")
                }
            } else {
                Melinoe.logger.warn("[TelosDataFetcher] Failed to fetch $type. HTTP Status: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("[TelosDataFetcher] Error fetching $type from web", e)
        }
    }
}
