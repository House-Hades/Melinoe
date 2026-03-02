package me.melinoe.features.impl.tracking

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.utils.data.persistence.DataConfig
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey
import me.melinoe.utils.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.ChatFormatting

/**
 * Lifetime Stats Module - Displays lifetime statistics for bag drops
 */
object LifetimeStatsModule : Module(
    name = "Lifetime Stats",
    category = Category.TRACKING,
    description = "Displays lifetime statistics for bag drops and runs"
) {

    // Color setting for border and title
    private val widgetColor by ColorSetting("Widget Color", Color(0xFFFFFF00.toInt()), desc = "Color for the widget border and title")

    // Toggleable stats (in display order)
    private val showEventBags by BooleanSetting("Event Bags", true, desc = "Display event bags counter")
    private val showCompanionBags by BooleanSetting("Companion Bags", true, desc = "Display companion bags counter")
    private val showRoyalBags by BooleanSetting("Royal Bags", true, desc = "Display royal bags counter")
    private val showBloodshotBags by BooleanSetting("Bloodshot Bags", true, desc = "Display bloodshot bags counter")
    private val showVoidboundBags by BooleanSetting("Voidbound Bags", true, desc = "Display voidbound bags counter")
    private val showUnholyBags by BooleanSetting("Unholy Bags", true, desc = "Display unholy bags counter")
    private val showTotalRuns by BooleanSetting("Total Runs", true, desc = "Display total runs counter")

    // Cached values for instant updates
    private var cachedTotalRuns = 0
    private var cachedBloodshotBags = 0
    private var cachedUnholyBags = 0
    private var cachedVoidboundBags = 0
    private var cachedRoyalBags = 0
    private var cachedCompanionBags = 0
    private var cachedEventBags = 0

    init {
        // Register callback for instant updates when stats change
        DataConfig.registerUpdateCallback {
            updateCache()
        }
        
        // Initial cache load
        updateCache()
    }

    private fun updateCache() {
        cachedTotalRuns = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.TotalRuns) ?: 0
        cachedBloodshotBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.BloodshotBags) ?: 0
        cachedUnholyBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.UnholyBags) ?: 0
        cachedVoidboundBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.VoidboundBags) ?: 0
        cachedRoyalBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.RoyalBags) ?: 0
        cachedCompanionBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.CompanionBags) ?: 0
        cachedEventBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.EventBags) ?: 0
    }

    private val lifetimeStatsHud by HUDSetting(
        name = "Lifetime Stats Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = false,
        description = "Position of the lifetime stats display",
        module = this
    ) render@{ example ->
        if (!enabled && !example) return@render Pair(0, 0)
        
        val stats = if (example) {
            linkedMapOf<String, Int>().apply {
                if (showEventBags) put("Events", 7)
                if (showCompanionBags) put("Companions", 15)
                if (showRoyalBags) put("Royal", 23)
                if (showBloodshotBags) put("Bloodshot", 56)
                if (showVoidboundBags) put("Voidbound", 8)
                if (showUnholyBags) put("Unholy", 12)
                if (showTotalRuns) put("Total Runs", 1234)
            }
        } else {
            linkedMapOf<String, Int>().apply {
                if (showEventBags) put("Events", cachedEventBags)
                if (showCompanionBags) put("Companions", cachedCompanionBags)
                if (showRoyalBags) put("Royals", cachedRoyalBags)
                if (showBloodshotBags) put("Bloodshots", cachedBloodshotBags)
                if (showVoidboundBags) put("Voidbounds", cachedVoidboundBags)
                if (showUnholyBags) put("Unholys", cachedUnholyBags)
                if (showTotalRuns) put("Total Runs", cachedTotalRuns)
            }
        }
        
        val font = mc.font
        val title = "Lifetime Stats"
        val titleComponent = net.minecraft.network.chat.Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD)
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        // Create lighter version of widget color for labels (increase brightness by 80%)
        val r = ((titleColor shr 16) and 0xFF)
        val g = ((titleColor shr 8) and 0xFF)
        val b = (titleColor and 0xFF)
        val lighterR = minOf(255, (r * 1.8).toInt())
        val lighterG = minOf(255, (g * 1.8).toInt())
        val lighterB = minOf(255, (b * 1.8).toInt())
        val labelColor = 0xFF000000.toInt() or (lighterR shl 16) or (lighterG shl 8) or lighterB
        
        // Calculate dimensions - use bold title width
        val lineSpacing = 11 // Line spacing between entries
        val titleWidth = font.width(titleComponent)
        val maxLabelWidth = if (stats.isNotEmpty()) {
            stats.keys.maxOfOrNull { font.width(it) } ?: font.width("Example Stat")
        } else {
            font.width("No stats data")
        }
        val maxValueWidth = if (stats.isNotEmpty()) {
            stats.values.maxOfOrNull { font.width(it.toString()) } ?: font.width("9999")
        } else {
            0
        }
        val contentWidth = maxLabelWidth + maxValueWidth + 10
        val boxWidth = maxOf(titleWidth + 16, contentWidth + 12) // 6px padding on each side = 12px total
        val boxHeight = font.lineHeight + 2 + (maxOf(stats.size, 1) * lineSpacing) + 4
        
        // Draw background
        fill(1, 0, boxWidth - 1, boxHeight, bgColor)
        fill(0, 1, 1, boxHeight - 1, bgColor)
        fill(boxWidth - 1, 1, boxWidth, boxHeight - 1, bgColor)
        
        // Draw borders
        val strHeightHalf = font.lineHeight / 2
        val strAreaWidth = titleWidth + 4
        
        // Top border (split around title)
        fill(2, 1 + strHeightHalf, 6, 2 + strHeightHalf, borderColor)
        fill(2 + strAreaWidth + 4, 1 + strHeightHalf, boxWidth - 2, 2 + strHeightHalf, borderColor)
        // Bottom border
        fill(2, boxHeight - 2, boxWidth - 2, boxHeight - 1, borderColor)
        // Left border
        fill(1, 2 + strHeightHalf, 2, boxHeight - 2, borderColor)
        // Right border
        fill(boxWidth - 2, 2 + strHeightHalf, boxWidth - 1, boxHeight - 2, borderColor)
        
        // Draw title in bold
        drawString(font, titleComponent, 8, 2, borderColor, false)
        
        // Draw stats
        if (stats.isNotEmpty()) {
            var yOffset = font.lineHeight + 4
            val leftPadding = 6 // Padding from left edge
            val rightPadding = 6 // Padding from right edge
            for ((label, value) in stats) {
                val labelText = "$label:"
                val valueText = value.toString()
                
                drawString(font, labelText, leftPadding, yOffset, labelColor, false)
                
                val valueX = boxWidth - font.width(valueText) - rightPadding
                drawString(font, valueText, valueX, yOffset, 0xFF000000.toInt() or (ChatFormatting.YELLOW.color ?: 0xFFFF00), false)
                
                yOffset += lineSpacing
            }
        } else {
            // Example mode with no data
            val exampleText = "No stats data"
            drawString(font, exampleText, 6, font.lineHeight + 4, 0xFF808080.toInt(), false)
        }
        
        Pair(boxWidth, boxHeight)
    }

    override fun onEnable() {
        super.onEnable()
    }

    override fun onDisable() {
        super.onDisable()
    }
}
