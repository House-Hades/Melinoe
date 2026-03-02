package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.utils.TelosItemUtils
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

/**
 * Boss data enum with spawn positions, patterns, and model identifiers
 */
enum class BossData(
    val label: String,
    val playerCallPattern: Pattern,
    val spawnPosition: BlockPos,
    val modelIdentifier: ResourceLocation
) {
    ANUBIS(
        "Anubis",
        Pattern.compile("^\\[Anubis] You will not disturb their peace, (.+)!"),
        BlockPos(458, 204, -467),
        TelosItemUtils.BOSS_ANUBIS
    ),
    ASTAROTH(
        "Astaroth",
        Pattern.compile("^\\[Astaroth] Your futile struggles are mere entertainment for the denizens of the void, (.+)"),
        BlockPos(250, 217, 60),
        TelosItemUtils.BOSS_ASTAROTH
    ),
    CHUNGUS(
        "Chungus",
        Pattern.compile("^\\[Chungus] The void strengthens me, (.+)!"),
        BlockPos(61, 256, -490),
        TelosItemUtils.BOSS_CHUNGUS
    ),
    FREDDY(
        "Freddy",
        Pattern.compile("^\\[Freddy] YOU WILL NOT BE SPARED! YOU WILL NOT BE SAVED, (.+)!"),
        BlockPos(-136, 200, 653),
        TelosItemUtils.BOSS_FREDDY
    ),
    GLUMI(
        "Glumi",
        Pattern.compile("^\\[Glumi] You will not access the sacred caverns, (.+)!"),
        BlockPos(339, 222, 552),
        TelosItemUtils.BOSS_GLUMI
    ),
    ILLARIUS(
        "Illarius",
        Pattern.compile("^\\[Illarius] Don't send me back to Loa, (.+)!"),
        BlockPos(478, 200, -45),
        TelosItemUtils.BOSS_ILLARIUS
    ),
    LOTIL(
        "Lotil",
        Pattern.compile("^\\[Lotil] You will NOT take my symbolic shield away from me, (.+)!"),
        BlockPos(-138, 214, 17),
        TelosItemUtils.BOSS_LOTIL
    ),
    OOZUL(
        "Oozul",
        Pattern.compile("^\\[Oozul] Don't expose mortals such as (.+) to Chronos!"),
        BlockPos(-424, 195, 91),
        TelosItemUtils.BOSS_OOZUL
    ),
    TIDOL(
        "Tidol",
        Pattern.compile("^\\[Tidol] Face my trident, (.+)!"),
        BlockPos(-543, 190, 364),
        TelosItemUtils.BOSS_TIDOL
    ),
    VALUS(
        "Valus",
        Pattern.compile("^\\[Valus] You are not worthy of joining our worship, (.+)!"),
        BlockPos(35, 210, 307),
        TelosItemUtils.BOSS_VALUS
    ),
    HOLLOWBANE(
        "Hollowbane",
        Pattern.compile("^\\[Hollowbane] Hollow is your fate, as it is mine (.+)!"),
        BlockPos(232, 150, 696),
        TelosItemUtils.BOSS_HOLLOWBANE
    ),
    RAPHAEL(
        "Raphael",
        Pattern.compile("^\\[Raphael] .+"), // Dummy pattern - Raphael doesn't have player calls
        BlockPos(-15, 243, 88),
        TelosItemUtils.BOSS_RAPHAEL
    );

    /**
     * Create an ItemStack for this boss icon
     */
    fun createItemStack(): ItemStack {
        return TelosItemUtils.createItemStack(modelIdentifier)
    }

    companion object {
        fun fromString(name: String): BossData? {
            return try {
                valueOf(name.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
