package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * Melinoe message indicator for chat messages
 * Shows a line on the left side of Melinoe mod messages
 */
val Melinoe_MESSAGE_INDICATOR = net.minecraft.client.GuiMessageTag(
    0xB8FFE1,  // Melinoe mint color
    null,
    Component.literal("Message from Melinoe"),
    "Melinoe"
)

/**
 * Color constants for chat messages
 */
object ChatColors {
    // Message type colors
    const val SUCCESS = 0x00FF00      // Green
    const val ERROR = 0xFF3333        // Red
    const val WARNING = 0xFFFF00      // Yellow
    const val INFO = 0x55FFFF         // Cyan
    
    // UI colors
    const val COMMAND = 0xFFD700      // Gold/Yellow for commands
    const val TEXT = 0xAAAAAA         // Light gray for regular text
    const val MUTED = 0x808080        // Gray for muted/secondary text
    const val SEPARATOR = 0x606060    // Dark gray for separators
    const val PREFIX = 0x808080       // Gray for prefixes (›)
    
    // Special colors
    const val DEV = 0xFF3333          // Bright red for dev messages
}

/**
 * Helper functions for creating styled text components
 */
object ChatStyle {
    /**
     * Creates a styled text component with the given color
     */
    fun text(content: String, color: Int, bold: Boolean = false): Component {
        return Component.literal(content).withStyle { style ->
            if (bold) {
                style.withColor(TextColor.fromRgb(color)).withBold(true)
            } else {
                style.withColor(TextColor.fromRgb(color))
            }
        }
    }
    
    /**
     * Creates a command-style text (gold/yellow)
     */
    fun command(content: String): Component = text(content, ChatColors.COMMAND)
    
    /**
     * Creates a regular text (light gray)
     */
    fun regular(content: String): Component = text(content, ChatColors.TEXT)
    
    /**
     * Creates a muted text (gray)
     */
    fun muted(content: String): Component = text(content, ChatColors.MUTED)
    
    /**
     * Creates a prefix (bold gray ›)
     */
    fun prefix(): Component = text("› ", ChatColors.PREFIX, bold = true)
    
    /**
     * Creates a separator (gray -)
     */
    fun separator(): Component = text("- ", ChatColors.MUTED)
    
    /**
     * Creates a success message
     */
    fun success(content: String): Component = text(content, ChatColors.SUCCESS)
    
    /**
     * Creates an error message
     */
    fun error(content: String): Component = text(content, ChatColors.ERROR)
    
    /**
     * Creates a warning message
     */
    fun warning(content: String): Component = text(content, ChatColors.WARNING)
    
    /**
     * Creates an info message
     */
    fun info(content: String): Component = text(content, ChatColors.INFO)
}

/**
 * Creates gradient text for "Melinoe" using themed colors.
 * Each character has a different shade transitioning from light teal to emerald green.
 */
fun createMelinoeGradient(): Component {
    // Gradient colors for Melinoe (light teal to emerald green)
    val colors = intArrayOf(
        0xB8FFE1, // M - light mint
        0xA4FFD1, // e - pale teal
        0x90FFC2, // l - light teal
        0x7CFFB2, // i - mint green
        0x58E8A2, // n - sea green
        0x34D091, // o - emerald
        0x10B981  // e - deep emerald
    )
    
    val chars = arrayOf("M", "e", "l", "i", "n", "o", "e")
    
    var result = Component.empty()
    for (i in chars.indices) {
        result = result.append(
            Component.literal(chars[i]).withStyle { style ->
                style.withColor(TextColor.fromRgb(colors[i])).withBold(true)
            }
        )
    }
    
    return result
}

/**
 * Creates the Melinoe prefix with gradient text and separator.
 * Format: [Gradient Melinoe] » 
 */
fun createMelinoeWatermark(): Component {
    return Component.empty()
        .append(createMelinoeGradient())
        .append(Component.literal(" ›").withStyle { style ->
            style.withColor(TextColor.fromRgb(ChatColors.SEPARATOR)).withBold(true)
        })
}

fun sendChatMessage(message: Any) {
    Melinoe.mc.execute { Melinoe.mc.player?.connection?.sendChat(message.toString()) }
}

fun sendCommand(command: String) {
    Melinoe.mc.execute { Melinoe.mc.player?.connection?.sendCommand(command) }
}

fun getCenteredText(text: String): String {
    val strippedText = text.noControlCodes
    if (strippedText.isEmpty()) return text
    val textWidth = Melinoe.mc.font.width(strippedText)
    val chatWidth = Melinoe.mc.gui.chat.width

    if (textWidth >= chatWidth) return text

    val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
    return " ".repeat(spacesNeeded) + text
}

fun getChatBreak(): String =
    Melinoe.mc.gui?.chat?.width?.let {
        "§8§m" + " ".repeat(it / Melinoe.mc.font.width(" "))
    } ?: ""
