package me.melinoe.features.impl

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import me.melinoe.Melinoe
import me.melinoe.clickgui.ClickGUI
import me.melinoe.clickgui.HudManager
import me.melinoe.clickgui.settings.AlwaysActive
import me.melinoe.clickgui.settings.impl.ActionSetting
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.MapSetting
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.alert
import me.melinoe.utils.getCenteredText
import me.melinoe.utils.getChatBreak
import me.melinoe.utils.getMelinoeWatermark
import me.melinoe.utils.toNative
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
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

    // Safety checks
    private var clickGuiPressCount = 0
    private var clickGuiLastPress = 0L
    
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
        // Dungeon safety check
        if (LocalAPI.isInDungeon()) {
            val currentTime = System.currentTimeMillis()
            
            // If more than 5 seconds have passed, reset the sequence
            if (currentTime - clickGuiLastPress > 5000) {
                clickGuiPressCount = 1
                clickGuiLastPress = currentTime
                Message.actionBar("${getMelinoeWatermark()} ${Message.Colors.ERROR}<bold>Warning:</bold> You are in a dungeon! Press 2 more times within 5s to open Click GUI.")
                return // Prevent opening menu
            } else {
                clickGuiPressCount++
                if (clickGuiPressCount < 3) {
                    val remaining = 3 - clickGuiPressCount
                    Message.actionBar("${getMelinoeWatermark()} ${Message.Colors.ERROR}Press $remaining more time to open Click GUI.")
                    return // Prevent opening menu
                }
                // If we reach here, 3 presses completed successfully, reset
                clickGuiPressCount = 0
            }
        }
        
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
    private const val RELEASE_LINK = "https://modrinth.com/project/fsZHbO2r/version/"
    private const val GITHUB_API_URL = "https://api.github.com/repos/House-Hades/Melinoe/releases/latest"
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
            
            CoroutineScope(Dispatchers.Default).launch {
                delay(2000)
                
                Minecraft.getInstance().execute {
                    notifyUpdate(latestVersionNumber!!)
                }
            }
        }
    }

    /**
     * Notify the player about the available update using melinoe message style.
     */
    private fun notifyUpdate(version: String) {
        mc.execute {
            val currentVersion = Melinoe.version.friendlyString
            val message = buildUpdateMessage(currentVersion, version, RELEASE_LINK + version.removePrefix("v"))
            
            Melinoe.mc.gui?.chat?.addMessage(message)
            
            // Play alert sound
            alert("Melinoe Update Available", playSound = true)
        }
    }
    
    /**
     * Build the update message in the melinoe style with centered text.
     */
    private fun buildUpdateMessage(currentVersion: String, targetVersion: String, releaseUrl: String): MutableComponent {
        val sepTag = "<#606060>${getChatBreak()}"
        
        val headerText = getCenteredText("<#FFFFFF>☽ </#FFFFFF><#7CFFB2><bold>UPDATE AVAILABLE</bold></#7CFFB2><#FFFFFF> ☽</#FFFFFF>")
        val melinoeText = getCenteredText("<bold><gradient:#B8FFE1:#7CFFB2:#2E8F78>Melinoe</gradient></bold><#AAAAAA> grows stronger, a new version awaits.</#AAAAAA>")
        val versionText = getCenteredText("<#AAAAAA>Current version: <#7CFFB2>v$currentVersion</#7CFFB2> → New version: <#7CFFB2>$targetVersion</#7CFFB2></#AAAAAA>")
        
        val githubTag = "<click:open_url:'$releaseUrl'><hover:show_text:'<#AAAAAA>Open the Modrinth Page'><#7CFFB2><underlined>ᴍᴏᴅʀɪɴᴛʜ</underlined></#7CFFB2></hover></click>"
        val discordTag = "<click:open_url:'https://discord.gg/Nxhmxjt3kR'><hover:show_text:'<#AAAAAA>Join the Melinoe Discord server'><#7CFFB2><underlined>ᴅɪѕᴄᴏʀᴅ</underlined></#7CFFB2></hover></click>"
        val dText = getCenteredText("<#AAAAAA>Download the latest release on $githubTag or $discordTag!</#AAAAAA>")
        
        val miniMessageStr = "$sepTag<br>$headerText<br>$melinoeText<br>$versionText<br>$dText<br>$sepTag"
        
        return Component.empty().append(miniMessageStr.toNative()) as MutableComponent
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