package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor

/**
 * Unified messaging system for Melinoe mod.
 * 
 * Provides a consistent API for sending messages to the player with proper formatting,
 * watermark prefix, and message indicator.
 * 
 * Features:
 * - Consistent Melinoe watermark prefix on all chat messages
 * - Red indicator line on the left of mod messages
 * - Color-coded message types (success=green, error=red, warning=yellow, info=cyan)
 * - Action bar support for non-intrusive notifications
 * - Centered message support for titles
 * - Dev message toggle for debugging
 * - Visual separators for grouping messages
 */
object Message {
    
    /**
     * Sends a basic chat message with the Melinoe watermark prefix.
     * 
     * @param message The message content
     */
    fun chat(message: String) {
        val component = Component.literal(message)
        chat(component)
    }
    
    /**
     * Sends a chat message with the Melinoe watermark prefix.
     * 
     * @param message The message component
     */
    fun chat(message: Component) {
        Melinoe.mc.execute {
            val watermark = createMelinoeWatermark()
            val text = (watermark as net.minecraft.network.chat.MutableComponent)
                .append(Component.literal(" "))
                .append(message)
            
            Melinoe.mc.gui?.chat?.addMessage(text, null, Melinoe_MESSAGE_INDICATOR)
        }
    }
    
    /**
     * Sends a raw chat message WITHOUT the Melinoe watermark prefix.
     * 
     * Useful for scoreboard-style outputs or multi-line formatted messages
     * where the watermark would be redundant.
     * 
     * @param message The message content
     */
    fun raw(message: String) {
        val component = Component.literal(message)
        raw(component)
    }
    
    /**
     * Sends a raw chat message WITHOUT the Melinoe watermark prefix.
     * 
     * @param message The message component
     */
    fun raw(message: Component) {
        Melinoe.mc.execute {
            Melinoe.mc.gui?.chat?.addMessage(message)
        }
    }
    
    /**
     * Sends a success message (green text) with the Melinoe watermark prefix.
     * 
     * @param message The success message content
     */
    fun success(message: String) {
        val component = Component.literal(message).withStyle { style ->
            style.withColor(TextColor.fromRgb(MessageColors.SUCCESS))
        }
        chat(component)
    }
    
    /**
     * Sends an error message (red text) with the Melinoe watermark prefix.
     * 
     * @param message The error message content
     */
    fun error(message: String) {
        val component = Component.literal(message).withStyle { style ->
            style.withColor(TextColor.fromRgb(MessageColors.ERROR))
        }
        chat(component)
    }
    
    /**
     * Sends a warning message (yellow text) with the Melinoe watermark prefix.
     * 
     * @param message The warning message content
     */
    fun warning(message: String) {
        val component = Component.literal(message).withStyle { style ->
            style.withColor(TextColor.fromRgb(MessageColors.WARNING))
        }
        chat(component)
    }
    
    /**
     * Sends an info message (cyan text) with the Melinoe watermark prefix.
     * 
     * @param message The info message content
     */
    fun info(message: String) {
        val component = Component.literal(message).withStyle { style ->
            style.withColor(TextColor.fromRgb(MessageColors.INFO))
        }
        chat(component)
    }
    
    /**
     * Sends a message to the action bar (temporary overlay above hotbar).
     * 
     * Action bar messages don't include the watermark prefix for a cleaner look.
     * They automatically clear after a few seconds.
     * 
     * @param message The action bar message content
     */
    fun actionBar(message: String) {
        val component = Component.literal(message)
        actionBar(component)
    }
    
    /**
     * Sends a component to the action bar (temporary overlay above hotbar).
     * 
     * @param message The action bar message component
     */
    fun actionBar(message: Component) {
        Melinoe.mc.execute {
            Melinoe.mc.player?.displayClientMessage(message, true)
        }
    }
    
    /**
     * Sends a centered chat message with the Melinoe watermark prefix.
     * 
     * The message is automatically centered based on the chat width.
     * Useful for titles and important notifications.
     * 
     * @param message The message content (can include formatting codes)
     */
    fun centered(message: String) {
        val centeredText = getCenteredText(message)
        chat(centeredText)
    }
    
    /**
     * Sends a centered chat message WITHOUT the Melinoe watermark prefix.
     * 
     * The message is automatically centered based on the chat width.
     * Useful for scoreboard-style outputs where the watermark would be redundant.
     * 
     * @param message The message content (can include formatting codes)
     */
    fun centeredRaw(message: String) {
        val centeredText = getCenteredText(message)
        raw(centeredText)
    }
    
    /**
     * Sends a dev message (only shows if dev mode is enabled).
     * 
     * Dev messages have a distinct red "Dev" prefix and are useful for debugging.
     * They can be toggled on/off in the ClickGUI settings.
     * 
     * @param message The dev message content
     */
    fun dev(message: String) {
        // Check if dev mode is enabled
        if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
            return
        }
        
        val devPrefix = (createMelinoeGradient() as net.minecraft.network.chat.MutableComponent)
            .append(Component.literal("Dev").withStyle { style ->
                style.withColor(TextColor.fromRgb(MessageColors.DEV)).withBold(true)
            })
            .append(Component.literal(" ›").withStyle { style ->
                style.withColor(TextColor.fromRgb(MessageColors.SEPARATOR)).withBold(true)
            })
        
        Melinoe.mc.execute {
            val text = devPrefix
                .append(Component.literal(" "))
                .append(Component.literal(message))
            
            Melinoe.mc.gui?.chat?.addMessage(text, null, Melinoe_MESSAGE_INDICATOR)
        }
    }
    
    /**
     * Sends a visual separator line in chat.
     * 
     * The separator width automatically matches the chat width.
     * Useful for grouping related messages.
     * 
     * @param color The separator color (default: MessageColors.SEPARATOR)
     */
    fun separator(color: Int = MessageColors.SEPARATOR) {
        val breakLine = getChatBreak()
        val colorCode = String.format("§x§%x§%x§%x§%x§%x§%x", 
            (color shr 20) and 0xF,
            (color shr 16) and 0xF,
            (color shr 12) and 0xF,
            (color shr 8) and 0xF,
            (color shr 4) and 0xF,
            color and 0xF
        )
        
        Melinoe.mc.execute {
            val component = Component.literal(colorCode + breakLine)
            Melinoe.mc.gui?.chat?.addMessage(component)
        }
    }
    
    /**
     * Creates a new MessageBuilder for building complex messages.
     * 
     * @return A new MessageBuilder instance
     */
    fun builder(): MessageBuilder {
        return MessageBuilder()
    }
}
