package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe

/**
 * Definition of the Telos season pass shown on the profile overview.
 */
object SeasonPassData {

    /** Number of pages shown by the paginator. */
    @Volatile
    var PAGES = 5
        private set

    /** Tiles per page (the grid). */
    @Volatile
    var PER_PAGE = 10
        private set

    /** 1-based positions within each page that belong to the premium track. */
    @Volatile
    private var premiumPositions: Set<Int> = setOf(3, 8)

    /**
     * A single season pass tier. Rewards can be stickers OR items, so the [texture] is set
     * directly as a full resource path (with extension).
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

    /** The full season pass, in tier order. */
    @Volatile
    var rewards: List<Reward> = emptyList()
        private set

    private val gson = Gson()

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

    /** Parses the season pass data set and rebuilds the reward list. Returns true if it had tiers. */
    fun load(json: String): Boolean = try {
        val raw = gson.fromJson(json, RawSeasonPass::class.java)
        val tiers = raw?.tiers.orEmpty().filter { it.texture != null }
        if (tiers.isEmpty()) {
            false
        } else {
            PAGES = raw?.pages ?: 5
            PER_PAGE = raw?.perPage ?: 10
            premiumPositions = raw?.premiumPositions?.toSet() ?: setOf(3, 8)
            rewards = tiers.mapIndexed { i, t ->
                val tier = i + 1
                val posInPage = ((tier - 1) % PER_PAGE) + 1
                Reward(
                    tier = tier,
                    texture = "${t.texture}.png",
                    displayName = nameFor(t.texture!!),
                    xpRequired = t.xp,
                    premium = posInPage in premiumPositions
                )
            }
            true
        }
    } catch (e: Exception) {
        Melinoe.logger.warn("[SeasonPassData] Failed to parse season pass: ${e.message}")
        false
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

    // === RAW JSON MODELS ===
    private data class RawSeasonPass(
        val pages: Int?,
        val perPage: Int?,
        val premiumPositions: List<Int>?,
        val tiers: List<RawTier>?
    )

    private data class RawTier(val texture: String?, val xp: Long = 0)
}
