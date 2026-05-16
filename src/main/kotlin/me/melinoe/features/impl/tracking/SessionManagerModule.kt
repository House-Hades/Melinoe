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
    
    // Layout Cache Variables
    private var cachedLines = listOf<Pair<String, String>>()
    private var cachedBoxWidth = 100
    private var cachedBoxHeight = 50
    private var lastPlaytimeRender = -1L
    private var lastFameRender = -1
    private var lastExampleState = false
    private var lastSettingsHash = 0
    
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
            return if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
        }
        
        val mStr = if (m < 10) "0$m" else m.toString()
        val sStr = if (s < 10) "0$s" else s.toString()
        return if (h > 0) {
            val hStr = if (h < 10) "0$h" else h.toString()
            "$hStr:$mStr:$sStr"
        } else {
            "$mStr:$sStr"
        }
    }
    
    /**
     * Get fame as an int by removing the commas
     */
    private fun formatFame(fame: Int): String {
        val str = fame.toString()
        if (str.length <= 3) return str
        val result = StringBuilder(str.length + 2)
        val firstGroup = str.length % 3
        for (i in str.indices) {
            if (i > 0 && (i == firstGroup || (i > firstGroup && (i - firstGroup) % 3 == 0))) {
                result.append(',')
            }
            result.append(str[i])
        }
        return result.toString()
    }
    
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
        val currentWorld = client.level!!.dimension().identifier().toString()
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
                    if (diff > 0) fameGained += diff
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
        
        val currentSettingsHash = (if(showSessionPlaytime) 1 else 0) or
                ((if(showTimeInNexus) 1 else 0) shl 1) or
                ((if(showTimeInDungeons) 1 else 0) shl 2) or
                ((if(showTimeInRealm) 1 else 0) shl 3) or
                ((if(showFameGain) 1 else 0) shl 4) or
                (timeFormat shl 5)
        
        // Only recalculate strings and width/height if variables actually ticked or toggled
        if (sessionPlaytime != lastPlaytimeRender || fameGained != lastFameRender || example != lastExampleState || currentSettingsHash != lastSettingsHash) {
            lastPlaytimeRender = sessionPlaytime
            lastFameRender = fameGained
            lastExampleState = example
            lastSettingsHash = currentSettingsHash
            
            val lines = mutableListOf<Pair<String, String>>()
            if (showSessionPlaytime || example) lines.add("Playtime" to formatTime(if (example) 3665 else sessionPlaytime))
            if (showTimeInNexus || example) lines.add("Nexus Time" to formatTime(if (example) 600 else timeInNexus))
            if (showTimeInDungeons || example) lines.add("Dungeon Time" to formatTime(if (example) 1500 else timeInDungeons))
            if (showTimeInRealm || example) lines.add("Realm Time" to formatTime(if (example) 1565 else timeInRealm))
            if (showFameGain || example) lines.add("Fame Gained" to formatFame(if (example) 15420 else fameGained))
            
            cachedLines = lines
            
            val font = mc.font
            val titleComponent = Component.literal("Session").withStyle(ChatFormatting.BOLD)
            val titleWidth = font.width(titleComponent)
            
            // Width Calculation
            val maxLabelWidth = lines.maxOfOrNull { font.width(it.first) } ?: 50
            val maxValueWidth = lines.maxOfOrNull { font.width(it.second) } ?: 30
            
            val contentWidth = maxLabelWidth + maxValueWidth + 12
            val lineSpacing = font.lineHeight + 2
            
            cachedBoxWidth = maxOf(titleWidth + 16, contentWidth + 12)
            cachedBoxHeight = font.lineHeight + 2 + (lines.size * lineSpacing) + 4
        }
        
        if (cachedLines.isEmpty() && !example) return@render Pair(100, 50)
        
        val font = mc.font
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        fill(1, 0, cachedBoxWidth - 1, cachedBoxHeight, bgColor)
        fill(0, 1, 1, cachedBoxHeight - 1, bgColor)
        fill(cachedBoxWidth - 1, 1, cachedBoxWidth, cachedBoxHeight - 1, bgColor)
        
        val titleComponent = Component.literal("Session").withStyle(ChatFormatting.BOLD)
        val strHeightHalf = font.lineHeight / 2
        val strAreaWidth = font.width(titleComponent) + 4
        
        // Top Split Border
        fill(2, 1 + strHeightHalf, 6, 2 + strHeightHalf, borderColor)
        fill(2 + strAreaWidth + 4, 1 + strHeightHalf, cachedBoxWidth - 2, 2 + strHeightHalf, borderColor)
        // Other Borders
        fill(2, cachedBoxHeight - 2, cachedBoxWidth - 2, cachedBoxHeight - 1, borderColor) // Bottom
        fill(1, 2 + strHeightHalf, 2, cachedBoxHeight - 2, borderColor) // Left
        fill(cachedBoxWidth - 2, 2 + strHeightHalf, cachedBoxWidth - 1, cachedBoxHeight - 2, borderColor) // Right
        
        // Draw Title
        text(font, titleComponent, 8, 2, borderColor, false)
        
        // Draw Content
        var yOffset = font.lineHeight + 4
        val leftPadding = 6
        val lineSpacing = font.lineHeight + 2
        
        for ((label, value) in cachedLines) {
            // Label (Left)
            text(font, label, leftPadding, yOffset, textColorSetting.rgba, false)
            
            // Value (Right)
            val valueX = cachedBoxWidth - font.width(value) - 6
            text(font, value, valueX, yOffset, valueColorSetting.rgba, false)
            
            yOffset += lineSpacing
        }
        
        return@render Pair(cachedBoxWidth, cachedBoxHeight)
    }
}