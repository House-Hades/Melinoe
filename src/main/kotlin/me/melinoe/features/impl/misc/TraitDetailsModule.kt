package me.melinoe.features.impl.misc

import me.melinoe.Melinoe
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.data.TraitDetailsData
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW

/**
 * Trait Details Module - While holding Shift, inserts the description of each trait in an item's
 * lore directly under the trait's name, with the stats for the trait's level filled in
 */
object TraitDetailsModule : Module(
    name = "Trait Details",
    category = Category.MISC,
    description = "Hold Shift while hovering an item to see trait descriptions"
) {

    /** Level indicator glyphs preceding trait names in lore, level 1 to 5 in order */
    private val LEVEL_GLYPHS = listOf("𖉉", "𖉈", "𖉇", "𖉆", "𖉅")

    init {
        ItemTooltipCallback.EVENT.register { _, _, _, lines ->
            if (enabled && isShiftDown()) insertDescriptions(lines)
        }
    }

    private fun isShiftDown(): Boolean {
        val windowHandle = Melinoe.mc.window.handle()
        return GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    /**
     * Finds trait name lines and inserts the formatted description one line under each
     */
    private fun insertDescriptions(lines: MutableList<Component>) {
        // The title is the widest line of the tooltip
        val maxWidth = lines.firstOrNull()?.let { Melinoe.mc.font.width(it) } ?: return
        var i = 0
        while (i < lines.size) {
            val text = lines[i].string
            val level = LEVEL_GLYPHS.indexOfFirst { text.startsWith(it) }
            if (level >= 0) {
                val name = text.substring(LEVEL_GLYPHS[level].length).trim()
                val trait = TraitDetailsData.get(name)
                if (trait != null) {
                    val style = nameStyle(lines[i], name)
                    val descLines = wrap(format(trait, level), style, maxWidth)
                    descLines.forEachIndexed { offset, line ->
                        lines.add(i + 1 + offset, Component.literal(line).withStyle(style))
                    }
                    i += descLines.size
                }
            }
            i++
        }
    }

    /**
     * The style of the part of the lore line holding the trait's name, so the description matches
     * its colour. Falls back to gray if the name can't be found in the line's components.
     */
    private fun nameStyle(line: Component, name: String): Style {
        val part = line.siblings.firstOrNull { it.string.trim().equals(name, ignoreCase = true) }
            ?: line.siblings.lastOrNull()
        return part?.style?.takeIf { it.color != null }
            ?: Style.EMPTY.withColor(ChatFormatting.GRAY)
    }

    /**
     * Fills each %s in the description with the stat for [level]
     */
    private fun format(trait: TraitDetailsData.Trait, level: Int): String {
        var k = 0
        return Regex("%s").replace(trait.description) {
            trait.levels.getOrNull(k++ * 5 + level)?.let(::formatStat) ?: "?"
        }
    }

    /** Formats a stat value, dropping the decimal part when it is a whole number */
    private fun formatStat(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** Word-wraps text so each line fits within [maxWidth] pixels */
    private fun wrap(text: String, style: Style, maxWidth: Int): List<String> {
        val font = Melinoe.mc.font
        val result = ArrayList<String>()
        val sb = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (sb.isEmpty()) word else "$sb $word"
            if (sb.isNotEmpty() && font.width(Component.literal(candidate).withStyle(style)) > maxWidth) {
                result.add(sb.toString())
                sb.setLength(0)
                sb.append(word)
            } else {
                sb.setLength(0)
                sb.append(candidate)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
