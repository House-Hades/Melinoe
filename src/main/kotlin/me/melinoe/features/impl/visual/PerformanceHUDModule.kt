package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.utils.Color
import me.melinoe.utils.Colors
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.toFixed
import me.melinoe.utils.render.textDim
import net.minecraft.client.gui.GuiGraphics

/**
 * Performance HUD Module - displays FPS, TPS, and ping.
 */
object PerformanceHUDModule : Module(
    name = "Performance HUD",
    category = Category.VISUAL,
    description = "Shows performance information on the screen."
) {
    private val nameColor by ColorSetting("Name Color", Color(0xFF7CFFB2.toInt()), desc = "The color of the stat information.")
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "The color of the stat values.")
    private val direction by SelectorSetting("Direction", "Horizontal", listOf("Horizontal", "Vertical"), "Direction the information is displayed.")
    private val showFPS by BooleanSetting("Show FPS", true, desc = "Shows the FPS in the HUD.")
    private val showTPS by BooleanSetting("Show TPS", true, desc = "Shows the TPS in the HUD.")
    private val showPing by BooleanSetting("Show Ping", true, desc = "Shows the ping in the HUD.")

    private const val HORIZONTAL = 0

    private val hud by HUDSetting(
        name = "Performance Display",
        x = 10,
        y = 10,
        scale = 2f,
        toggleable = false,
        description = "Shows performance information on the screen.",
        module = this
    ) { example ->
        if (!showFPS && !showTPS && !showPing && !example) return@HUDSetting 0 to 0

        var width = 1
        var height = 1
        val lineHeight = mc.font.lineHeight

        fun renderMetric(label: String, value: String) {
            val w = drawText(label, value, if (direction == HORIZONTAL) width else 1, height)
            if (direction == HORIZONTAL) width += w
            else {
                width = maxOf(width, w)
                height += lineHeight
            }
        }

        if (showTPS || example) renderMetric("TPS: ", "${ServerUtils.averageTps.toFixed(1)} ")
        if (showFPS || example) renderMetric("FPS: ", "${mc.fps} ")
        if (showPing || example) renderMetric("Ping: ", "${ServerUtils.averagePing}ms ")

        width to if (direction == HORIZONTAL) lineHeight else height
    }

    private fun GuiGraphics.drawText(name: String, value: String, x: Int, y: Int): Int {
        var width = 0
        width += textDim(name, x, y, nameColor, true).first
        width += textDim(value, x + width, y, valueColor, true).first
        return width
    }
}
