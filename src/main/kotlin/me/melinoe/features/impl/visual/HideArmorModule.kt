package me.melinoe.features.impl.visual

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
)
