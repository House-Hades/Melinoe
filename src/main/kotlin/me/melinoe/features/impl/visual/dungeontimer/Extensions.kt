package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.Melinoe
import me.melinoe.utils.data.DungeonData
import net.minecraft.network.chat.Component

/**
 * Extension functions for the Dungeon Timer system.
 */

/**
 * Checks if this dungeon is a multi-stage dungeon with multiple bosses.
 */
fun DungeonData.isMultiStageDungeon(): Boolean =
    this == DungeonData.RUSTBORN_KINGDOM || this == DungeonData.CELESTIALS_PROVINCE

/**
 * Centers a Component by adding spaces before it based on chat width.
 */
fun centerComponent(component: Component): Component {
    val plainText = component.string
    val textWidth = Melinoe.mc.font.width(plainText)
    val chatWidth = Melinoe.mc.gui.chat.width
    
    if (textWidth >= chatWidth) return component
    
    val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
    val spaces = " ".repeat(spacesNeeded)
    
    return Component.literal(spaces).append(component)
}
