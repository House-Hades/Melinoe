package me.melinoe.utils.data

/**
 * Catalog of every pet and mount in the game, with each companion's rarity.
 *
 * The player API only returns unlocked companions, so the full list (to show locked ones too)
 * and the rarity of each lives here. Each dungeon has a pet (`pet-<id>`) and a mount
 * (`mount-<id>`); icons resolve through [TelosItems] (`telos:material/pet|mount/<id>.png`).
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
    
    /** A single companion entry; [id] is the API key, e.g. "pet-onyx" or "mount-onyx". */
    data class Companion(val id: String, val rarity: Rarity)
    
    // Each companion dungeon mapped to its rarity (the dungeon's pet and mount share it).
    // These are rough guesses for now, still need the real rarities + any missing dungeons.
    private val dungeonRarity: Map<String, Rarity> = linkedMapOf(
        "skull_cavern" to Rarity.USUAL,
        "thornwood_wargrove" to Rarity.USUAL,
        "goblin_lair" to Rarity.USUAL,
        "sakura_shrine" to Rarity.USUAL,
        "secluded_woodland" to Rarity.USUAL,
        "desert_temple" to Rarity.USUAL,
        "tomb_of_shadows" to Rarity.USUAL,
        
        "abyss_of_demons" to Rarity.STRANGE,
        "ice_cave" to Rarity.STRANGE,
        "dwarven_frostkeep" to Rarity.STRANGE,
        "undead_lair" to Rarity.STRANGE,
        "treasure_cave" to Rarity.STRANGE,
        "kobolds_den" to Rarity.STRANGE,
        "purgatory" to Rarity.STRANGE,
        "corvus_crypt" to Rarity.STRANGE,
        "frozen_ruins" to Rarity.STRANGE,
        
        "omnipotents_citadel" to Rarity.FABLED,
        "fungal_cavern" to Rarity.FABLED,
        "corsairs_conductorium" to Rarity.FABLED,
        "chronos" to Rarity.FABLED,
        "freddys_pizzeria" to Rarity.FABLED,
        "anubis_lair" to Rarity.FABLED,
        "tartarus" to Rarity.FABLED,
        
        "cultists_hideout" to Rarity.EXOTIC,
        "illarius_hideout" to Rarity.EXOTIC,
        "aurora_sanctum" to Rarity.EXOTIC,
        "aviary" to Rarity.EXOTIC,
        "dreadwood_thicket" to Rarity.EXOTIC,
        "shatters" to Rarity.EXOTIC,
        "resounding_ruins" to Rarity.EXOTIC,
        "onyx" to Rarity.EXOTIC,
        "celestials_province" to Rarity.EXOTIC,
        "tenebris" to Rarity.EXOTIC,
        "neo_eden" to Rarity.EXOTIC,
        
        "starter" to Rarity.LEGACY
        )
    
    /**
     * Number of starter ranks. The starter pet/mount ranks up each time a class is taken to max
     * soul points, giving variants: "starter", "starter1" ... "starter6" (7 total).
     */
    const val STARTER_COUNT = 7
    
    /** Expands a dungeon id into its companion variants (the starter has 7, everything else 1). */
    private fun variants(dungeon: String): List<String> =
        if (dungeon == "starter") (0 until STARTER_COUNT).map { if (it == 0) "starter" else "starter$it" }
        else listOf(dungeon)
    
    /**
     * The starter rank (0-6) of a companion [id] like "pet-starter3", or null if it isn't a
     * starter companion. Rank N is unlocked once the player has N classes at max soul points.
     */
    fun starterRank(id: String): Int? {
        val name = id.substringAfter('-')
        if (!name.startsWith("starter")) return null
        val suffix = name.removePrefix("starter")
        return if (suffix.isEmpty()) 0 else suffix.toIntOrNull()
    }
    
    /** All pets, in catalog order (starter expanded to its 7 ranks). */
    val pets: List<Companion> = dungeonRarity.flatMap { (id, rarity) -> variants(id).map { Companion("pet-$it", rarity) } }
    
    /** All mounts, in catalog order (starter expanded to its 7 ranks). */
    val mounts: List<Companion> = dungeonRarity.flatMap { (id, rarity) -> variants(id).map { Companion("mount-$it", rarity) } }
}
