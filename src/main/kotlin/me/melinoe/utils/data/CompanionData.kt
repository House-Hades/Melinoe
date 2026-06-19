package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe

/**
 * Catalog of every pet and mount in the game, with each companion's rarity.
 *
 * The player API only returns unlocked companions, so the full list (to show locked ones too)
 * and the rarity of each is loaded from the Telos data set (`companions.json`)
 */
object CompanionData {

    /** The companion rarities, in ascending order, with their display colours. */
    enum class Rarity(val display: String, val color: Int) {
        USUAL("Usual", 0xFF80787F.toInt()),
        STRANGE("Strange", 0xFF1A884E.toInt()),
        FABLED("Fabled", 0xFF1E2AA0.toInt()),
        EXOTIC("Exotic", 0xFF712082.toInt()),
        LEGACY("Legacy", 0xFFB3590E.toInt())
    }

    /** A single companion entry; [id] is the API key, e.g. "pet/onyx" or "mount/onyx". */
    data class Companion(val id: String, val rarity: Rarity)

    /**
     * Number of starter ranks. The starter pet/mount ranks up each time a class is taken to max
     * soul points, giving variants: "starter", "starter1" ... "starter6" (7 total).
     */
    @Volatile
    var STARTER_COUNT = 7
        private set

    // Each companion dungeon mapped to its rarity (the dungeon's pet and mount share it).
    @Volatile
    private var dungeonRarity: Map<String, Rarity> = emptyMap()

    /** All pets, in catalog order (starter expanded to its ranks). */
    @Volatile
    var pets: List<Companion> = emptyList()
        private set

    /** All mounts, in catalog order (starter expanded to its ranks). */
    @Volatile
    var mounts: List<Companion> = emptyList()
        private set

    private val gson = Gson()

    /** Expands a dungeon id into its companion variants (the starter has [STARTER_COUNT], else 1). */
    private fun variants(dungeon: String): List<String> =
        if (dungeon == "starter") (0 until STARTER_COUNT).map { if (it == 0) "starter" else "starter$it" }
        else listOf(dungeon)

    /**
     * The starter rank (0-6) of a companion [id] like "pet/starter3", or null if it isn't a
     * starter companion. Rank N is unlocked once the player has N classes at max soul points.
     */
    fun starterRank(id: String): Int? {
        val name = id.substringAfter('/')
        if (!name.startsWith("starter")) return null
        val suffix = name.removePrefix("starter")
        return if (suffix.isEmpty()) 0 else suffix.toIntOrNull()
    }

    /** Parses the companions data set and rebuilds the catalog. Returns true if it had entries. */
    fun load(json: String): Boolean = try {
        val raw = gson.fromJson(json, RawCompanions::class.java)
        val ordered = LinkedHashMap<String, Rarity>()
        raw?.companions.orEmpty().forEach { entry ->
            val rarity = runCatching { Rarity.valueOf(entry.rarity ?: "") }.getOrNull()
            if (entry.dungeon == null || rarity == null) {
                Melinoe.logger.warn("[CompanionData] Skipping malformed companion: ${entry.dungeon}")
            } else {
                ordered[entry.dungeon] = rarity
            }
        }
        if (ordered.isEmpty()) {
            false
        } else {
            STARTER_COUNT = raw?.starterCount ?: 7
            dungeonRarity = ordered
            pets = ordered.flatMap { (id, rarity) -> variants(id).map { Companion("pet/$it", rarity) } }
            mounts = ordered.flatMap { (id, rarity) -> variants(id).map { Companion("mount/$it", rarity) } }
            true
        }
    } catch (e: Exception) {
        Melinoe.logger.warn("[CompanionData] Failed to parse companions: ${e.message}")
        false
    }

    // === RAW JSON MODELS ===
    private data class RawCompanions(val starterCount: Int?, val companions: List<RawCompanion>?)
    private data class RawCompanion(val dungeon: String?, val rarity: String?)
}
