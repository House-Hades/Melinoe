package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * Fullbright module using ambient light modification
 * Based on NoFrills' Ambient mode - cleanest fullbright with no visual overlay
 */
object FullbrightModule : Module(
    name = "Fullbright",
    category = Category.VISUAL,
    description = "Provides full brightness using ambient light"
)
