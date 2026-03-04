package me.melinoe.features.impl.tracking

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.events.BossBarUpdateEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.Item
import me.melinoe.utils.data.persistence.DataConfig
import me.melinoe.utils.data.persistence.TrackingKey
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Pity Counter Module - displays pity counters for items from the current boss
 * 
 * Features:
 * - Item-based tracking (not boss-based)
 * - Icon rendering for each item
 * - Compact mode toggle
 * - Dynamic filtering based on current boss
 * - Rarity-based color coding
 */
object PityCounterModule : Module(
    name = "Pity Counter",
    category = Category.TRACKING,
    description = "Displays pity counters for items from the current boss"
) {
    
    // Settings - Color for widget border and title
    private val widgetColor by ColorSetting("Widget Color", Color(0xFFAA0000.toInt()), desc = "Color for the widget border and title")
    
    // Compact mode - show only icons
    private val compactMode by BooleanSetting("Compact Mode", false, desc = "Show only item icons without names")
    
    // Value color (always white by default, like Ttt)
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for pity counter values")

    val useCustomMsg by BooleanSetting("Custom Drop", default = true, desc = "Show custom drop messages which include pity")
    val showAnnounceButton by BooleanSetting("Announce Button", true, desc = "Show the announce button at the end of drop messages")
    
    // Current boss tracking
    private var currentBossData: BossData? = null
    
    // Cached pity counters for instant updates
    private val cachedPityCounters = mutableMapOf<String, Int>()
    
    init {
        // Register callback for instant updates when pity changes
        DataConfig.registerUpdateCallback {
            updateCache()
        }
        
        // Initial cache load
        updateCache()
        
        // Listen for dungeon entry/exit
        on<DungeonEntryEvent> {
            handleDungeonEntry(dungeon)
        }
        
        on<DungeonExitEvent> {
            currentBossData = null
        }
        
        // Listen for boss bar updates (world bosses)
        on<BossBarUpdateEvent> {
            handleBossBarUpdate(bossBarMap)
        }
    }
    
    /**
     * Update cached pity counters for all items
     */
    private fun updateCache() {
        // Update cache for all items that might be displayed
        Item.entries.forEach { item ->
            val pityKey = TrackingKey.PityCounter(item.name)
            cachedPityCounters[item.name] = TypeSafeDataAccess.get(pityKey) ?: 0
        }
    }
    
    /**
     * Handle dungeon entry - set current boss to dungeon's final boss
     */
    private fun handleDungeonEntry(dungeonData: DungeonData) {
        currentBossData = dungeonData.finalBoss
    }
    
    /**
     * Handle boss bar update - set current boss for world bosses
     */
    private fun handleBossBarUpdate(bossBarMap: Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent>) {
        // Check if in dungeon - don't override dungeon boss
        val currentArea = try {
            LocalAPI.getCurrentCharacterArea()
        } catch (e: Exception) {
            null
        }
        
        if (currentArea != null && DungeonData.findByKey(currentArea) != null) {
            return // In dungeon, keep dungeon boss
        }
        
        // Get boss name from boss bar
        val bossName = bossBarMap.values.firstOrNull()?.name?.string
        if (bossName != null) {
            // Find world boss by name
            currentBossData = BossData.findByKey(bossName)
        }
    }
    
    /**
     * Get items to display based on current boss
     */
    private fun getItemsToDisplay(): List<Item> {
        val boss = currentBossData ?: return emptyList()
        return boss.items.toList()
    }
    
    /**
     * Get text color for item rarity
     */
    private fun getTextColor(rarity: Item.Rarity): Int {
        return when (rarity) {
            Item.Rarity.IRRADIATED -> 0xFF15CD15.toInt()
            Item.Rarity.GILDED -> 0xFFDF5320.toInt()
            Item.Rarity.ROYAL -> 0xFFAA00AA.toInt()
            Item.Rarity.BLOODSHOT -> 0xFFAA0000.toInt()
            Item.Rarity.VOIDBOUND -> 0xFF4169E1.toInt()
            Item.Rarity.UNHOLY -> 0xFFBFBFBF.toInt()
            Item.Rarity.COMPANION -> 0xFFFFAA00.toInt()
            Item.Rarity.RUNE -> 0xFF616161.toInt()
        }
    }
    
    /**
     * HUD rendering - similar to LifetimeStats with icons from BossTracker
     */
    private val pityCounterHud by HUDSetting(
        name = "Pity Counter Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = false,
        description = "Position of the pity counter display",
        module = this
    ) render@{ example ->
        if (!enabled && !example) return@render Pair(100, 50)
        if (!ServerUtils.isOnTelos() && !example) return@render Pair(100, 50)
        
        // Get items to display
        val items = if (example) {
            // Show Eddie's drops as example
            listOf(
                Item.BLUNDERBOW,
                Item.LOST_TREASURE_SCRIPTURE,
                Item.SLIME_ARCHER,
                Item.GOLDEN_STALLION
            )
        } else {
            getItemsToDisplay()
        }
        
        if (items.isEmpty() && !example) return@render Pair(100, 50)
        
        // Get boss name for title
        val bossName = if (example) {
            "Eddie"
        } else {
            currentBossData?.label ?: "Pity Counters"
        }
        
        val font = mc.font
        val title = bossName
        val titleComponent = Component.literal(title).withStyle(ChatFormatting.BOLD)
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        // Create lighter version of widget color for labels
        val r = ((titleColor shr 16) and 0xFF)
        val g = ((titleColor shr 8) and 0xFF)
        val b = (titleColor and 0xFF)
        val lighterR = minOf(255, (r * 1.8).toInt())
        val lighterG = minOf(255, (g * 1.8).toInt())
        val lighterB = minOf(255, (b * 1.8).toInt())
        val labelColor = 0xFF000000.toInt() or (lighterR shl 16) or (lighterG shl 8) or lighterB
        
        // Calculate dimensions
        val lineSpacing = 16 // Same as BossTracker
        val iconSize = 16
        val iconPadding = 18 // Icon + padding = 18px (matches BossTracker)
        val titleWidth = font.width(titleComponent)
        
        // Check if chat is focused to determine width calculation
        val isChatFocusedForWidth = example || mc.screen is net.minecraft.client.gui.screens.ChatScreen
        val truncationWidth = 100 // Fixed width for truncation when chat is closed
        
        val maxLabelWidth = if (compactMode) {
            0 // No labels in compact mode
        } else if (isChatFocusedForWidth) {
            // Use full width when chat is open or in example mode
            items.maxOfOrNull { font.width(it.displayName) } ?: 100
        } else {
            // Use truncation width when chat is closed
            truncationWidth
        }
        
        val maxValueWidth = if (compactMode) {
            0
        } else {
            items.maxOfOrNull { item ->
                val pityCount = if (example) {
                    // Use same fixed values as in rendering
                    when (item) {
                        Item.BLUNDERBOW -> 42
                        Item.LOST_TREASURE_SCRIPTURE -> 87
                        Item.SLIME_ARCHER -> 15
                        Item.GOLDEN_STALLION -> 103
                        else -> 50
                    }
                } else {
                    cachedPityCounters[item.name] ?: 0
                }
                font.width(pityCount.toString())
            } ?: font.width("999")
        }
        
        val contentWidth = iconPadding + maxLabelWidth + maxValueWidth + (if (!compactMode) 10 else 0)
        val boxWidth = maxOf(titleWidth + 16, contentWidth + 12)
        val boxHeight = font.lineHeight + 2 + (items.size * lineSpacing) + 4
        
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
        
        // Draw items
        var yOffset = font.lineHeight + 4
        val leftPadding = 6
        
        // Check if chat is focused for full names (or if in example mode)
        val isChatFocused = example || mc.screen is net.minecraft.client.gui.screens.ChatScreen
        
        for (item in items) {
            var xOffset = leftPadding
            
            // Render item icon - create ResourceLocation from texture path
            val resourceLocation = ResourceLocation.parse(item.texturePath)
            val itemStack = ItemStack(net.minecraft.world.item.Items.CARROT_ON_A_STICK)
            itemStack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, resourceLocation)
            
            renderItem(itemStack, xOffset, yOffset - 2)
            xOffset += iconPadding
            
            if (!compactMode) {
                // Get pity count
                val pityCount = if (example) {
                    // Use fixed example values for each item
                    when (item) {
                        Item.BLUNDERBOW -> 42
                        Item.LOST_TREASURE_SCRIPTURE -> 87
                        Item.SLIME_ARCHER -> 15
                        Item.GOLDEN_STALLION -> 103
                        else -> 50
                    }
                } else {
                    cachedPityCounters[item.name] ?: 0
                }
                
                // Get text color for rarity
                val textColor = getTextColor(item.rarity)
                
                // Draw item name (truncated if chat closed)
                val truncationWidth = 100 // Fixed width for truncation when chat is closed
                val itemName = if (isChatFocused) {
                    item.displayName
                } else {
                    // Truncate to fit truncationWidth
                    val fullName = item.displayName
                    if (font.width(fullName) > truncationWidth) {
                        var truncated = fullName
                        while (font.width(truncated + "…") > truncationWidth && truncated.isNotEmpty()) {
                            truncated = truncated.dropLast(1)
                        }
                        truncated + "…"
                    } else {
                        fullName
                    }
                }
                
                drawString(font, itemName, xOffset, yOffset, textColor, false)
                
                // Draw pity value (right-aligned)
                val valueX = boxWidth - font.width(pityCount.toString()) - 6
                drawString(font, pityCount.toString(), valueX, yOffset, valueColor.rgba, false)
            }
            
            yOffset += lineSpacing
        }
        
        Pair(boxWidth, boxHeight)
    }
}
