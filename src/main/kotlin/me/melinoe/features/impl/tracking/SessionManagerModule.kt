package me.melinoe.features.impl.tracking

import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.TabListUtils
import me.melinoe.utils.data.DungeonData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.*

/**
 * Session Manager Module - tracks session playtime, area durations, and fame
 */
object SessionManagerModule : Module(
    name = "Session Manager",
    category = Category.TRACKING,
    description = "Tracks playtime, location times, and fame gained during the session"
) {
    
    // Display Color Settings
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF2E8F78.toInt()), desc = "Color for the widget border and title")
    private val textColorSetting by ColorSetting("Label Color", Color(0xFF7CFFB2.toInt()), desc = "Color for the tracking labels")
    private val valueColorSetting by ColorSetting("Value Color", Color(-1), desc = "Color for the value outputs")
    
    // Time Formatting Setting
    private val timeFormat by SelectorSetting("Time Format", "hh:mm:ss", listOf("hh:mm:ss", "hh mm ss"), desc = "Format for time display")
    
    // Toggleable Settings
    private val showSessionPlaytime by BooleanSetting("Show Playtime", true, desc = "Show total session playtime")
    private val showTimeInNexus by BooleanSetting("Show Nexus Time", true, desc = "Show time spent in the Nexus")
    private val showTimeInDungeons by BooleanSetting("Show Dungeon Time", true, desc = "Show time spent in dungeons")
    private val showTimeInRealm by BooleanSetting("Show Realm Time", true, desc = "Show time spent in the realm")
    private val showFameGain by BooleanSetting("Show Fame Gained", true, desc = "Show fame gained during this session")
    
    
    // Tracking Variables
    private var sessionPlaytime = 0L
    private var timeInNexus = 0L
    private var timeInDungeons = 0L
    private var timeInRealm = 0L
    private var fameGained = 0
    
    // Others
    private var lastFame = -1
    private var lastUpdateTime = 0L
    private var lastWorldStr: String? = null
    private var lastPlayerId: UUID? = null
    private var needsBaseline = true
    
    init {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            onClientTick(client)
        }
    }
    
    /**
     * Time formatting function
     */
    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        
        if (timeFormat == 1) {
            return if (h > 0) {
                "${h}h ${m}m ${s}s"
            } else {
                "${m}m ${s}s"
            }
        }
        
        // Default hh:mm:ss
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }
    
    /**
     * Get fame as an int by removing the commas
     */
    private fun getParsedFame(): Int? {
        val raw = TabListUtils.getFame()
        if (raw.isNullOrEmpty()) return null
        return try {
            raw.replace(",", "").toInt()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun onClientTick(client: Minecraft) {
        if (!enabled) return
        
        // Detect loading screens & disable the next fame gain calculation
        if (client.player == null || client.level == null) {
            needsBaseline = true
            return
        }
        
        // Detect world/context changes
        val currentWorld = client.level!!.dimension().location().toString()
        val currentPlayer = client.player!!.uuid
        
        if (currentWorld != lastWorldStr || currentPlayer != lastPlayerId) {
            needsBaseline = true
            lastWorldStr = currentWorld
            lastPlayerId = currentPlayer
        }
        
        // Fame Gained Calculations
        val currentFame = getParsedFame()
        
        if (currentFame != null) {
            if (needsBaseline) {
                // Ran when the player has entered a new world. Do not add this
                lastFame = currentFame
                needsBaseline = false
            } else {
                // Calculate valid fame gains normally since no world swap
                if (lastFame != -1) {
                    val diff = currentFame - lastFame
                    // Only count positive gains in fame
                    if (diff > 0) {
                        fameGained += diff
                    }
                    // Always update tracker to current
                    lastFame = currentFame
                } else {
                    // Failsafe incase fame was invalid
                    lastFame = currentFame
                }
            }
        }
        
        // Playtimes
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= 1000) {
            lastUpdateTime = currentTime
            
            // Increment Timers
            sessionPlaytime++
            
            val areaName = try {
                LocalAPI.getCurrentCharacterArea() ?: ""
            } catch (e: Exception) {
                ""
            }
            
            val currentlyInNexus = areaName == "The Nexus"
            val currentlyInDungeon = DungeonData.findByKey(areaName) != null
            val currentlyInRealm = !currentlyInDungeon && !currentlyInNexus && areaName.isNotEmpty()
            
            if (currentlyInNexus) timeInNexus++
            else if (currentlyInDungeon) timeInDungeons++
            else if (currentlyInRealm) timeInRealm++
        }
    }
    
    /**
     * HUD Rendering
     */
    private val sessionManagerHud by HUDSetting(
        name = "Session Manager Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = false,
        description = "Position of the session manager display",
        module = this
    ) render@{ example ->
        if (!enabled && !example) return@render Pair(100, 50)
        
        val lines = mutableListOf<Pair<String, String>>()
        
        if (showSessionPlaytime || example) {
            lines.add("Playtime" to formatTime(if (example) 3665 else sessionPlaytime))
        }
        if (showTimeInNexus || example) {
            lines.add("Nexus Time" to formatTime(if (example) 600 else timeInNexus))
        }
        if (showTimeInDungeons || example) {
            lines.add("Dungeon Time" to formatTime(if (example) 1500 else timeInDungeons))
        }
        if (showTimeInRealm || example) {
            lines.add("Realm Time" to formatTime(if (example) 1565 else timeInRealm))
        }
        if (showFameGain || example) {
            val displayFame = if (example) 15420 else fameGained
            lines.add("Fame Gained" to String.format(Locale.US, "%,d", displayFame))
        }
        
        if (lines.isEmpty() && !example) return@render Pair(100, 50)
        
        // Rendering
        val font = mc.font
        val titleComponent = Component.literal("Session").withStyle(ChatFormatting.BOLD)
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        val lineSpacing = font.lineHeight + 2
        val titleWidth = font.width(titleComponent)
        
        // Width Calculation
        val maxLabelWidth = lines.maxOfOrNull { font.width(it.first) } ?: 50
        val maxValueWidth = lines.maxOfOrNull { font.width(it.second) } ?: 30
        
        val contentWidth = maxLabelWidth + maxValueWidth + 12
        val boxWidth = maxOf(titleWidth + 16, contentWidth + 12)
        val boxHeight = font.lineHeight + 2 + (lines.size * lineSpacing) + 4
        
        // Draw Background
        fill(1, 0, boxWidth - 1, boxHeight, bgColor)
        fill(0, 1, 1, boxHeight - 1, bgColor)
        fill(boxWidth - 1, 1, boxWidth, boxHeight - 1, bgColor)
        
        // Draw Borders
        val strHeightHalf = font.lineHeight / 2
        val strAreaWidth = titleWidth + 4
        
        // Top Split Border
        fill(2, 1 + strHeightHalf, 6, 2 + strHeightHalf, borderColor)
        fill(2 + strAreaWidth + 4, 1 + strHeightHalf, boxWidth - 2, 2 + strHeightHalf, borderColor)
        // Other Borders
        fill(2, boxHeight - 2, boxWidth - 2, boxHeight - 1, borderColor) // Bottom
        fill(1, 2 + strHeightHalf, 2, boxHeight - 2, borderColor)        // Left
        fill(boxWidth - 2, 2 + strHeightHalf, boxWidth - 1, boxHeight - 2, borderColor) // Right
        
        // Draw Title
        drawString(font, titleComponent, 8, 2, borderColor, false)
        
        // Draw Content
        var yOffset = font.lineHeight + 4
        val leftPadding = 6
        
        for ((label, value) in lines) {
            // Label (Left)
            drawString(font, label, leftPadding, yOffset, textColorSetting.rgba, false)
            
            // Value (Right)
            val valueX = boxWidth - font.width(value) - 6
            drawString(font, value, valueX, yOffset, valueColorSetting.rgba, false)
            
            yOffset += lineSpacing
        }
        
        Pair(boxWidth, boxHeight)
    }
}