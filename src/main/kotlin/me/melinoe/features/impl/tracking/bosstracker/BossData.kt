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
        BlockPos(458, 214, -467),
        TelosItemUtils.BOSS_ANUBIS
    ),
    ASTAROTH(
        "Astaroth",
        Pattern.compile("^\\[Astaroth] Your futile struggles are mere entertainment for the denizens of the void, (.+)"),
        BlockPos(250, 228, 60),
        TelosItemUtils.BOSS_ASTAROTH
    ),
    CHUNGUS(
        "Chungus",
        Pattern.compile("^\\[Chungus] The void strengthens me, (.+)!"),
        BlockPos(61, 266, -490),
        TelosItemUtils.BOSS_CHUNGUS
    ),
    FREDDY(
        "Freddy",
        Pattern.compile("^\\[Freddy] YOU WILL NOT BE SPARED! YOU WILL NOT BE SAVED, (.+)!"),
        BlockPos(-136, 214, 653),
        TelosItemUtils.BOSS_FREDDY
    ),
    GLUMI(
        "Glumi",
        Pattern.compile("^\\[Glumi] You will not access the sacred caverns, (.+)!"),
        BlockPos(317, 200, 558),
        TelosItemUtils.BOSS_GLUMI
    ),
    ILLARIUS(
        "Illarius",
        Pattern.compile("^\\[Illarius] Don't send me back to Loa, (.+)!"),
        BlockPos(479, 211, -44),
        TelosItemUtils.BOSS_ILLARIUS
    ),
    LOTIL(
        "Lotil",
        Pattern.compile("^\\[Lotil] You will NOT take my symbolic shield away from me, (.+)!"),
        BlockPos(-138, 223, 17),
        TelosItemUtils.BOSS_LOTIL
    ),
    OOZUL(
        "Oozul",
        Pattern.compile("^\\[Oozul] Don't expose mortals such as (.+) to Chronos!"),
        BlockPos(-424, 205, 91),
        TelosItemUtils.BOSS_OOZUL
    ),
    TIDOL(
        "Tidol",
        Pattern.compile("^\\[Tidol] Face my trident, (.+)!"),
        BlockPos(-543, 199, 364),
        TelosItemUtils.BOSS_TIDOL
    ),
    VALUS(
        "Valus",
        Pattern.compile("^\\[Valus] You are not worthy of joining our worship, (.+)!"),
        BlockPos(35, 220, 307),
        TelosItemUtils.BOSS_VALUS
    ),
    HOLLOWBANE(
        "Hollowbane",
        Pattern.compile("^\\[Hollowbane] Hollow is your fate, as it is mine (.+)!"),
        BlockPos(233, 200, 703),
        TelosItemUtils.BOSS_HOLLOWBANE
    ),
    CLAUS(
        "Claus",
        Pattern.compile("^\\[Claus] The only sleighing happening here is YOU, (.+)!"),
        BlockPos(10, 222, -122),
        TelosItemUtils.BOSS_CLAUS
    ),
    RAPHAEL(
        "Raphael",
        Pattern.compile("^\\[Raphael] .+"), // Dummy pattern - Raphael doesn't have player calls
        BlockPos(-15, 243, 88),
        TelosItemUtils.BOSS_RAPHAEL
    ),
    DEFENDER(
        "Defender",
        Pattern.compile("^\\[Defender] I stood for honor, but honor was my undoing (.+)!"),
        BlockPos(65, -51, 64),
        TelosItemUtils.BOSS_DEFENDER
    ),
    REAPER(
        "Reaper",
        Pattern.compile("^\\[Reaper] We never knew what fate had in store for us, (.+). And yet, dead men tell no tales."),
        BlockPos(22, -47, 323),
        TelosItemUtils.BOSS_REAPER
    ),
    HERALD(
        "Herald",
        Pattern.compile("^\\[Herald] The flames cast deep shadows, (.+). There’s a tragic tale within them, growing darker the further you stray."),
        BlockPos(148, -47, -176),
        TelosItemUtils.BOSS_HERALD
    ),
    WARDEN(
        "Warden",
        Pattern.compile("^\\[Warden] Look at it, (.+). This battle standard is all I have left to honour my fallen brethren."),
        BlockPos(-125, -46, -122),
        TelosItemUtils.BOSS_WARDEN
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