package me.melinoe.features.impl

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.ClickGUI
import me.melinoe.clickgui.HudManager
import me.melinoe.clickgui.settings.AlwaysActive
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Category.Companion.categories
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.alert
import me.melinoe.utils.createMelinoeGradient
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.lwjgl.glfw.GLFW
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.max
import kotlin.math.round

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT,
    category = Category.MISC,
    description = "Allows you to customize the UI."
) {
    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(27, 197, 97), desc = "The color of the Click GUI.") // bright green: 0x1BC561
    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")
    
    private val action by ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { 
        mc.execute { mc.setScreen(HudManager) }
    }
    
    val devMode by BooleanSetting("Dev Mode", false, desc = "Enables developer commands and debug messages")

    val panelSetting by MapSetting("Panel Settings", mutableMapOf<String, PanelData>())
    data class PanelData(var x: Float = 10f, var y: Float = 10f, var extended: Boolean = true)

    fun resetPositions() {
        // Only position the visible categories in the correct order
        val visibleCategories = listOf(Category.COMBAT, Category.VISUAL, Category.TRACKING, Category.MISC)
        visibleCategories.forEachIndexed { index, category ->
            val setting = panelSetting.getOrPut(category.name) { PanelData() }
            setting.x = 10f + 260f * index
            setting.y = 10f
            setting.extended = true
        }
    }

    fun getStandardGuiScale(): Float {
        val verticalScale = (mc.window.screenHeight.toFloat() / 1080f) / NVGRenderer.devicePixelRatio()
        val horizontalScale = (mc.window.screenWidth.toFloat() / 1920f) / NVGRenderer.devicePixelRatio()
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 3f) * 10f) / 10f
    }

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.execute {
            mc.setScreen(ClickGUI)
        }
        super.onEnable()
        toggle()
    }

    // Update checker integration
    private const val RELEASE_LINK = "https://github.com/NoWayItzJoey/melinoe/releases/latest"
    private const val GITHUB_API_URL = "https://api.github.com/repos/NoWayItzJoey/melinoe/releases/latest"
    private var latestVersionNumber: String? = null
    private var hasSentUpdateMessage = false
    
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    init {
        updateScope.launch {
            latestVersionNumber = checkNewerVersion(Melinoe.version.friendlyString)
        }
        
        on<WorldLoadEvent> {
            if (hasSentUpdateMessage || latestVersionNumber == null) return@on
            hasSentUpdateMessage = true
            
            notifyUpdate(latestVersionNumber!!)
        }
    }

    /**
     * Notify the player about the available update using melinoe message style.
     */
    private fun notifyUpdate(version: String) {
        mc.execute {
            val currentVersion = Melinoe.version.friendlyString
            val message = buildUpdateMessage(currentVersion, version, RELEASE_LINK)
            
            mc.player?.displayClientMessage(message, false)
            
            // Play alert sound
            alert("melinoe Update Available", playSound = true)
        }
    }
    
    /**
     * Build the update message in the melinoe style with centered text.
     */
    private fun buildUpdateMessage(currentVersion: String, targetVersion: String, releaseUrl: String): net.minecraft.network.chat.MutableComponent {
        val message = Component.empty() as net.minecraft.network.chat.MutableComponent
        val chatWidth = mc.gui.chat.width
        
        // Helper function to center text
        fun centerText(text: String, color: Int = 0xAAAAAA): Component {
            val textWidth = mc.font.width(text)
            if (textWidth >= chatWidth) return Component.literal(text).withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(color)) }
            val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
            val spaces = " ".repeat(spacesNeeded)
            return Component.literal(spaces + text).withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(color)) }
        }
        
        // Top separator line
        val separatorWidth = chatWidth / 4
        val separator = "─".repeat(separatorWidth)
        message.append(Component.literal(separator)
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x404040)).withStrikethrough(true) })
        message.append(Component.literal("\n"))
        
        // "UPDATE AVAILABLE" header with moon emojis (centered)
        val headerText = "☽ UPDATE AVAILABLE ☽"
        val headerWidth = mc.font.width(headerText)
        val headerSpaces = ((chatWidth - headerWidth) / 2 / 4).coerceAtLeast(0)
        message.append(Component.literal(" ".repeat(headerSpaces)))
        message.append(Component.literal("☽ "))
        message.append(Component.literal("UPDATE AVAILABLE")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x7CFFB2)).withBold(true) })
        message.append(Component.literal(" ☽"))
        message.append(Component.literal("\n"))
        
        // "Melinoe grows stronger" line (centered)
        val melinoeText = "Melinoe grows stronger, a new version awaits."
        val melinoeWidth = mc.font.width(melinoeText)
        val melinoeSpaces = ((chatWidth - melinoeWidth) / 2 / 4).coerceAtLeast(0)
        message.append(Component.literal(" ".repeat(melinoeSpaces)))
        message.append(createMelinoeGradient())
        message.append(Component.literal(" grows stronger, a new version awaits.")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        message.append(Component.literal("\n"))
        
        // Version comparison line (centered)
        val versionText = "Current version: v$currentVersion → New version: $targetVersion"
        val versionWidth = mc.font.width(versionText)
        val versionSpaces = ((chatWidth - versionWidth) / 2 / 4).coerceAtLeast(0)
        message.append(Component.literal(" ".repeat(versionSpaces)))
        message.append(Component.literal("Current version: ")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        message.append(Component.literal("v$currentVersion")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x7CFFB2)) })
        message.append(Component.literal(" → New version: ")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        message.append(Component.literal(targetVersion)
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x7CFFB2)) })
        message.append(Component.literal("\n"))
        
        // Download instruction line (centered)
        val downloadText = "Download the latest release on ɢɪᴛʜᴜʙ or ᴅɪѕᴄᴏʀᴅ!"
        val downloadWidth = mc.font.width(downloadText)
        val downloadSpaces = ((chatWidth - downloadWidth) / 2 / 4).coerceAtLeast(0)
        message.append(Component.literal(" ".repeat(downloadSpaces)))
        message.append(Component.literal("Download the latest release on ")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        
        // GitHub link (small caps style)
        val githubLink = Component.literal("ɢɪᴛʜᴜʙ")
            .withStyle {
                it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x7CFFB2))
                    .withUnderlined(true)
                    .withClickEvent(ClickEvent.OpenUrl(URI(releaseUrl)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Open GitHub release")))
            }
        message.append(githubLink)
        
        message.append(Component.literal(" or ")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        
        // Discord link (small caps style)
        val discordLink = Component.literal("ᴅɪѕᴄᴏʀᴅ")
            .withStyle {
                it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x7CFFB2))
                    .withUnderlined(true)
                    .withClickEvent(ClickEvent.OpenUrl(URI("https://discord.gg/YdQRJt5Z2U")))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Join the melinoe Discord server")))
            }
        message.append(discordLink)
        
        message.append(Component.literal("!\n")
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) })
        
        // Bottom separator line
        message.append(Component.literal(separator)
            .withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x404040)).withStrikethrough(true) })
        
        return message
    }

    private suspend fun checkNewerVersion(currentVersion: String): String? {
        return try {
            val release = fetchLatestRelease() ?: return null
            if (isSecondNewer(currentVersion, release.tagName)) {
                Melinoe.logger.info("Update available: ${release.tagName} (current: $currentVersion)")
                release.tagName
            } else {
                Melinoe.logger.info("No update available (current: $currentVersion, latest: ${release.tagName})")
                null
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to check for updates: ${e.message}")
            null
        }
    }

    private suspend fun fetchLatestRelease(): Release? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/json")
                .header("User-Agent", "melinoe-Mod-Update-Checker")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                com.google.gson.Gson().fromJson(response.body(), Release::class.java)
            } else {
                Melinoe.logger.warn("GitHub API returned status ${response.statusCode()}")
                null
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to fetch latest release: ${e.message}")
            null
        }
    }

    private fun isSecondNewer(currentVersion: String, previousVersion: String?): Boolean {
        if (currentVersion.isEmpty() || previousVersion.isNullOrEmpty()) return false
        
        val current = currentVersion.removePrefix("v")
        val previous = previousVersion.removePrefix("v")
        
        val (major, minor, patch) = current.split(".").mapNotNull { it.toIntOrNull() }
        val (major2, minor2, patch2) = previous.split(".").mapNotNull { it.toIntOrNull() }
        
        return when {
            major > major2 -> false
            major < major2 -> true
            minor > minor2 -> false
            minor < minor2 -> true
            patch > patch2 -> false
            patch < patch2 -> true
            else -> false
        }
    }

    private data class Release(
        @SerializedName("tag_name")
        val tagName: String
    )
}
