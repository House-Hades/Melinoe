package me.melinoe.network

import me.melinoe.Melinoe
import me.melinoe.utils.data.TelosData
import java.io.File
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
    
    private const val BASE_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/"
    private const val ITEMS_URL = "${BASE_URL}items.json"
    private const val BOSSES_URL = "${BASE_URL}bosses.json"
    private const val DUNGEONS_URL = "${BASE_URL}dungeons.json"
    private const val PORTALS_URL = "${BASE_URL}portals.json"
    private const val COMPANIONS_URL = "${BASE_URL}companions.json"
    private const val SEASON_PASS_URL = "${BASE_URL}season_pass.json"
    private const val CLASSES_URL = "${BASE_URL}classes.json"
    private const val TRAITS_URL = "${BASE_URL}traits.json"
    private const val MAP_URL = "${BASE_URL}map.json"
    private const val MINIBOSSES_URL = "${BASE_URL}minibosses.json"

    private val urls: Map<TelosData.Type, String> = mapOf(
        TelosData.Type.ITEMS to ITEMS_URL,
        TelosData.Type.BOSSES to BOSSES_URL,
        TelosData.Type.DUNGEONS to DUNGEONS_URL,
        TelosData.Type.PORTALS to PORTALS_URL,
        TelosData.Type.COMPANIONS to COMPANIONS_URL,
        TelosData.Type.SEASON_PASS to SEASON_PASS_URL,
        TelosData.Type.CLASSES to CLASSES_URL,
        TelosData.Type.TRAITS to TRAITS_URL,
        TelosData.Type.MAP to MAP_URL,
        TelosData.Type.MINIBOSSES to MINIBOSSES_URL,
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
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()

            // Only ask for a 304 if we still have the cached payload the ETag belongs to
            val cachedEtag = readEtag(type)
            if (cachedEtag != null) builder.header("If-None-Match", cachedEtag)

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                304 -> Melinoe.logger.info("[TelosDataFetcher] $type unchanged; keeping cached data.")
                200 -> {
                    if (TelosData.reload(type, response.body())) {
                        response.headers().firstValue("ETag").ifPresent { writeEtag(type, it) }
                        Melinoe.logger.info("[TelosDataFetcher] Updated $type from GitHub.")
                    } else {
                        Melinoe.logger.warn("[TelosDataFetcher] Rejected invalid $type payload; keeping existing data.")
                    }
                }
                else -> Melinoe.logger.warn("[TelosDataFetcher] Failed to fetch $type. HTTP Status: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("[TelosDataFetcher] Error fetching $type from web", e)
        }
    }

    private fun etagFile(type: TelosData.Type): File =
        File(type.cacheFile.parentFile, "${type.cacheFile.name}.etag")

    private fun readEtag(type: TelosData.Type): String? {
        if (!type.cacheFile.isFile) return null
        return runCatching { etagFile(type).takeIf(File::isFile)?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun writeEtag(type: TelosData.Type, etag: String) {
        runCatching {
            val file = etagFile(type)
            file.parentFile?.mkdirs()
            file.writeText(etag)
        }.onFailure { Melinoe.logger.warn("[TelosDataFetcher] Failed to save ETag for $type: ${it.message}") }
    }
}
