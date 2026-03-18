package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * Hide Armor module - visually hides all equipped armor on the player.
 * Armor still provides protection, it's just not rendered.
 */
object HideArmorModule : Module(
    name = "Hide Armor",
    category = Category.VISUAL,
    description = "Visually hides all equipped armor on the player"
) {
    val hideOthers by BooleanSetting("Hide for Others", true, "Hide armor pieces for other players")
    val hideHelmet by BooleanSetting("Hide Helmet", true, "Hide helmet armor piece")
    val hideChestplate by BooleanSetting("Hide Chestplate", true, "Hide chestplate armor piece")
    val hideLeggings by BooleanSetting("Hide Leggings", true, "Hide leggings armor piece")
    val hideBoots by BooleanSetting("Hide Boots", true, "Hide boots armor piece")
}
