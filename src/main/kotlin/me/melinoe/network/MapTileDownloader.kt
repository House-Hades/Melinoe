package me.melinoe.network

import me.melinoe.Melinoe
import me.melinoe.utils.data.MapData
import me.melinoe.utils.data.MapTileData
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Credit to Wynntils for practically everything related to the Map.
 *
 * Downloads the map tile PNGs referenced by the map manifest. TelosDataFetcher only handles
 * JSON payloads, so binary tiles get their own downloader with md5 verification
 */
object MapTileDownloader {

    private const val BASE_URL = "https://raw.githubusercontent.com/House-Hades/melinoe-data/refs/heads/main/"

    enum class Status { MISSING, DOWNLOADING, READY, FAILED }

    // Keyed by tile md5
    private val statuses = ConcurrentHashMap<String, Status>()

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    fun status(tile: MapTileData): Status = statuses[tile.md5] ?: Status.MISSING

    /**
     * Starts background downloads for every manifest tile that isn't cached yet.
     * FAILED tiles are only retried when [retryFailed] is set, so a dead URL doesn't
     * get hammered every time the map opens.
     */
    fun ensureTiles(retryFailed: Boolean = false) {
        for (tile in MapData.tiles) {
            when (statuses[tile.md5]) {
                Status.READY, Status.DOWNLOADING -> continue
                Status.FAILED -> if (!retryFailed) continue
                else -> {}
            }
            if (tile.localFile.isFile) {
                statuses[tile.md5] = Status.READY
                continue
            }
            statuses[tile.md5] = Status.DOWNLOADING
            CompletableFuture.runAsync { fetch(tile) }
        }
    }

    /** Seeds the cache from the copy shipped in the jar when it matches the manifest, else downloads */
    private fun fetch(tile: MapTileData) {
        val bundled = runCatching {
            javaClass.getResourceAsStream(tile.bundledResource)?.use { it.readBytes() }
        }.getOrNull()
        if (bundled != null && md5(bundled).equals(tile.md5, ignoreCase = true)) {
            try {
                writeTile(tile, bundled)
                statuses[tile.md5] = Status.READY
                Melinoe.logger.info("[MapTileDownloader] Seeded map tile ${tile.name} from bundled resources")
                return
            } catch (e: Exception) {
                Melinoe.logger.warn("[MapTileDownloader] Failed to seed tile ${tile.name} from bundle: ${e.message}")
            }
        }
        download(tile)
    }

    private fun download(tile: MapTileData) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + tile.fileName))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200) {
                statuses[tile.md5] = Status.FAILED
                Melinoe.logger.warn("[MapTileDownloader] Failed to fetch tile ${tile.name}. HTTP Status: ${response.statusCode()}")
                return
            }

            val bytes = response.body()
            val md5 = md5(bytes)
            if (!md5.equals(tile.md5, ignoreCase = true)) {
                statuses[tile.md5] = Status.FAILED
                Melinoe.logger.warn("[MapTileDownloader] md5 mismatch for tile ${tile.name} (expected ${tile.md5}, got $md5); discarding")
                return
            }

            writeTile(tile, bytes)
            statuses[tile.md5] = Status.READY
            Melinoe.logger.info("[MapTileDownloader] Downloaded map tile ${tile.name}")
        } catch (e: Exception) {
            statuses[tile.md5] = Status.FAILED
            Melinoe.logger.error("[MapTileDownloader] Error downloading map tile ${tile.name}", e)
        }
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeTile(tile: MapTileData, bytes: ByteArray) {
        val file = tile.localFile
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
