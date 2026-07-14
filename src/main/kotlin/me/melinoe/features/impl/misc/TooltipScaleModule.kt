package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * Customize the scale of tooltips.
 */
object TooltipScaleModule : Module(
    name = "Tooltip Scale",
    description = "Customize the scale of tooltips",
    category = Category.MISC
) {
    val scale by NumberSetting("Scale", 1.0, 0.5, 1.0, 0.01, "The tooltip scale multiplier.")
}
