package me.melinoe.utils.data

/**
 * Static definition of the Telos season pass shown on the profile overview.
 *
 * The player API only tells us the pass tier XP and the premium flag, not which reward sits at
 * each tier. That mapping (texture + XP requirement) lives here. Rewards are mostly normal items;
 * each tier's [Reward.texture] is a full `telos:material/...` path, and every 3rd and 8th tier on
 * each 10-tile page is premium-only.
 */
object SeasonPassData {
    
    /** Number of pages shown by the paginator. */
    const val PAGES = 5
    
    /** Tiles per page (the 5x2 grid). */
    const val PER_PAGE = 10
    
    /**
     * A single season pass tier. Rewards can be stickers OR items, so the [texture] is set
     * directly as a full resource path (with extension), e.g.
     *  - item:    `telos:material/weapon/sword/ex-microphone.png`
     */
    data class Reward(
        /** 1-based position in the pass. */
        val tier: Int,
        /** Full texture resource path, including the `.png` extension. */
        val texture: String,
        /** Human-readable reward name shown in the preview/tooltip. */
        val displayName: String,
        /** Total season pass XP required to reach this tier. */
        val xpRequired: Long,
        /** Whether this tier is part of the premium track. */
        val premium: Boolean
    )
    
    // Each tier's reward texture (without extension) and its XP requirement, in order.
    private val tierData: List<Pair<String, Long>> = listOf(
        "telos:material/crate/uncommon" to 8_000L,
        "telos:material/misc/soulpoint1" to 16_500L,
        "telos:material/mount/lava_whale" to 25_000L,
        "telos:material/fragment/gilded" to 33_000L,
        "telos:material/misc/character_slot" to 42_000L,
        "telos:material/crate/strange" to 51_000L,
        "telos:material/sticker/clueless_dog" to 61_000L,
        "telos:material/crate/epic" to 70_000L,
        "telos:material/pouch/gilded" to 80_000L,
        "telos:material/dungeon/shatters" to 91_000L,
        "telos:material/crate/uncommon" to 101_000L,
        "telos:material/fragment/gilded" to 112_000L,
        "telos:material/sticker/disintegrating_squirrel" to 124_000L,
        "telos:material/misc/soulpoint2" to 135_000L,
        "telos:material/crate/rare" to 148_000L,
        "telos:material/crate/rare" to 160_000L,
        "telos:material/fragment/royal" to 173_000L,
        "telos:material/misc/stash_slot" to 187_000L,
        "telos:material/crate/strange" to 201_000L,
        "telos:material/dungeon/celestials_province" to 216_000L,
        "telos:material/crate/epic" to 231_000L,
        "telos:material/misc/soulpoint3" to 246_000L,
        "telos:material/crate/fabled" to 263_000L,
        "telos:material/misc/stash_slot" to 280_000L,
        "telos:material/misc/dungeon_legendary" to 297_000L,
        "telos:material/crate/uncommon" to 315_000L,
        "telos:material/sticker/voided_dog" to 334_000L,
        "telos:material/pouch/royal" to 354_000L,
        "telos:material/fragment/bloodshot" to 374_000L,
        "telos:material/misc/sigil" to 395_000L,
        "telos:material/crate/rare" to 417_000L,
        "telos:material/misc/soulpoint4" to 439_000L,
        "telos:material/sticker/uhhhh_monkey" to 462_000L,
        "telos:material/dungeon/neo_eden" to 487_000L,
        "telos:material/misc/character_slot" to 511_000L,
        "telos:material/crate/strange" to 537_000L,
        "telos:material/pouch/royal" to 564_000L,
        "telos:material/fragment/bloodshot" to 591_000L,
        "telos:material/crate/epic" to 620_000L,
        "telos:material/dungeon/tenebris" to 649_000L,
        "telos:material/crate/strange" to 680_000L,
        "telos:material/misc/soulpoint5" to 711_000L,
        "telos:material/pet/endydragon" to 743_000L,
        "telos:material/misc/sigil" to 777_000L,
        "telos:material/crate/epic" to 811_000L,
        "telos:material/fragment/bloodshot" to 847_000L,
        "telos:material/crate/fabled" to 883_000L,
        "telos:material/crate/legendary" to 921_000L,
        "telos:material/pouch/bloodshot" to 960_000L,
        "telos:material/crate/exotic" to 1_000_000L
    )
    
    /** Builds a readable name from a texture path, e.g. "crate/uncommon" -> "Uncommon Crate". */
    private fun nameFor(path: String): String {
        val parts = path.substringAfter("telos:material/").split('/')
        val category = parts.firstOrNull().orEmpty()
        val raw = parts.last()
        if (raw.startsWith("soulpoint")) return "Soul Points ${raw.removePrefix("soulpoint")}"
        val pretty = raw.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        return when (category) {
            "crate" -> "$pretty Crate"
            "fragment" -> "$pretty Fragment"
            "pouch" -> "$pretty Pouch"
            "dungeon" -> "$pretty Dungeon"
            else -> pretty
        }
    }
    
    /** The full season pass. Every 3rd and 8th tier on each 10-tile page is premium-only. */
    val rewards: List<Reward> = tierData.mapIndexed { i, (path, xp) ->
        val tier = i + 1
        val posInPage = ((tier - 1) % PER_PAGE) + 1
        Reward(
            tier = tier,
            texture = "$path.png",
            displayName = nameFor(path),
            xpRequired = xp,
            premium = posInPage == 3 || posInPage == 8
        )
    }
    
    /** Rewards on [pageIndex] (0-based), in tile order. */
    fun page(pageIndex: Int): List<Reward> =
        rewards.drop(pageIndex * PER_PAGE).take(PER_PAGE)
    
    /**
     * Whether the player has earned [reward] given their pass [xp] and [premium] status.
     * Premium-track tiers also require the premium pass.
     */
    fun isUnlocked(reward: Reward, xp: Long, premium: Boolean): Boolean =
        xp >= reward.xpRequired && (!reward.premium || premium)
}
