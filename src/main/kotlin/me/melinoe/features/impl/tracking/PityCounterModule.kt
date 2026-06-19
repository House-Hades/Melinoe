package me.melinoe.features.impl.tracking

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
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
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

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
    private val showHud by BooleanSetting("Show HUD", default = true, desc = "Toggle the visibility of the Pity Counter HUD")
    
    // Settings - Color for widget border and title
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF2E8F78.toInt()), desc = "Color for the widget border and title")
    
    // Value color (always white by default, like Ttt)
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for pity counter values")
    
    // Truncation setting to limit the number of visible characters
    private val maxCharacters by NumberSetting("Max Characters", 15, min = 0, max = 30, desc = "Maximum number of characters for item names (excluding apostrophes)")
    
    val useCustomMsg by BooleanSetting("Custom Drop Message", default = true, desc = "Show custom drop messages which include pity")
    val showAnnounceButton by BooleanSetting("Announce Button", true, desc = "Show the announce button at the end of drop messages").withDependency { useCustomMsg }
    
    // Current boss tracking
    private var currentBossData: BossData? = null
    
    // Memory Caches to massively improve performance
    private val cachedPityCounters = mutableMapOf<String, Int>()
    private val cachedPityStrings = mutableMapOf<String, String>()
    private val cachedPityWidths = mutableMapOf<String, Int>()
    private val cachedItemStacks = mutableMapOf<Item, ItemStack>()
    
    // Throttling for environment calculations
    private var lastEnvCheckTime = 0L
    private var cachedArea = ""
    private var cachedIsOnTelos = false
    private var cachedItemsToDisplay = emptyList<Item>()
    
    // Render loop state tracking
    private var lastItemsToDisplay = emptyList<Item>()
    private var lastMaxChars = -1
    private var wasExample = false
    private var forceRenderUpdate = false
    
    private var lastRenderWidth = 0
    private var lastRenderHeight = 0
    private var cachedRenderPair = Pair(0, 0)
    
    private var cachedTitleComponent: Component? = null
    private var lastBossName = ""
    private var cachedTitleWidth = 0
    
    // Data class to store pre-calculated item render data
    private class CachedRenderItem(
        val item: Item,
        var displayName: String,
        var nameWidth: Int,
        var isFullyTruncated: Boolean,
        var pityValueStr: String,
        var valueWidth: Int,
        var rarityColor: Int
    )
    private var renderDataList = mutableListOf<CachedRenderItem>()
    
    // Pre-mapped collections
    private val shadowlandsBosses by lazy {
        listOfNotNull(
            BossData.byKey("DEFENDER"), BossData.byKey("REAPER"),
            BossData.byKey("WARDEN"), BossData.byKey("HERALD")
        )
    }
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
//        updateCache()
        
        on<DungeonEntryEvent> { handleDungeonEntry(dungeon) } // Listen for dungeon entry
        on<DungeonChangeEvent> { handleDungeonEntry(newDungeon) } // Listen for dungeon changes
        on<DungeonExitEvent> { currentBossData = null; invalidateEnvCache() } // Listen for dungeon exit
        on<BossBarUpdateEvent> { handleBossBarUpdate(bossBarMap) } // Listen for boss bar updates (world bosses)
    }
    
    /**
     * Update cached pity counters for all items
     */
    private fun updateCache() {
        val font = mc.font
        Item.all.forEach { item ->
            val count = TypeSafeDataAccess.get(TrackingKey.PityCounter(item.name)) ?: 0
            cachedPityCounters[item.name] = count
            
            val countStr = count.toString()
            cachedPityStrings[item.name] = countStr
            
            if (font != null) {
                cachedPityWidths[item.name] = font.width(countStr)
            }
        }
        // Force the render loop to rebuild string caches on the next frame
        forceRenderUpdate = true
    }
    
    private fun invalidateEnvCache() {
        lastEnvCheckTime = 0L
    }
    
    private fun getItemStack(item: Item): ItemStack {
        return cachedItemStacks.getOrPut(item) {
            val itemStack = ItemStack(Items.CARROT_ON_A_STICK)
            itemStack.set(DataComponents.ITEM_MODEL, Identifier.parse(item.texturePath))
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
        
        var validCharCount = 0
        for (i in name.indices) {
            if (name[i] != '\'') validCharCount++
            if (validCharCount > maxChars) return name.substring(0, i) + "…"
        }
        return name
    }
    
    /**
     * Handle dungeon entry - set current boss to dungeon's final boss
     */
    private fun handleDungeonEntry(dungeonData: DungeonData) {
        currentBossData = dungeonData.finalBoss
        invalidateEnvCache()
    }
    
    /**
     * Handle boss bar update - set current boss for world bosses
     */
    private fun handleBossBarUpdate(bossBarMap: Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent>) {
        // Check if in dungeon - don't override dungeon boss
        if (LocalAPI.isInDungeon()) return
        
        // Get boss name from boss bar
        val bossName = bossBarMap.values.firstOrNull()?.name?.string ?: return
        // Find world boss by name
        currentBossData = BossData.findByKey(bossName)
        invalidateEnvCache()
    }
    
    private fun updateState() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEnvCheckTime < 500) return
        lastEnvCheckTime = currentTime
        
        cachedIsOnTelos = ServerUtils.isOnTelos()
        if (!cachedIsOnTelos) {
            cachedItemsToDisplay = emptyList()
            return
        }
        
        cachedArea = try { LocalAPI.getCurrentCharacterArea() } catch (e: Exception) { "" }
        if (LocalAPI.isInNexus()) {
            cachedItemsToDisplay = emptyList()
            return
        }
        
        val player = mc.player ?: return
        val px = player.x
        val py = player.y
        val pz = player.z
        
        if (cachedArea == "Shadowlands") {
            if (pz < -360) currentBossData = BossData.byKey("HERALD")
            else if (pz > 500) currentBossData = BossData.byKey("REAPER")
            else if (px < -400) currentBossData = BossData.byKey("WARDEN")
            else {
                var minDistance = Double.MAX_VALUE
                for (boss in shadowlandsBosses) {
                    val sp = boss.spawnPosition ?: continue
                    val dist = kotlin.math.abs(px - sp.x) + kotlin.math.abs(py - sp.y) + kotlin.math.abs(pz - sp.z)
                    if (dist < minDistance) {
                        minDistance = dist
                        currentBossData = boss
                    }
                }
            }
            cachedItemsToDisplay = currentBossData?.items?.toList() ?: emptyList()
            return
        }
        
        if (currentBossData != null) {
            cachedItemsToDisplay = currentBossData!!.items.toList()
            return
        }
        
        var nearestBossData: BossData? = null
        var minDistanceSq = 5625.0 // 75 squared
        
        for ((boss, dataBoss) in realmBossMapping) {
            val pos = boss.spawnPosition
            val dx = px - (pos.x + 0.5)
            val dy = py - (pos.y + 0.5)
            val dz = pz - (pos.z + 0.5)
            val distanceSq = dx * dx + dy * dy + dz * dz
            
            if (distanceSq <= minDistanceSq) {
                minDistanceSq = distanceSq
                nearestBossData = dataBoss
            }
        }
        
        cachedItemsToDisplay = nearestBossData?.items?.toList() ?: emptyList()
    }
    
    /**
     * Builds the render cache only when items or settings change
     */
    private fun updateRenderData(itemsToProcess: List<Item>, example: Boolean) {
        val font = mc.font ?: return
        renderDataList.clear()
        val currentMaxChars = maxCharacters
        
        for (item in itemsToProcess) {
            val itemName = getTruncatedName(item.displayName, currentMaxChars)
            
            val pityCountStr: String
            val valueWidth: Int
            if (example) {
                val exCount = when (item.name) {
                    "BLUNDERBOW" -> 42
                    "LOST_TREASURE_SCRIPTURE" -> 87
                    "SLIME_ARCHER" -> 15
                    "GOLDEN_STALLION" -> 103
                    else -> 50
                }
                pityCountStr = exCount.toString()
                valueWidth = font.width(pityCountStr)
            } else {
                pityCountStr = cachedPityStrings[item.name] ?: "0"
                valueWidth = cachedPityWidths[item.name] ?: font.width(pityCountStr)
            }
            
            renderDataList.add(
                CachedRenderItem(
                    item = item,
                    displayName = itemName,
                    nameWidth = font.width(itemName),
                    isFullyTruncated = (itemName == "-"),
                    pityValueStr = pityCountStr,
                    valueWidth = valueWidth,
                    rarityColor = getTextColor(item.rarity)
                )
            )
        }
    }
    
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
        
        if (!enabled && !example) return@render cachedRenderPair
        if (!showHud && !example) return@render Pair(0, 0)
        
        updateState()
        
        if (!cachedIsOnTelos && !example) return@render cachedRenderPair
        
        // Determine active items for this frame
        val currentItems = if (example) {
            listOfNotNull(
                Item.byKey("BLUNDERBOW"), Item.byKey("LOST_TREASURE_SCRIPTURE"),
                Item.byKey("SLIME_ARCHER"), Item.byKey("GOLDEN_STALLION")
            )
        } else if (cachedArea == "Rustborn Kingdom") {
            BossData.itemsOf("VALERION", "NEBULA", "OPHANIM")
        } else if (cachedArea == "Celestial's Province") {
            BossData.itemsOf("ASMODEUS", "SERAPHIM")
        } else {
            cachedItemsToDisplay
        }
        
        // If there are no items, exit cleanly
        if (currentItems.isEmpty()) return@render Pair(100, 50)
        
        // Update the cache if ANY relevant state changed
        if (forceRenderUpdate || currentItems != lastItemsToDisplay || maxCharacters != lastMaxChars || example != wasExample) {
            updateRenderData(currentItems, example)
            
            lastItemsToDisplay = currentItems
            lastMaxChars = maxCharacters
            wasExample = example
            forceRenderUpdate = false
        }
        
        if (renderDataList.isEmpty()) return@render Pair(100, 50)
        
        val font = mc.font
        var anyFullyTruncated = false
        var maxLabelWidth = 0
        var maxValueWidth = 0
        
        for (i in 0 until renderDataList.size) {
            val data = renderDataList[i]
            if (data.isFullyTruncated) anyFullyTruncated = true
            if (data.nameWidth > maxLabelWidth && !data.isFullyTruncated) maxLabelWidth = data.nameWidth
            if (data.valueWidth > maxValueWidth) maxValueWidth = data.valueWidth
        }
        
        val bossName = if (anyFullyTruncated) "Pity"
        else if (example) "Eddie"
        else if (cachedArea == "Rustborn Kingdom") "Rustborn Kingdom"
        else if (cachedArea == "Celestial's Province") "Celestial's Province"
        else currentBossData?.label ?: "Pity Counters"
        
        // Cache title string and width to avoid rebuilding Component and width checks
        if (bossName != lastBossName || cachedTitleComponent == null) {
            lastBossName = bossName
            cachedTitleComponent = Component.literal(bossName).withStyle(ChatFormatting.BOLD)
            cachedTitleWidth = font.width(cachedTitleComponent!!)
        }
        
        val titleColor = widgetColor.rgba and 0x00FFFFFF
        val borderColor = 0xFF000000.toInt() or titleColor
        val bgColor = 0xC00C0C0C.toInt()
        
        // Calculate dimensions
        val lineSpacing = 16
        val targetItemSize = 14
        val itemPadding = targetItemSize + 4
        
        val spaceWidth = font.width(" ")
        val dashWidth = font.width("-")
        
        val contentWidth = if (maxLabelWidth == 0) {
            targetItemSize + spaceWidth + dashWidth + spaceWidth + maxValueWidth
        } else {
            itemPadding + maxLabelWidth + (spaceWidth * 2) + maxValueWidth
        }
        
        val boxWidth = maxOf(cachedTitleWidth + 16, contentWidth + 12)
        val boxHeight = font.lineHeight + 2 + (renderDataList.size * lineSpacing) + 4
        
        // Update returned dimension pair
        if (boxWidth != lastRenderWidth || boxHeight != lastRenderHeight) {
            lastRenderWidth = boxWidth
            lastRenderHeight = boxHeight
            cachedRenderPair = Pair(boxWidth, boxHeight)
        }
        
        // Draw background
        fill(1, 0, boxWidth - 1, boxHeight, bgColor)
        fill(0, 1, 1, boxHeight - 1, bgColor)
        fill(boxWidth - 1, 1, boxWidth, boxHeight - 1, bgColor)
        
        // Draw borders
        val strHeightHalf = font.lineHeight / 2
        val strAreaWidth = cachedTitleWidth + 4
        
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
        text(font, cachedTitleComponent!!, 8, 2, borderColor, false)
        
        // Draw items
        var yOffset = font.lineHeight + 4
        val leftPadding = 6
        val scaleFactor = targetItemSize.toFloat() / 16f
        
        for (i in 0 until renderDataList.size) {
            val renderData = renderDataList[i]
            var xOffset = leftPadding
            val itemStack = getItemStack(renderData.item)
            
            // Item texture scaling & translating to be in the exact center
            pose().pushMatrix()
            val itemY = yOffset + (font.lineHeight / 2f) - (targetItemSize / 2f)
            pose().translate(xOffset.toFloat(), itemY)
            pose().scale(scaleFactor, scaleFactor)
            item(itemStack, 0, 0)
            pose().popMatrix()
            
            xOffset += itemPadding
            val valueX = boxWidth - renderData.valueWidth - 6
            var drawX = xOffset
            
            if (renderData.isFullyTruncated) {
                val textureEnd = xOffset - itemPadding + targetItemSize
                val spaceAvailable = valueX - textureEnd
                drawX = textureEnd + (spaceAvailable - renderData.nameWidth) / 2
            }
            
            text(font, renderData.displayName, drawX, yOffset, renderData.rarityColor, false)
            text(font, renderData.pityValueStr, valueX, yOffset, valueColor.rgba, false)
            
            yOffset += lineSpacing
        }
        
        return@render cachedRenderPair
    }
}