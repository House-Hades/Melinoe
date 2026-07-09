package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe
import me.melinoe.network.MapTileDownloader
import java.io.File

/**
 * Credit to Wynntils for practically everything related to the Map.
 *
 * One rectangular region of the world. The PNG uses 1 world block per pixel
 */
data class MapTileData(
    val name: String,
    val fileName: String,
    val region: String,
    val x1: Int,
    val z1: Int,
    val x2: Int,
    val z2: Int,
    val md5: String
) {
    val width get() = x2 - x1 + 1
    val height get() = z2 - z1 + 1

    /** Downloaded tiles are stored under their md5, so a tile update is a new file */
    val localFile: File get() = File(File(File(Melinoe.configFile, "data"), "map"), "$md5.png")

    /** Classpath location of the copy shipped in the jar, if any */
    val bundledResource: String get() = "/assets/melinoe/data/$fileName"
}

/**
 * Map tile manifest. Loaded at runtime by TelosData.
 */
object MapData {

    private val gson = Gson()

    @Volatile
    var tiles: List<MapTileData> = emptyList()
        private set

    /** Tiles belonging to one map region (main, shadowlands, radiant_isles, ...) */
    fun tilesFor(region: String): List<MapTileData> = tiles.filter { it.region == region }

    /** Union world rect of a region's tiles as (minX, minZ, maxX, maxZ), or null when it has none */
    fun bounds(region: String): IntArray? =
        tilesFor(region).takeIf { it.isNotEmpty() }?.let { t ->
            intArrayOf(t.minOf { it.x1 }, t.minOf { it.z1 }, t.maxOf { it.x2 }, t.maxOf { it.z2 })
        }

    /**
     * Parses the manifest, swaps the registry, and kicks off downloads for missing tiles.
     * An empty tile list is a valid manifest (no imagery published yet)
     */
    fun load(json: String): Boolean {
        val raw = gson.fromJson(json, RawManifest::class.java)?.tiles ?: return false
        tiles = raw.mapNotNull { tile ->
            if (tile.name == null || tile.fileName == null || tile.md5 == null ||
                tile.x1 == null || tile.z1 == null || tile.x2 == null || tile.z2 == null ||
                tile.x2 < tile.x1 || tile.z2 < tile.z1
            ) {
                Melinoe.logger.warn("[TelosData] Skipping malformed map tile: ${tile.name}")
                null
            } else {
                MapTileData(tile.name, tile.fileName, tile.region ?: "main", tile.x1, tile.z1, tile.x2, tile.z2, tile.md5)
            }
        }
        MapTileDownloader.ensureTiles()
        return true
    }

    private data class RawManifest(val tiles: List<RawTile>?)
    private data class RawTile(
        val name: String?,
        val fileName: String?,
        val region: String?,
        val x1: Int?,
        val z1: Int?,
        val x2: Int?,
        val z2: Int?,
        val md5: String?
    )
}
