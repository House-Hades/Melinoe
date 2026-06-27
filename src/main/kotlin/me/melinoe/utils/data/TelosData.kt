package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe
import net.minecraft.core.BlockPos
import java.io.File

/**
 * Loads the Telos data sets (items, bosses, dungeons, portals) from file so they can be
 * updated from GitHub without updating the mod
 */
object TelosData {

    /**
     * One data set, resource is the bundled baseline, cacheFile is the saved remote copy
     */
    enum class Type(val resource: String, val cacheFileName: String) {
        ITEMS("assets/melinoe/data/items.json", "items.json"),
        BOSSES("assets/melinoe/data/bosses.json", "bosses.json"),
        DUNGEONS("assets/melinoe/data/dungeons.json", "dungeons.json"),
        PORTALS("assets/melinoe/data/portals.json", "portals.json"),
        COMPANIONS("assets/melinoe/data/companions.json", "companions.json"),
        SEASON_PASS("assets/melinoe/data/season_pass.json", "season_pass.json"),
        CLASSES("assets/melinoe/data/classes.json", "classes.json");

        val cacheFile: File get() = File(File(Melinoe.configFile, "data"), cacheFileName)
    }

    private val gson = Gson()

    /**
     * Loads every set from the bundled baseline, then overlays the disk cache if present
     */
    fun init() {
        for (type in Type.entries) {
            // Baseline should always load
            val baseline = readResource(type)
            if (baseline == null || !applyJson(type, baseline)) {
                Melinoe.logger.error("[TelosData] Failed to load bundled baseline for $type")
            }

            // Use the cached copy from a previous session if we have one
            val cacheFile = type.cacheFile
            if (cacheFile.isFile) {
                runCatching { cacheFile.readText() }
                    .getOrNull()
                    ?.let { if (applyJson(type, it)) Melinoe.logger.info("[TelosData] Loaded cached $type") }
            }
        }
    }

    /**
     * Applies a freshly fetched payload and saves it to the disk cache. Returns false if invalid
     */
    fun reload(type: Type, json: String): Boolean {
        if (!applyJson(type, json)) return false
        runCatching {
            val cacheFile = type.cacheFile
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(json)
        }.onFailure { Melinoe.logger.warn("[TelosData] Failed to cache $type: ${it.message}") }
        return true
    }

    /**
     * Parses the json and swaps it into the registry if it has at least one valid entry
     */
    private fun applyJson(type: Type, json: String): Boolean = try {
        when (type) {
            Type.ITEMS -> parseItems(json).also { if (it.isNotEmpty()) Item.setAll(it) }.isNotEmpty()
            Type.BOSSES -> parseBosses(json).also { if (it.isNotEmpty()) BossData.setAll(it) }.isNotEmpty()
            Type.DUNGEONS -> parseDungeons(json).also { if (it.isNotEmpty()) DungeonData.setAll(it) }.isNotEmpty()
            Type.PORTALS -> parsePortals(json).also { if (it.isNotEmpty()) PortalData.setAll(it) }.isNotEmpty()
            Type.COMPANIONS -> CompanionData.load(json)
            Type.SEASON_PASS -> SeasonPassData.load(json)
            Type.CLASSES -> ClassData.load(json)
        }
    } catch (e: Exception) {
        Melinoe.logger.warn("[TelosData] Failed to parse $type payload: ${e.message}")
        false
    }

    /** Reads a bundled baseline file from the jar */
    private fun readResource(type: Type): String? =
        javaClass.classLoader.getResourceAsStream(type.resource)?.bufferedReader()?.use { it.readText() }

    // === PARSING ===
    // Bad entries are skipped and logged; the payload is fine as long as one entry is valid

    private fun parseItems(json: String): List<Item> =
        gson.fromJson(json, RawItems::class.java)?.items.orEmpty().mapNotNull { raw ->
            val rarity = runCatching { Item.Rarity.valueOf(raw.rarity ?: "") }.getOrNull()
            if (raw.name == null || rarity == null || raw.displayName == null || raw.texturePath == null) {
                Melinoe.logger.warn("[TelosData] Skipping malformed item: ${raw.name}")
                null
            } else {
                Item(raw.name, rarity, raw.displayName, raw.texturePath, raw.maxPity ?: 120, raw.hasShiny ?: false)
            }
        }

    private fun parseBosses(json: String): List<BossData> =
        gson.fromJson(json, RawBosses::class.java)?.bosses.orEmpty().mapNotNull { raw ->
            val bossType = runCatching { BossType.valueOf(raw.bossType ?: "") }.getOrNull()
            if (raw.name == null || raw.label == null || bossType == null) {
                Melinoe.logger.warn("[TelosData] Skipping malformed boss: ${raw.name}")
                null
            } else {
                val spawnPosition = raw.spawnPosition?.let { BlockPos(it.x, it.y, it.z) }
                BossData(raw.name, raw.label, spawnPosition, bossType, raw.items.orEmpty())
            }
        }

    private fun parseDungeons(json: String): List<DungeonData> =
        gson.fromJson(json, RawDungeons::class.java)?.dungeons.orEmpty().mapNotNull { raw ->
            val dungeonType = runCatching { DungeonDifficulty.valueOf(raw.dungeonType ?: "") }.getOrNull()
            if (raw.name == null || raw.areaName == null || dungeonType == null || raw.finalBoss == null) {
                Melinoe.logger.warn("[TelosData] Skipping malformed dungeon: ${raw.name}")
                null
            } else {
                DungeonData(raw.name, raw.areaName, dungeonType, raw.finalBoss)
            }
        }

    private fun parsePortals(json: String): List<PortalData> =
        gson.fromJson(json, RawPortals::class.java)?.portals.orEmpty().mapNotNull { raw ->
            if (raw.name == null || raw.label == null) {
                Melinoe.logger.warn("[TelosData] Skipping malformed portal: ${raw.name}")
                null
            } else {
                PortalData(raw.name, raw.label)
            }
        }

    // === RAW JSON MODELS ===
    // These match the JSON shape. Fields are nullable so bad entries can be skipped

    private data class RawItems(val items: List<RawItem>?)
    private data class RawItem(
        val name: String?,
        val rarity: String?,
        val displayName: String?,
        val texturePath: String?,
        val maxPity: Int?,
        val hasShiny: Boolean?
    )

    private data class RawBosses(val bosses: List<RawBoss>?)
    private data class RawBoss(
        val name: String?,
        val label: String?,
        val spawnPosition: RawPos?,
        val bossType: String?,
        val items: List<String>?
    )

    private data class RawPos(val x: Int = 0, val y: Int = 0, val z: Int = 0)

    private data class RawDungeons(val dungeons: List<RawDungeon>?)
    private data class RawDungeon(
        val name: String?,
        val areaName: String?,
        val dungeonType: String?,
        val finalBoss: String?
    )

    private data class RawPortals(val portals: List<RawPortal>?)
    private data class RawPortal(val name: String?, val label: String?)
}