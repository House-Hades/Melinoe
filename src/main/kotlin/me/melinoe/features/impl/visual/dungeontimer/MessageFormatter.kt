package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.PersonalBestManager
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import net.minecraft.network.chat.Component

/**
 * Formats dungeon completion and split messages with personal best comparisons.
 */
object MessageFormatter {
    
    /**
     * Formats a dungeon completion message with boss defeat and PB comparison.
     */
    fun formatCompletionMessage(
        dungeon: DungeonData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType)
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType)
        
        return Component.empty()
            .append(Component.literal("${Constants.ICON_SKULL} §7Defeated "))
            .append(Component.literal(dungeon.finalBoss.label).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(bossColor))
            })
            .append(Component.literal("§7 in "))
            .append(Component.literal(PersonalBestManager.formatTimeWithDecimals(time)).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(timeColor))
            })
            .append(formatPBComparison(time, oldPB, isNewPB))
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
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType)
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType)
        
        return Component.empty()
            .append(Component.literal("${Constants.ICON_SPLIT} Split: §7Defeated "))
            .append(Component.literal(boss.label).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(bossColor))
            })
            .append(Component.literal("§7 in "))
            .append(Component.literal(PersonalBestManager.formatTimeWithDecimals(time)).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(timeColor))
            })
            .append(formatPBComparison(time, oldPB, isNewPB))
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
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType)
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType)
        
        return Component.empty()
            .append(Component.literal("${Constants.ICON_SKULL} §7Defeated "))
            .append(Component.literal(boss.label).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(bossColor))
            })
            .append(Component.literal("§7 in "))
            .append(Component.literal(PersonalBestManager.formatTimeWithDecimals(splitTime)).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(timeColor))
            })
            .append(formatPBComparison(splitTime, oldPB, wasNewPB))
    }
    
    /**
     * Formats the personal best comparison section of a message.
     */
    private fun formatPBComparison(time: Float, oldPB: Float, isNewPB: Boolean): Component {
        if (isNewPB) {
            val improvement = if (oldPB != -1f) {
                val diff = time - oldPB
                val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(diff)
                " §8(§a$diffStr§8)"
            } else ""
            return Component.literal(" ${Constants.ICON_FIRE} §6§lNEW RECORD!$improvement")
        }
        
        if (oldPB != -1f) {
            val difference = time - oldPB
            val color = if (difference > 0) "§c" else "§a"
            val oldPBStr = PersonalBestManager.formatTimeWithDecimals(oldPB)
            val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(difference)
            return Component.literal(" §8(${Constants.ICON_STAR} §7$oldPBStr §8| $color$diffStr§8)")
        }
        
        return Component.empty()
    }
}
