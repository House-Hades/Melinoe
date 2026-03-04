package me.melinoe.utils.handlers

import me.melinoe.Melinoe
import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.core.EventBus
import me.melinoe.events.core.on
import me.melinoe.features.impl.visual.dungeontimer.TimerModule
import me.melinoe.utils.ChatManager.hideMessage
import me.melinoe.utils.Color
import me.melinoe.utils.Message
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.BagTracker
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.BossType
import me.melinoe.utils.getCenteredText
import me.melinoe.utils.noControlCodes
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
    
    init {
        EventBus.subscribe(this)
        
        on<ChatPacketEvent> {
            if (!ServerUtils.isOnTelos()) return@on
            handleMessage(value)
        }
    }
    
    /**
     * Handle incoming chat messages for boss defeat tracking
     */
    private fun ChatPacketEvent.handleMessage(message: String) {
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
        
        // If we're toggling ON (first separator), hide it and send our own separator
        if (trackerBit && !wasTrackingBefore) {
            hideMessage() // Hide the first separator
            Message.separator() // Send our separator
        }
        
        // If we're toggling OFF (second separator) and have a pending boss name, process tracking
        if (!trackerBit && wasTrackingBefore && pendingBossName != null) {
            processBossDefeat(pendingBossName!!)
            pendingBossName = null
            hideMessage() // Hide the second separator
            Message.separator() // Send our separator
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
        pendingBossName = cleanValue
        bossNameCaptured = true
        
        // Find the boss data and send custom message immediately
        val bossData = BossData.findByKey(strippedValue)
        if (bossData != null && bossData.bossType == BossType.DUNGEON) {
            // Send the custom message (separator was already sent when hiding first separator)
            TimerModule.onBossDefeated(bossData)
            // Add separator after boss defeat message, before damage stats
            Message.separator()
        }
        
        hideMessage() // Hide only the boss name line
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
        hideMessage() // Hide the original message
        
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
            val nameColorRgb = when (medal) {
                "𕑱" -> Color(0xFFD700).rgba // Gold (1st place)
                "𕑰" -> Color(0xC0C0C0).rgba // Silver (2nd place)
                "𕑩" -> Color(0x895129).rgba // Bronze (3rd place)
                else -> null // Light gray (4th+ place - use default §7)
            }
            
            // Damage color (red)
            val damageColorRgb = Color(0xFF3333).rgba
            
            // Build the formatted string first for centering
            val plainText = "$medal $username — $percentage $damage"
            val centeredSpaces = getCenteredText(plainText).takeWhile { it == ' ' }
            
            // Build the message as a Component with proper colors
            val messageComponent = Component.literal(centeredSpaces)
            
            // Add medal with same color as username
            if (nameColorRgb != null) {
                messageComponent.append(Component.literal("$medal ").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(nameColorRgb))
                })
            } else {
                messageComponent.append(Component.literal("§7$medal ")) // Light gray fallback
            }
            
            // Add username with color
            if (nameColorRgb != null) {
                messageComponent.append(Component.literal(username).withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(nameColorRgb))
                })
            } else {
                messageComponent.append(Component.literal("§7$username")) // Light gray fallback
            }
            
            // Add separator and stats
            messageComponent
                .append(Component.literal(" §8—")) // Dark gray dash
                .append(Component.literal(" $percentage $damage").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(damageColorRgb))
                })
            
            // Send the component
            hideMessage()
            Melinoe.mc.execute {
                Melinoe.mc.gui?.chat?.addMessage(messageComponent)
            }
        } else {
            // Fallback: just center the original if parsing fails
            Message.centeredRaw(cleanValue)
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
