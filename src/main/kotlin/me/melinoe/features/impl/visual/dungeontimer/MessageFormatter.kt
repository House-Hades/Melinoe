package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.PersonalBestManager
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.toNative
import net.minecraft.network.chat.Component

/**
 * Formats dungeon completion and split messages with personal best comparisons.
 */
object MessageFormatter {
    
    /**
     * Helper to convert an integer color to a MiniMessage hex tag
     */
    private fun Int.toHexTag(): String = "<#${String.format("%06X", this and 0xFFFFFF)}>"
    
    /**
     * Formats a dungeon completion message with boss defeat and PB comparison.
     */
    fun formatCompletionMessage(
        dungeon: DungeonData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val bossName = dungeon.finalBoss.label
        val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
        val pbString = getPBComparisonString(dungeon.areaName, time, oldPB, isNewPB)
        
        return "${Constants.ICON_SKULL} <#AAAAAA>Defeated $bossColor$bossName <#AAAAAA>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats a boss split message (for multi-stage dungeons).
     */
    fun formatSplitMessage(
        dungeon: DungeonData,
        boss: BossData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
        val pbString = getPBComparisonString(dungeon.areaName, time, oldPB, isNewPB)
        
        return "${Constants.ICON_SPLIT} Split: <#AAAAAA>Defeated $bossColor${boss.label} <#AAAAAA>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats a boss split summary message (shown at the end of multi-stage dungeons).
     */
    fun formatSplitSummaryMessage(
        dungeon: DungeonData,
        boss: BossData,
        splitTime: Float,
        oldPB: Float,
        wasNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val timeStr = PersonalBestManager.formatTimeWithDecimals(splitTime)
        val pbString = getPBComparisonString(dungeon.areaName,  splitTime, oldPB, wasNewPB)
        
        return "${Constants.ICON_SKULL} <#AAAAAA>Defeated $bossColor${boss.label} <#AAAAAA>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats the personal best comparison section of a message.
     */
    private fun getPBComparisonString(dungeon: String, time: Float, oldPB: Float, isNewPB: Boolean): String {
        if (isNewPB) {
            val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
            val hasOldPB = oldPB != -1f
            
            val shareText: String
            val improveText: String
            
            if (hasOldPB) {
                val diff = time - oldPB
                val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(diff)
                val oldPBStr = PersonalBestManager.formatTimeWithDecimals(oldPB)
                
                shareText = "NEW RECORD! Completed $dungeon in $timeStr! (Old: $oldPBStr | $diffStr)"
                improveText = " <#555555>(<#00FF00>$diffStr<#555555>)"
            } else {
                shareText = "NEW RECORD! Completed $dungeon in $timeStr!"
                improveText = ""
            }
            
            val safeShareText = shareText.replace("'", "\\'")
            
            val shareButton = " <click:suggest_command:'$safeShareText'><hover:show_text:\"<#AAAAAA>Click to share in chat!</#AAAAAA>\"><#AAAAAA><b>⧉</b></#AAAAAA></hover></click>"
            val improvement = "$improveText$shareButton"
            
            return " ${Constants.ICON_FIRE} <#FFD700><bold>NEW RECORD!</bold></#FFD700>$improvement"
        }
        
        if (oldPB != -1f) {
            val difference = time - oldPB
            val diffColor = if (difference > 0) "<#FF3333>" else "<#00FF00>"
            
            val oldPBStr = PersonalBestManager.formatTimeWithDecimals(oldPB)
            val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(difference)
            
            return " <#555555>(${Constants.ICON_STAR} <#AAAAAA>$oldPBStr <#555555>| $diffColor$diffStr<#555555>)"
        }
        
        return ""
    }
}