package me.melinoe.utils

import me.melinoe.Melinoe
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * Fluent builder for constructing complex messages.
 * 
 * Provides a chainable API for building messages with multiple components,
 * colors, formatting, hover events, and click events.
 */
class MessageBuilder {
    private val components = mutableListOf<Component>()
    private var currentStyle = Style.EMPTY
    
    /**
     * Adds text with optional color.
     * 
     * @param content The text content
     * @param color The text color (RGB format, optional)
     * @return This builder for chaining
     */
    fun text(content: String, color: Int? = null): MessageBuilder {
        var style = currentStyle
        if (color != null) {
            style = style.withColor(TextColor.fromRgb(color))
        }
        
        val component = Component.literal(content).withStyle(style)
        components.add(component)
        
        // Reset style after adding component
        currentStyle = Style.EMPTY
        
        return this
    }
    
    /**
     * Adds gradient text with multiple colors.
     * 
     * Each character gets a color from the colors array, interpolated if needed.
     * 
     * @param content The text content
     * @param colors Array of colors (RGB format)
     * @return This builder for chaining
     */
    fun gradient(content: String, colors: IntArray): MessageBuilder {
        if (content.isEmpty() || colors.isEmpty()) return this
        
        val chars = content.toCharArray()
        val step = (colors.size - 1).toFloat() / (chars.size - 1).coerceAtLeast(1)
        
        for (i in chars.indices) {
            val colorIndex = (i * step).toInt().coerceIn(0, colors.size - 1)
            val color = colors[colorIndex]
            
            val component = Component.literal(chars[i].toString()).withStyle { style ->
                style.withColor(TextColor.fromRgb(color))
            }
            components.add(component)
        }
        
        return this
    }
    
    /**
     * Makes the next text component bold.
     * 
     * @return This builder for chaining
     */
    fun bold(): MessageBuilder {
        currentStyle = currentStyle.withBold(true)
        return this
    }
    
    /**
     * Makes the next text component italic.
     * 
     * @return This builder for chaining
     */
    fun italic(): MessageBuilder {
        currentStyle = currentStyle.withItalic(true)
        return this
    }
    
    /**
     * Makes the next text component underlined.
     * 
     * @return This builder for chaining
     */
    fun underline(): MessageBuilder {
        currentStyle = currentStyle.withUnderlined(true)
        return this
    }
    
    /**
     * Adds a hover event to the last component.
     * 
     * @param text The hover text
     * @return This builder for chaining
     */
    fun hover(text: String): MessageBuilder {
        if (components.isEmpty()) return this
        
        val lastComponent = components.removeLast()
        val hoverComponent = Component.literal(text)
        val styledComponent = lastComponent.copy().withStyle { style ->
            style.withHoverEvent(HoverEvent.ShowText(hoverComponent))
        }
        components.add(styledComponent)
        
        return this
    }
    
    /**
     * Adds a hover event to the last component.
     * 
     * @param component The hover component
     * @return This builder for chaining
     */
    fun hover(component: Component): MessageBuilder {
        if (components.isEmpty()) return this
        
        val lastComponent = components.removeLast()
        val styledComponent = lastComponent.copy().withStyle { style ->
            style.withHoverEvent(HoverEvent.ShowText(component))
        }
        components.add(styledComponent)
        
        return this
    }
    
    /**
     * Adds a click event to the last component.
     * 
     * @param event The click event
     * @return This builder for chaining
     */
    fun click(event: ClickEvent): MessageBuilder {
        if (components.isEmpty()) return this
        
        val lastComponent = components.removeLast()
        val styledComponent = lastComponent.copy().withStyle { style ->
            style.withClickEvent(event)
        }
        components.add(styledComponent)
        
        return this
    }
    
    /**
     * Adds a new line.
     * 
     * @return This builder for chaining
     */
    fun newLine(): MessageBuilder {
        components.add(Component.literal("\n"))
        return this
    }
    
    /**
     * Adds a space.
     * 
     * @return This builder for chaining
     */
    fun space(): MessageBuilder {
        components.add(Component.literal(" "))
        return this
    }
    
    /**
     * Builds the final component by combining all added components.
     * 
     * @return The combined component
     */
    fun build(): Component {
        if (components.isEmpty()) {
            return Component.empty()
        }
        
        val result = Component.empty()
        for (component in components) {
            result.append(component)
        }
        
        return result
    }
    
    /**
     * Builds and sends the message to chat with the Melinoe watermark prefix.
     */
    fun send() {
        val component = build()
        Message.chat(component)
    }
    
    /**
     * Builds and sends the message to the action bar (without watermark).
     */
    fun sendActionBar() {
        val component = build()
        Message.actionBar(component)
    }
}
