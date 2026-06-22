package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.LocalAPI
import net.minecraft.network.chat.Component

/**
 * Boss Bar Scale module - shrinks a real boss's boss bar so it takes up less
 * screen space
 *
 * A bar is considered a real boss bar when its name hash maps to a known boss via
 * [LocalAPI.getBossNameFromHash]
 */
object BossBarScaleModule : Module(
    name = "Boss Bar Scale",
    category = Category.VISUAL,
    description = "Scales down a boss's boss bar."
) {
    @JvmStatic
    val scale by NumberSetting("Scale", 0.7f, 0.25f, 1f, 0.05f, desc = "Size of the boss bar relative to vanilla")

    /**
     * Whether the bar with the given name should be scaled this frame
     */
    @JvmStatic
    fun shouldScale(name: Component): Boolean =
        enabled && scale != 1f && LocalAPI.getBossNameFromHash(name.hashCode()).isNotEmpty()
}