package me.melinoe.features.impl.tracking

import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.events.BossBarUpdateEvent
import me.melinoe.events.DungeonChangeEvent
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
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Pity Counter Module - displays pity counters for items from the current boss
 *
 * Features:
 * - Item-based tracking (not boss-based)
 * - Icon rendering for each item
 * - Dynamic filtering based on current boss
 * - Rarity-based color coding
 */
object PityCounterModule : Module(
    name = "Pity Counter",
    category = Category.TRACKING,
    description = "Displays pity counters for items from the current boss"
) {
    
    // Toggle to show or hide the HUD
    private val toggleHud by BooleanSetting("Show HUD", default = true, desc = "Toggle the visibility of the Pity Counter HUD")
    
    // Settings - Color for widget border and title
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF2E8F78.toInt()), desc = "Color for the widget border and title")
    
    // Value color (always white by default, like Ttt)
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for pity counter values")
    
    // Truncation setting to limit the number of visible characters
    private val maxCharacters by NumberSetting("Max Characters", 15, min = 0, max = 30, desc = "Maximum number of characters for item names (excluding apostrophes)")
    
    val useCustomMsg by BooleanSetting("Custom Drop", default = true, desc = "Show custom drop messages which include pity")
    val showAnnounceButton by BooleanSetting("Announce Button", true, desc = "Show the announce button at the end of drop messages")
    
    // Current boss tracking
    private var currentBossData: BossData? = null
    
    // Memory Caches to massively improve performance
    private val cachedPityCounters = mutableMapOf<String, Int>()
    private val cachedItemStack = mutableMapOf<Item, ItemStack>()
    private val cachedTruncatedName = mutableMapOf<Pair<String, Int>, String>()
    private var cachedTitleComponent: Component? = null
    
    private var lastBossName = ""
    
    // Pre-mapped lists to prevent creating them every frame
    private val shadowlandsBosses = listOf(BossData.DEFENDER, BossData.REAPER, BossData.WARDEN, BossData.HERALD)
    private val realmBossMapping by lazy {
        me.melinoe.features.impl.tracking.bosstracker.BossData.entries
            .filter { it.name !in listOf("RAPHAEL", "DEFENDER", "REAPER", "WARDEN", "HERALD") }
            .mapNotNull { trackerBoss ->
                BossData.findByKey(trackerBoss.label)?.let { dataBoss -> trackerBoss to dataBoss }
            }
    }
    
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
        
        on<DungeonChangeEvent> {
            handleDungeonEntry(newDungeon)
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
        Item.entries.forEach { item ->
            val pityKey = TrackingKey.PityCounter(item.name)
            cachedPityCounters[item.name] = TypeSafeDataAccess.get(pityKey) ?: 0
        }
    }
    
    /**
     * Get or create cached ItemStack to avoid creating objects every frame
     */
    private fun getItemStack(item: Item): ItemStack {
        return cachedItemStack.getOrPut(item) {
            val itemStack = ItemStack(net.minecraft.world.item.Items.CARROT_ON_A_STICK)
            itemStack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, ResourceLocation.parse(item.texturePath))
            itemStack
        }
    }
    
    /**
     * Evaluates the string character by character, ignoring apostrophes from the count,
     * and adds an ellipsis if the length exceeds the config setting
     */
    private fun getTruncatedName(name: String, maxChars: Int): String {
        // If the max allowed characters is 0 or less, immediately return the dash indicator
        if (maxChars <= 0) return "-"
        
        // Cache based on the item name
        val key = Pair(name, maxChars)
        return cachedTruncatedName.getOrPut(key) {
            var validCharCount = 0
            
            for (i in name.indices) {
                // Only count characters that aren't apostrophes
                if (name[i] != '\'') {
                    validCharCount++
                }
                
                if (validCharCount > maxChars) {
                    // Slices the string from the beginning up to the preset setting
                    return@getOrPut name.substring(0, i) + "…"
                }
            }
            
            name
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
        
        if (LocalAPI.isInDungeon()) {
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
        // Skip if the player is in the nexus
        if (LocalAPI.isInNexus()) return emptyList()
        
        val player = mc.player ?: return emptyList()
        
        val px = player.x
        val py = player.y
        val pz = player.z
        
        // Shadowlands bosses section
        if (LocalAPI.getCurrentCharacterArea() == "Shadowlands") {
            var minDistance = Double.MAX_VALUE
            
            for (boss in shadowlandsBosses) {
                val dist = kotlin.math.abs(px - boss.spawnPosition!!.x) +
                        kotlin.math.abs(py - boss.spawnPosition.y) +
                        kotlin.math.abs(pz - boss.spawnPosition.z)
                
                if (dist < minDistance) {
                    minDistance = dist
                    currentBossData = boss
                }
            }
            
            return currentBossData!!.items.toList()
        }
        
        // Dungeon section
        if (currentBossData != null) return currentBossData!!.items.toList()
        
        // Realm boss section
        var nearestBossData: BossData? = null
        var minDistance = 5625.0 // 75 squared
        
        // Scan for realm bosses
        for ((boss, dataBoss) in realmBossMapping) {
            val pos = boss.spawnPosition
            
            // Calculate distance to the center of the boss spawn block
            val dx = px - (pos.x + 0.5)
            val dy = py - (pos.y + 0.5)
            val dz = pz - (pos.z + 0.5)
            val distance = dx * dx + dy * dy + dz * dz
            
            if (distance <= minDistance) {
                minDistance = distance
                nearestBossData = dataBoss
            }
        }
        
        return nearestBossData?.items?.toList() ?: emptyList()
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
            Item.Rarity.VOIDBOUND -> 0xFF8D15F0.toInt()
            Item.Rarity.UNHOLY -> 0xFFBFBFBF.toInt()
            Item.Rarity.COMPANION -> 0xFFFFAA00.toInt()
            Item.Rarity.RUNE -> 0xFF616161.toInt()
        }
    }
    
    /**
     * HUD rendering
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
        // Check if HUD is toggled
        if (!toggleHud && !example) return@render Pair(0, 0)
        
        if (!ServerUtils.isOnTelos() && !example) return@render Pair(100, 50)
        
        // Get items to display
        val items : List<Item> = if (example) {
            // Show Eddie's drops as an example
            listOf(Item.BLUNDERBOW, Item.LOST_TREASURE_SCRIPTURE, Item.SLIME_ARCHER, Item.GOLDEN_STALLION)
        } else if (LocalAPI.getCurrentCharacterArea().equals("Rustborn Kingdom")) {
            (BossData.VALERION.items + BossData.NEBULA.items + BossData.OPHANIM.items).toList()
        } else {
            getItemsToDisplay()
        }
        
        if (items.isEmpty()) return@render Pair(100, 50)
        
        val font = mc.font
        val isChatFocused = example || mc.screen is net.minecraft.client.gui.screens.ChatScreen
        var anyFullyTruncated = false
        
        val processedItems = items.map { item ->
            var itemName = item.displayName
            
            // If chat isn't focused, truncate
            if (!isChatFocused) {
                itemName = getTruncatedName(itemName, maxCharacters)
                if (itemName == "-") anyFullyTruncated = true
            }
            
            val pityCount = if (example) {
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
            
            Triple(item, itemName, pityCount.toString())
        }
        
        // Get boss name for title
        val bossName = if (anyFullyTruncated) {
            "Pity"
        } else if (example) {
            "Eddie"
        } else if (LocalAPI.getCurrentCharacterArea().equals("Rustborn Kingdom")){
            "Rustborn Kingdom"
        } else {
            currentBossData?.label ?: "Pity Counters"
        }
        
        // Cache the title to only format it once
        if (bossName != lastBossName || cachedTitleComponent == null) {
            lastBossName = bossName
            cachedTitleComponent = Component.literal(bossName).withStyle(ChatFormatting.BOLD)
        }
        
        val titleComponent = cachedTitleComponent!!
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        // Calculate dimensions
        val lineSpacing = 16
        val targetItemSize = 14
        val itemPadding = targetItemSize + 4
        val titleWidth = font.width(titleComponent)
        
        val spaceWidth = font.width(" ")
        val doubleSpaceWidth = spaceWidth * 2
        val dashWidth = font.width("-")
        
        // Get actual space taken by longest required label
        val maxLabelWidth = processedItems.maxOfOrNull { if (it.second == "-") 0 else font.width(it.second) } ?: 0
        val maxValueWidth = processedItems.maxOfOrNull { font.width(it.third) } ?: font.width("999")
        
        val contentWidth = if (maxLabelWidth == 0) {
            targetItemSize + spaceWidth + dashWidth + spaceWidth + maxValueWidth
        } else {
            itemPadding + maxLabelWidth + doubleSpaceWidth + maxValueWidth
        }
        
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
        
        // Draw title
        drawString(font, titleComponent, 8, 2, borderColor, false)
        
        // Draw items
        var yOffset = font.lineHeight + 4
        val leftPadding = 6
        val scaleFactor = targetItemSize.toFloat() / 16f
        
        for ((item, itemName, pityCountStr) in processedItems) {
            var xOffset = leftPadding
            val itemStack = getItemStack(item)
            
            // Item texture scaling & translating to be in the exact center
            pose().pushMatrix()
            val itemY = yOffset + (font.lineHeight / 2f) - (targetItemSize / 2f)
            pose().translate(xOffset.toFloat(), itemY)
            pose().scale(scaleFactor, scaleFactor)
            renderItem(itemStack, 0, 0)
            pose().popMatrix()
            
            xOffset += itemPadding
            val textColor = getTextColor(item.rarity)
            val valueWidth = font.width(pityCountStr)
            val valueX = boxWidth - valueWidth - 6
            var drawX = xOffset
            
            if (itemName == "-") {
                val dashW = font.width(itemName)
                val textureEnd = xOffset - itemPadding + targetItemSize
                val spaceAvailable = valueX - textureEnd
                drawX = textureEnd + (spaceAvailable - dashW) / 2
            }
            
            drawString(font, itemName, drawX, yOffset, textColor, false)
            drawString(font, pityCountStr, valueX, yOffset, valueColor.rgba, false)
            
            yOffset += lineSpacing
        }
        
        Pair(boxWidth, boxHeight)
    }
}