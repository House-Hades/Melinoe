package me.melinoe.utils

import com.mojang.serialization.JsonOps
import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.client.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent

/**
 * Melinoe message indicator for chat messages.
 * Shows a line on the left side of Melinoe mod messages.
 * Note: Must remain as standard native UI implementation since GuiMessageTag requires Int.
 */
val Melinoe_MESSAGE_INDICATOR = GuiMessageTag(
    0xB8FFE1,
    null,
    Component.literal("Message from Melinoe"),
    "Melinoe"
)

/**
 * Converts a MiniMessage string into a native Minecraft Component.
 */
internal fun String.toNative(): Component {
    val adventureComponent = Message.MINI_MESSAGE.deserialize(this)
    val json = GsonComponentSerializer.gson().serializeToTree(adventureComponent)

    val registryAccess = Melinoe.mc.level?.registryAccess()
        ?: Melinoe.mc.connection?.registryAccess()

    return ComponentSerialization.CODEC.parse(registryAccess!!.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow()
}

/**
 * Creates gradient text for "Melinoe" using themed colors.
 * Utilizes MiniMessage's built-in multi-stop gradient tags.
 */
fun getMelinoeGradient(): String {
    // Exact hex translation of the light teal to emerald green gradient
    val gradientTag = "<gradient:#B8FFE1:#7CFFB2:#2E8F78>"
    return "<bold>${gradientTag}Melinoe</gradient></bold>"
}

/**
 * Creates gradient text for "Melinoe" using themed colors.
 * Utilizes MiniMessage's built-in multi-stop gradient tags.
 */
fun createMelinoeGradient(): Component {
    // Exact hex translation of the light teal to emerald green gradient
    return getMelinoeGradient().toNative()
}

/**
 * Creates the Melinoe prefix with gradient text and separator.
 * Format: [Gradient Melinoe] ›
 */
fun getMelinoeWatermark(): String {
    return (getMelinoeGradient() + " <bold>${Message.Colors.SEPARATOR}›</bold><reset>")
}

/**
 * Creates the Melinoe prefix with gradient text and separator.
 * Format: [Gradient Melinoe] ›
 */
fun createMelinoeWatermark(): Component {
    return getMelinoeWatermark().toNative()
}

fun sendChatMessage(message: Any) {
    Melinoe.mc.execute { Melinoe.mc.player?.connection?.sendChat(message.toString()) }
}

fun sendCommand(command: String) {
    Melinoe.mc.execute { Melinoe.mc.player?.connection?.sendCommand(command) }
}

fun getCenteredText(text: String): String {
    // Strip both MiniMessage tags and legacy formatting codes safely
    val strippedText = Message.MINI_MESSAGE.stripTags(text).replace(Regex("§[0-9a-fk-or]"), "")
    if (strippedText.isEmpty()) return text

    val textWidth = Melinoe.mc.font.width(strippedText)
    val chatWidth = Melinoe.mc.gui.chat.width

    if (textWidth >= chatWidth) return text

    val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
    return " ".repeat(spacesNeeded) + text
}

fun getChatBreak(): String =
    Melinoe.mc.gui?.chat?.width?.let { width ->
        "<st>" + " ".repeat(width / Melinoe.mc.font.width(" ")) + "</st>"
    } ?: ""

/**
 * Helper functions for creating styled text components cleanly powered by MiniMessage.
 */
object ChatStyle {
    /**
     * Creates a styled text component with the given MiniMessage color tag
     */
    fun text(content: String, colorTag: String, bold: Boolean = false): Component {
        val boldTag = if (bold) "<bold>" else ""
        val endBoldTag = if (bold) "</bold>" else ""
        return "$colorTag$boldTag$content$endBoldTag<reset>".toNative()
    }

    fun command(content: String): Component = text(content, Message.Colors.COMMAND)
    fun regular(content: String): Component = text(content, Message.Colors.TEXT)
    fun muted(content: String): Component = text(content, Message.Colors.MUTED)
    fun prefix(): Component = text("› ", Message.Colors.PREFIX, bold = true)
    fun separator(): Component = text("- ", Message.Colors.MUTED)
    fun success(content: String): Component = text(content, Message.Colors.SUCCESS)
    fun error(content: String): Component = text(content, Message.Colors.ERROR)
    fun warning(content: String): Component = text(content, Message.Colors.WARNING)
    fun info(content: String): Component = text(content, Message.Colors.INFO)
}

/**
 * Unified messaging system for Melinoe mod.
 *
 * Provides a consistent API for sending messages to the player with proper formatting,
 * watermark prefix, and message indicator.
 */
object Message {

    /**
     * MiniMessage instance used to parse formatted strings.
     */
    val MINI_MESSAGE: MiniMessage = MiniMessage.miniMessage()

    /**
     * Centralized color tags for the unified messaging system utilizing MiniMessage.
     * All colors are in MiniMessage hex format (<#RRGGBB>).
     */
    object Colors {
        // Message type colors
        const val SUCCESS = "<#00FF00>"
        const val ERROR = "<#FF3333>"
        const val WARNING = "<#FFFF00>"
        const val INFO = "<#55FFFF>"

        // UI & Special colors
        const val COMMAND = "<#FFD700>"
        const val DEV = "<#FF3333>"
        const val SEPARATOR = "<#606060>"
        const val TEXT = "<#AAAAAA>"
        const val MUTED = "<#808080>"
        const val PREFIX = "<#808080>"
    }

    /**
     * Sends a basic chat message with the Melinoe watermark prefix.
     *
     * @param message The message content (supports MiniMessage formatting)
     */
    fun chat(message: String) {
        chat(message.toNative())
    }

    /**
     * Sends a chat message with the Melinoe watermark prefix.
     *
     * @param message The message component
     */
    fun chat(message: Component) {
        Melinoe.mc.execute {
            val watermark = createMelinoeWatermark()

            val text = Component.empty()
                .append(watermark)
                .append(Component.literal(" "))
                .append(message)

            Melinoe.mc.gui?.chat?.addMessage(text, null, Melinoe_MESSAGE_INDICATOR)
        }
    }

    /**
     * Sends a raw chat message WITHOUT the Melinoe watermark prefix.
     *
     * @param message The message content (supports MiniMessage formatting)
     */
    fun raw(message: String) {
        raw(message.toNative())
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
     */
    fun success(message: String) {
        chat("${Colors.SUCCESS}$message")
    }

    /**
     * Sends an error message (red text) with the Melinoe watermark prefix.
     */
    fun error(message: String) {
        chat("${Colors.ERROR}$message")
    }

    /**
     * Sends a warning message (yellow text) with the Melinoe watermark prefix.
     */
    fun warning(message: String) {
        chat("${Colors.WARNING}$message")
    }

    /**
     * Sends an info message (cyan text) with the Melinoe watermark prefix.
     */
    fun info(message: String) {
        chat("${Colors.INFO}$message")
    }

    /**
     * Sends a message to the action bar (temporary overlay above hotbar).
     *
     * @param message The action bar message content (supports MiniMessage formatting)
     */
    fun actionBar(message: String) {
        actionBar(message.toNative())
    }

    /**
     * Sends a component to the action bar (temporary overlay above hotbar).
     */
    fun actionBar(message: Component) {
        Melinoe.mc.execute {
            Melinoe.mc.player?.displayClientMessage(message, true)
        }
    }

    /**
     * Sends a centered chat message with the Melinoe watermark prefix.
     *
     * @param message The message content (supports MiniMessage formatting)
     */
    fun centered(message: String) {
        val centeredText = getCenteredText(message)
        chat(centeredText)
    }

    /**
     * Sends a centered chat message WITHOUT the Melinoe watermark prefix.
     *
     * @param message The message content (supports MiniMessage formatting)
     */
    fun centeredRaw(message: String) {
        val centeredText = getCenteredText(message)
        raw(centeredText)
    }

    /**
     * Sends a dev message (only shows if dev mode is enabled).
     *
     * @param message The dev message content (supports MiniMessage formatting)
     */
    fun dev(message: String) {
        if (!ClickGUIModule.devMode) {
            return
        }

        val devPrefixStr = "${Colors.DEV}<bold>Dev</bold> ${Colors.SEPARATOR}<bold>›</bold><reset>"
        val devPrefixComponent = devPrefixStr.toNative()

        Melinoe.mc.execute {
            val watermark = createMelinoeGradient()

            val text = Component.empty()
                .append(watermark)
                .append(Component.literal(" "))
                .append(devPrefixComponent)
                .append(Component.literal(" "))
                .append(message.toNative())

            Melinoe.mc.gui?.chat?.addMessage(text, null, Melinoe_MESSAGE_INDICATOR)
        }
    }

    /**
     * Sends a visual separator line in chat.
     *
     * @param colorTag The separator MiniMessage color tag (default: Colors.SEPARATOR)
     */
    fun separator(colorTag: String = Colors.SEPARATOR) {
        val breakLine = getChatBreak()

        Melinoe.mc.execute {
            val component = "$colorTag$breakLine".toNative()
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