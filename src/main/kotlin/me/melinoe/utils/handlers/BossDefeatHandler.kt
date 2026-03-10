package me.melinoe.utils.handlers

import me.melinoe.Melinoe
import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.DungeonChangeEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.core.EventBus
import me.melinoe.events.core.on
import me.melinoe.features.impl.tracking.PityCounterModule
import me.melinoe.features.impl.visual.dungeontimer.GradientTextBuilder
import me.melinoe.features.impl.visual.dungeontimer.PityCounterConfig
import me.melinoe.features.impl.visual.dungeontimer.TimerModule
import me.melinoe.utils.ChatManager.hideMessage
import me.melinoe.utils.Message
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.BagTracker
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.BossType
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.getCenteredText
import me.melinoe.utils.noControlCodes
import me.melinoe.utils.toNative
import net.minecraft.network.chat.Component

/**
 * Handles boss defeat messages from the server.
 *
 * Message format from server:
 * ===============================================  <- First separator: HIDE and send our separator + custom message
 * BossName                                        <- Capture boss name, HIDE this line
 * 100.0% (1119) 𕑱 {MINECRAFT_USERNAME}          <- SHOW damage stats
 * [more damage stat lines if multiple players]   <- SHOW damage stats
 * ===============================================  <- Second separator: HIDE and send our separator, process boss defeat tracking
 * BossName has been defeated (7/10)!             <- Hide this line (handled by BossTrackerModule)
 * Bonus dungeon chest has spawned!               <- Hide this line
 */
object BossDefeatHandler {
    
    // State tracking
    private var trackerBit = false
    private var pendingBossName: String? = null
    private var bossNameCaptured = false
    
    private var currentDungeon: DungeonData? = null
    
    // Queue for messages hidden during leaderboard building
    private val queuedMessages: MutableList<Component> by lazy { mutableListOf() }
    
    init {
        EventBus.subscribe(this)
        
        on<ChatPacketEvent> {
            if (!ServerUtils.isOnTelos()) return@on
            this.handleMessage()
        }
        
        on<DungeonEntryEvent> {
            currentDungeon = dungeon
        }
        
        on<DungeonExitEvent> {
            currentDungeon = null
        }
        
        on<DungeonChangeEvent> {
            currentDungeon = newDungeon
        }
    }
    
    /**
     * Handle incoming chat messages for boss defeat tracking
     */
    private fun ChatPacketEvent.handleMessage() {
        val message = value
        val cleanValue = message.trim()
        val strippedValue = cleanValue.noControlCodes.trim()
        
        // Note: "has been defeated" messages are handled by BossTrackerModule
        // This allows BossTrackerModule to extract the (X/10) progress before hiding
        
        // Check for separator line to toggle tracker bit
        if (strippedValue == "===============================================") {
            handleSeparator()
            return
        }
        
        // When trackerBit is true, capture and process the boss name
        if (trackerBit) {
            handleTrackerMessage(cleanValue, strippedValue)
            return
        }
        
        // Hide "Bonus dungeon chest has spawned!" message
        if (strippedValue.contains("Bonus dungeon chest has spawned")) {
            hideMessage()
        }
    }
    
    /**
     * Handle separator lines (===============================================)
     */
    private fun ChatPacketEvent.handleSeparator() {
        val wasTrackingBefore = trackerBit
        trackerBit = !trackerBit
        
        // Always hide the message
        hideMessage()
        
        if (trackerBit) {
            queuedMessages.clear()
        }
        
        if (!trackerBit && wasTrackingBefore && pendingBossName != null) {
            pendingBossName = null
            
            Message.separator()
            
            // Leaderboard is done, send back any hidden messages
            if (queuedMessages.isNotEmpty()) {
                queuedMessages.forEach { component ->
                    // Re-send the component to preserve formatting
                    Melinoe.mc.execute { Melinoe.mc.gui?.chat?.addMessage(component) }
                }
                queuedMessages.clear()
            }
        }
        
        // Reset boss name captured flag when toggling
        bossNameCaptured = false
    }
    
    /**
     * Handle messages when tracker bit is active (between separators)
     */
    private fun ChatPacketEvent.handleTrackerMessage(cleanValue: String, strippedValue: String) {
        // Capture and hide ONLY the first non-empty line (boss name)
        if (!bossNameCaptured && cleanValue.isNotEmpty()) {
            captureBossName(cleanValue, strippedValue)
            return
        }
        
        // All other lines (damage stats) - reformat and center them with colors
        if (cleanValue.isNotEmpty()) {
            formatDamageStats(cleanValue, strippedValue)
        }
    }
    
    /**
     * Capture the boss name from the first line after separator
     */
    private fun ChatPacketEvent.captureBossName(cleanValue: String, strippedValue: String) {
        if (strippedValue.matches(Regex("^.*\\s*\\[[^\\]]*\\]\\s*.*$"))) {
            // Log the hidden message
            Melinoe.logger.info("[BossDefeatHandler] Hidden message during leaderboard building: $strippedValue")
            
            // Queue the full Component to preserve formatting when re-sent later
            queuedMessages.add(this.component)
            
            hideMessage()
            return
        }
        
        pendingBossName = cleanValue
        bossNameCaptured = true
        hideMessage()
        
        // Process the boss defeat after in order for the pity counter line to provide accurate pity counters
        processBossDefeat(cleanValue)
        
        // Find the boss data
        val bossData = BossData.findByKey(strippedValue)
        val dungeon = currentDungeon
        
        val timerLines = mutableListOf<Component>()
        val pityLines = mutableListOf<String>()
        
        var headerComponent: Component? = null
        var plainHeaderText = strippedValue
        
        if (bossData != null) {
            // Make the Pity Module Lines
            if (PityCounterModule.enabled) {
                val pityLine = PityCounterConfig.buildPityLine(dungeon, bossData)
                if (pityLine.isNotEmpty()) {
                    pityLines.add(pityLine)
                }
            }
            
            // Make the Timer Module Lines
            timerLines.addAll(TimerModule.processBossDefeatAndGetLines(bossData))
            
            // Set the dungeon/boss name
            if (bossData.bossType == BossType.DUNGEON && dungeon != null) {
                headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
                plainHeaderText = dungeon.areaName
            }
        }
        
        Message.separator()
        
        // Centered Dungeon/Boss Name
        val headerSpaces = getCenteredText(plainHeaderText).takeWhile { it == ' ' }
        val finalHeader = Component.literal(headerSpaces).append(headerComponent ?: "<yellow><bold>$strippedValue</bold></yellow>".toNative())
        Melinoe.mc.execute { Melinoe.mc.gui?.chat?.addMessage(finalHeader) }
        
        // Space prior to either the Timer or Pity Module
        if (pityLines.isNotEmpty() || timerLines.isNotEmpty()) {
            Melinoe.mc.execute { Melinoe.mc.gui?.chat?.addMessage(" ".toNative()) }
        }
        
        // Pity Module Lines
        pityLines.forEach {
            Message.centeredRaw(it)
        }
        
        // Timer Module Lines
        for (line in timerLines) {
            val lineSpaces = getCenteredText(line.string).takeWhile { it == ' ' }
            val finalLine = Component.literal(lineSpaces).append(line)
            Melinoe.mc.execute { Melinoe.mc.gui?.chat?.addMessage(finalLine) }
        }
        
        Message.separator()
    }
    
    /**
     * Format and display damage statistics
     * Original format: "     68.8% (4805)      𕑱 NoWayItzJoey  "
     * New format: "𕑱 NoWayItzJoey — 68.8% (4805)"
     * Colors:
     *   - Medal (𕑱, 𕑰, 𕑩, #): §r (reset/white)
     *   - PlayerName: Gold (1st - 0xFFD700), Silver (2nd - 0xC0C0C0), Bronze (3rd - 0x895129), Light gray (4th+)
     *   - Em dash (—): Dark gray (§8)
     *   - Stats (percentage + damage): Red (0xFF3333)
     */
    private fun ChatPacketEvent.formatDamageStats(cleanValue: String, strippedValue: String) {
        // Verify this is actually a damage stat line to prevent formatting interleaved chat messages
        if (!strippedValue.matches(Regex("^\\d+(\\.\\d+)?%\\s+\\([^)]+\\)\\s+.+$"))) {
            // Log the hidden message
            Melinoe.logger.info("[BossDefeatHandler] Hidden message during leaderboard building: $strippedValue")
            
            // Queue the full Component to preserve formatting when re-sent later
            queuedMessages.add(this.component)
            
            hideMessage()
            return
        }
        
        hideMessage()
        
        // Parse the damage stat line
        // Pattern: "     percentage (damage)      medal username     "
        // Note: Multiple spaces between parts, need to filter empty strings
        val parts = strippedValue.split(Regex("\\s+")) // Split on one or more whitespace
        
        // Expected parts after splitting on whitespace:
        // [0] = percentage (e.g., "68.8%")
        // [1] = damage (e.g., "(4805)")
        // [2] = medal (e.g., "𕑱", "𕑰", "𕑩", or "#")
        // [3+] = username parts (may contain spaces if we had used limit)
        
        if (parts.size >= 4) {
            val percentage = parts[0] // e.g., "68.8%"
            val damage = parts[1]     // e.g., "(4805)"
            val medal = parts[2]      // e.g., "𕑱"
            val username = parts.drop(3).joinToString(" ") // e.g., "NoWayItzJoey" (handles names with spaces)
            
            // Determine player name color based on medal (using Color class)
            val nameColor = when (medal) {
                "𕑱" -> "<#FFD700>" // Gold
                "𕑰" -> "<#C0C0C0>" // Silver
                "𕑩" -> "<#895129>" // Bronze
                else -> "<#8B8B8B>" // Gray (4th+ place - use default <gray>)
            }
            
            // Damage color (red)
            val damageColor = "<#FF3333>"
            
            // Build the formatted string first for centering
            val plainText = "$medal $username — $percentage $damage"
            val spaces = getCenteredText(plainText).takeWhile { it == ' ' }
            
            // Build the message as a Component with proper colors
            val mmString = "$spaces$nameColor$medal $username<reset> <dark_gray>—</dark_gray> $damageColor$percentage $damage<reset>"
            
            // Add medal with same color as username
            Melinoe.mc.execute {
                Melinoe.mc.gui?.chat?.addMessage(mmString.toNative())
            }
        } else {
            val spaces = getCenteredText(strippedValue).takeWhile { it == ' ' }
            Melinoe.mc.execute {
                Melinoe.mc.gui?.chat?.addMessage("$spaces$cleanValue".toNative())
            }
        }
    }
    
    /**
     * Process boss defeat for tracking
     */
    private fun processBossDefeat(bossName: String) {
        // Strip any formatting codes
        val strippedBossName = bossName.noControlCodes.trim()
        
        // Find the boss data
        val bossData = BossData.findByKey(strippedBossName)
        if (bossData != null) {
            // Track boss defeat for lifetime stats and pity counters
            BagTracker.onBossDefeat(strippedBossName)
        }
    }
}
