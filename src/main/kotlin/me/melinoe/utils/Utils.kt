@file:JvmName("Utils")

package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.ActionSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.StringSetting
import me.melinoe.features.Module
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

fun logError(throwable: Throwable, context: Any) {
    val version = try {
        Melinoe.version.friendlyString
    } catch (e: Exception) {
        "unknown"
    }
    
    val message =
        "Melinoe $version Caught an ${throwable::class.simpleName ?: "error"} at ${context::class.simpleName}."
    Melinoe.logger.error(message, throwable)

    // Create formatted error for copying (markdown code block format)
    val errorText = "$message \\n``` ${throwable.message} \\n${
        throwable.stackTraceToString().lineSequence().take(10).joinToString("\n")
    }```"

    // Send clickable message with hover tooltip
    Message.chat(Component.literal("$message §cPlease click this message to copy and send it in the Melinoe discord!").withStyle {
        it
            .withClickEvent(ClickEvent.RunCommand("mdev copy $errorText"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§6Click to copy the error to your clipboard.")))
    })
}

fun setClipboardContent(string: String) {
    try {
        Melinoe.mc.keyboardHandler?.clipboard = string.ifEmpty { " " }
    } catch (e: Exception) {
        Melinoe.logger.error("Failed to set Clipboard Content", e)
    }
}

/**
 * Removes all Minecraft color codes from a string
 * More efficient than regex-based approach
 */
inline val String?.noControlCodes: String
    get() {
        val s = this ?: return ""
        val len = s.length

        if (s.indexOf('§') == -1) return s

        val out = CharArray(len)
        var outPos = 0
        var i = 0

        while (i < len) {
            val c = s[i]
            if (c == '§') i += 2
            else {
                out[outPos++] = c
                i++
            }
        }

        return String(out, 0, outPos)
    }

/**
 * Checks if the current string contains at least one of the specified strings.
 *
 * @param options List of strings to check.
 * @param ignoreCase If comparison should be case-sensitive or not.
 * @return `true` if the string contains at least one of the specified options, otherwise `false`.
 */
fun String.containsOneOf(vararg options: String, ignoreCase: Boolean = false): Boolean =
    containsOneOf(options.toList(), ignoreCase)

/**
 * Checks if the current string contains at least one of the specified strings.
 *
 * @param options List of strings to check.
 * @param ignoreCase If comparison should be case-sensitive or not.
 * @return `true` if the string contains at least one of the specified options, otherwise `false`.
 */
fun String.containsOneOf(options: Collection<String>, ignoreCase: Boolean = false): Boolean =
    options.any { this.contains(it, ignoreCase) }

fun String.startsWithOneOf(vararg options: String, ignoreCase: Boolean = false): Boolean =
    options.any { this.startsWith(it, ignoreCase) }

/**
 * Checks if the current object is equal to at least one of the specified objects.
 *
 * @param options List of other objects to check.
 * @return `true` if the object is equal to one of the specified objects.
 */
fun Any?.equalsOneOf(vararg options: Any?): Boolean =
    options.any { this == it }

fun String.matchesOneOf(vararg options: Regex): Boolean =
    options.any { this.matches(it) }

fun String.capitalizeFirst(): String =
    if (isNotEmpty() && this[0] in 'a'..'z') this[0].uppercaseChar() + substring(1) else this

fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar(Char::titlecase)
}

fun formatTime(time: Long, decimalPlaces: Int = 2): String {
    if (time == 0L) return "0s"
    var remaining = time
    val hours = (remaining / 3600000).toInt().let {
        remaining -= it * 3600000
        if (it > 0) "${it}h " else ""
    }
    val minutes = (remaining / 60000).toInt().let {
        remaining -= it * 60000
        if (it > 0) "${it}m " else ""
    }
    return "$hours$minutes${(remaining / 1000f).toFixed(decimalPlaces)}s"
}

inline val Entity.renderX: Double
    get() =
        xo + (x - xo) * Melinoe.mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderY: Double
    get() =
        yo + (y - yo) * Melinoe.mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderZ: Double
    get() =
        zo + (z - zo) * Melinoe.mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderPos: Vec3
    get() = Vec3(renderX, renderY, renderZ)

inline val Entity.renderBoundingBox: AABB
    get() = boundingBox.move(renderX - x, renderY - y, renderZ - z)

fun formatNumber(numStr: String): String {
    val num = numStr.replace(",", "").toDoubleOrNull() ?: return numStr
    return when {
        num >= 1_000_000_000 -> "%.2fB".format(num / 1_000_000_000)
        num >= 1_000_000 -> "%.2fM".format(num / 1_000_000)
        num >= 1_000 -> "%.2fK".format(num / 1_000)
        else -> "%.0f".format(num)
    }
}

fun BlockPos.getBlockBounds() =
    Melinoe.mc.level?.let { level ->
        level.getBlockState(this)?.getShape(level, this)?.singleEncompassing()
            ?.takeIf { !it.isEmpty }?.bounds()
    }

private val romanMap = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
private val numberRegex = Regex("^[0-9]+$")
fun romanToInt(s: String): Int {
    return if (s.matches(numberRegex)) s.toInt()
    else {
        var result = 0
        for (i in 0 until s.length - 1) {
            val current = romanMap[s[i]] ?: 0
            val next = romanMap[s[i + 1]] ?: 0
            result += if (current < next) -current else current
        }
        result + (romanMap[s.last()] ?: 0)
    }
}

fun Module.createSoundSettings(
    name: String,
    default: String,
    dependencies: () -> Boolean,
    buttonName: String = "Play sound"
): () -> Triple<String, Float, Float> {
    val customSound = +StringSetting(name, default, desc = "Name of a custom sound to play.", length = 64).withDependency { dependencies() }
    val pitch = +NumberSetting("$name Pitch", 1f, 0.1f, 2f, 0.01f, desc = "Pitch of the sound to play.").withDependency { dependencies() }
    val volume = +NumberSetting("$name Volume", 1f, 0.1f, 1f, 0.01f, desc = "Volume of the sound to play.").withDependency { dependencies() }
    val soundSettings = { Triple(customSound.value, volume.value, pitch.value) }
    +ActionSetting(buttonName, desc = "Plays the selected sound.") { playSoundSettings(soundSettings()) }.withDependency { dependencies() }
    return soundSettings
}
