package me.melinoe.utils.data

import me.melinoe.Melinoe
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.TabListUtils
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB

/**
 * Bag tracking system that detects bag drops and manages pity counters.
 * 
 * Now uses type-safe TrackingKey API for compile-time safety.
 * 
 * Lifetime Stats Tracked:
 * - companionBags: Companion bag drops
 * - royalBags: Royal bag drops
 * - bloodshotBags: Bloodshot totem drops (from various bosses)
 * - voidbound: Voidbound totem drops (Nihility)
 * - unholy: Unholy totem drops (Holy Cross, Pendant of Sin)
 * - eventBags: Event bag drops (Halloween, Valentine, Christmas)
 * - totalRuns: Total boss runs (incremented on boss defeat)
 * 
 * Item Drop Detection:
 * - When a bag animation plays, starts scanning for dropped items near player
 * - Scans for 20 ticks (1 second) after bag drop
 * - Detects specific items by texture path and resets their pity counters
 */
object BagTracker {
    
    private var currentBoss: String = ""
    private var chatNotificationsEnabled = true
    
    // Item scanning state
    private var ticksRemaining = 0
    private val detectedItems = mutableSetOf<Item>()
    private val recentPityCache = mutableMapOf<Item, Int>()
    private val recentPityCacheTime = mutableMapOf<Item, Long>()
    
    init {
        // Register tick handler for item scanning
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null && client.level != null) {
                onTick()
            }
        }
        
        // Register chat message handler for detecting item drops from chat
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) {
                handleChatMessage(message.string)
            }
        }
    }
    
    // ==================== BAG DROP HANDLERS ====================
    
    /**
     * Handle lootbag open sound - starts scanning for dropped items.
     * This is the primary trigger for item scanning (triggered by sound event).
     * Called from SoundSystemMixin when "noise:player.bags.open" sound plays.
     */
    fun handleLootbagOpen() {
        Melinoe.logger.info("Lootbag open sound detected, starting item scanning")
        startItemScanning()
    }
    
    /**
     * Handle bloodshot bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onBloodshotBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Melinoe.logger.info("Bloodshot bag dropped by boss: $currentBoss")
        
        // Increment bloodshot bag lifetime stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.BloodshotBags)
    }
    
    /**
     * Handle unholy bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onUnholyBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Melinoe.logger.info("Unholy bag dropped by boss: $currentBoss")
        
        // Increment unholy stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.UnholyBags)
    }
    
    /**
     * Handle voidbound bag drop - increments lifetime stat
     */
    @JvmOverloads
    fun onVoidboundBagDrop(itemName: String? = null) {
        currentBoss = LocalAPI.getCurrentCharacterFighting()
        Melinoe.logger.info("Voidbound bag dropped by boss: $currentBoss")
        
        // Increment voidbound bag lifetime stat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.VoidboundBags)
    }
    
    /**
     * Handle royal bag drop
     * Increments royalBags stat
     */
    fun onRoyalBagDrop() {
        Melinoe.logger.info("Royal bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.RoyalBags)
    }
    
    /**
     * Handle companion bag drop
     * Increments companionBags stat
     */
    fun onCompanionBagDrop() {
        Melinoe.logger.info("Companion bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.CompanionBags)
    }
    
    /**
     * Handle event bag drop (Halloween, Valentine, Christmas)
     * Increments eventBags stat
     */
    fun onEventBagDrop() {
        Melinoe.logger.info("Event bag dropped")
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.EventBags)
    }
    
    // ==================== BOSS DEFEAT HANDLER ====================
    
    /**
     * Handle boss defeat - increments totalRuns for ANY dungeon/boss defeated
     * Also increments pity counters for ALL items that the boss can drop
     * This should be called when a boss is defeated (detected via chat messages)
     */
    fun onBossDefeat(bossName: String) {
        Melinoe.logger.info("Boss defeated: $bossName")
        
        // Increment total runs for ANY boss defeat
        TypeSafeDataAccess.increment(TrackingKey.LifetimeStat.TotalRuns)
        
        // Increment pity counters for all items this boss can drop
        incrementPityCounters(bossName)
    }
    
    /**
     * Increment pity counters for all items that a boss can drop.
     * Uses Item-based tracking (TrackingKey.PityCounter(item.name)).
     */
    private fun incrementPityCounters(bossName: String) {
        val boss = BossData.findByKey(bossName)
        if (boss == null) {
            Melinoe.logger.warn("Boss not found in BossData: $bossName")
            return
        }
        
        if (boss.items.isEmpty()) {
            Melinoe.logger.debug("Boss $bossName has no items configured, skipping pity increment")
            return
        }
        
        Melinoe.logger.info("Incrementing pity counters for ${boss.items.size} items from boss: $bossName")
        
        // Increment pity for ALL items this boss can drop
        for (item in boss.items) {
            val pityKey = TrackingKey.PityCounter(item.name)
            val newCount = TypeSafeDataAccess.increment(pityKey)
            Melinoe.logger.debug("  ${item.displayName}: $newCount")
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Send pity reset message to chat with loot boost information (client-side only)
     */
    private fun sendPityResetMessage(prefix: String, itemType: String, pityCount: Int) {
        if (!chatNotificationsEnabled) return
        
        val mc = Melinoe.mc
        if (mc.player != null) {
            val lootboost = TabListUtils.getLootboostPercentage() ?: 0
            
            // Define boss name color and chat indicator based on drop type
            val (bossColorRgb, indicatorColor, indicatorSymbol) = when (prefix) {
                "BLOODSHOT" -> Triple(0xC40000, 0x9D0000, "𕌜") // red for bloodshot, first gradient color
                "UNHOLY" -> Triple(0x6F7680, 0x5D6069, "𕑦")    // gray for unholy, first gradient color
                "NIHILITY" -> Triple(0x804299, 0x56167C, "𖈵")  // purple for nihility, first gradient color
                else -> Triple(0xFFFFFF, 0xFFFFFF, "")
            }
            
            // Create chat indicator
            val chatIndicator = net.minecraft.client.GuiMessageTag(
                indicatorColor,
                null,
                net.minecraft.network.chat.Component.literal("$indicatorSymbol $prefix Drop"),
                "$prefix Drop"
            )
            
            // Build gradient prefix based on type
            val gradientPrefix = when (prefix) {
                "BLOODSHOT" -> {
                    // &#9D0000&lB&#B10000&lL&#C40000&lO&#D00000&lO&#DC0000&lD&#E80000&lS&#F00909&lH&#F71111&lO&#FF1A1A&lT
                    net.minecraft.network.chat.Component.literal("")
                        .append(net.minecraft.network.chat.Component.literal("B").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x9D0000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xB10000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xC40000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xD00000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("D").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDC0000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("S").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xE80000)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xF00909)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xF71111)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("T").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF1A1A)).withBold(true) })
                }
                "UNHOLY" -> {
                    // &#5D6069&lU&#6F7680&lN&#868F91&lH&#9CA8A2&lO&#BCC8BC&lL&#DCE8D5&lY
                    net.minecraft.network.chat.Component.literal("")
                        .append(net.minecraft.network.chat.Component.literal("U").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x5D6069)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("N").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x6F7680)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x868F91)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x9CA8A2)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xBCC8BC)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("Y").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDCE8D5)).withBold(true) })
                }
                "NIHILITY" -> {
                    // &#56167C&lN&#6B2C8B&lI&#804299&lH&#985AAB&lI&#B072BC&lL&#BF81C7&lI&#CE91D2&lT&#DDA0DD&lY
                    net.minecraft.network.chat.Component.literal("")
                        .append(net.minecraft.network.chat.Component.literal("N").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x56167C)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x6B2C8B)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x804299)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x985AAB)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xB072BC)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xBF81C7)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("T").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xCE91D2)).withBold(true) })
                        .append(net.minecraft.network.chat.Component.literal("Y").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDDA0DD)).withBold(true) })
                }
                else -> net.minecraft.network.chat.Component.literal(prefix)
            }
            
            // Build the message: SYMBOL PREFIX — From {boss} at X pity [+Y% Loot Boost]
            val messageBuilder = net.minecraft.network.chat.Component.literal("")
                .append(net.minecraft.network.chat.Component.literal("$indicatorSymbol ").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFFFF)) // white/reset
                })
                .append(gradientPrefix)
                .append(net.minecraft.network.chat.Component.literal(" - ").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) // light gray
                })
                .append(net.minecraft.network.chat.Component.literal("From ").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) // light gray
                })
                .append(net.minecraft.network.chat.Component.literal(itemType).withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(bossColorRgb)).withUnderlined(true)
                })
                .append(net.minecraft.network.chat.Component.literal(" at ").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) // light gray
                })
                .append(net.minecraft.network.chat.Component.literal("$pityCount").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF00)) // yellow
                })
                .append(net.minecraft.network.chat.Component.literal(" pity").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)) // light gray
                })
            
            // Only show loot boost if > 0
            if (lootboost > 0) {
                messageBuilder.append(net.minecraft.network.chat.Component.literal(" [+$lootboost% Loot Boost]").withStyle { style ->
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF00)) // yellow
                })
            }
            
            mc.gui?.chat?.addMessage(messageBuilder, null, chatIndicator)
            val logMessage = if (lootboost > 0) {
                "Sent pity reset message: [$prefix] From $itemType at $pityCount pity [+$lootboost% Loot Boost]"
            } else {
                "Sent pity reset message: [$prefix] From $itemType at $pityCount pity"
            }
            Melinoe.logger.info(logMessage)
        }
    }
    
    /**
     * Enable or disable chat notifications
     */
    fun setChatNotificationsEnabled(enabled: Boolean) {
        chatNotificationsEnabled = enabled
    }
    
    /**
     * Get current boss being fought
     */
    fun getCurrentBoss(): String = currentBoss
    
    // ==================== ITEM SCANNING ====================
    
    /**
     * Start scanning for dropped items near the player.
     * Called when a bag animation plays.
     */
    private fun startItemScanning() {
        ticksRemaining = 20 // Scan for 1 second (20 ticks)
        detectedItems.clear()
        Melinoe.logger.info("Started item scanning for 20 ticks")
    }
    
    /**
     * Tick handler for scanning dropped items.
     * Should be called every client tick.
     */
    fun onTick() {
        if (ticksRemaining <= 0) return
        
        val player = Melinoe.mc.player ?: return
        val level = Melinoe.mc.level ?: return
        
        // Create bounding box around player (10 block radius)
        val box = player.boundingBox.inflate(10.0)
        
        // Scan for item entities
        val itemEntities = level.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive }
        
        Melinoe.logger.debug("Scanning for items: ticksRemaining=$ticksRemaining, entities=${itemEntities.size}")
        
        for (itemEntity in itemEntities) {
            val itemStack = itemEntity.item
            
            // Get texture path from item
            val resourceLocation = try {
                itemStack.components.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)
            } catch (e: Exception) {
                null
            }
            
            if (resourceLocation == null) continue
            
            // Convert ResourceLocation to string format "namespace:path"
            val texturePath = "${resourceLocation.namespace}:${resourceLocation.path}"
            
            Melinoe.logger.debug("Found item entity with texture: $texturePath")
            
            // Find item by texture path
            val droppedItem = resolveContextualItem(texturePath)
            
            if (droppedItem != null && !detectedItems.contains(droppedItem)) {
                // Cache pity before reset (for chat message)
                val pityKey = TrackingKey.PityCounter(droppedItem.name)
                val preResetPity = TypeSafeDataAccess.get(pityKey) ?: 0
                recentPityCache[droppedItem] = preResetPity
                recentPityCacheTime[droppedItem] = System.currentTimeMillis()
                
                // Send chat notification
                if (chatNotificationsEnabled) {
                    sendPityResetMessage(droppedItem, preResetPity)
                }
                
                // Reset pity for this item
                TypeSafeDataAccess.reset(pityKey)
                Melinoe.logger.info("Detected and reset pity for ${droppedItem.displayName} (was at $preResetPity)")
                
                // Mark as detected
                detectedItems.add(droppedItem)
            }
        }
        
        ticksRemaining--
    }
    
    /**
     * Handle chat messages to detect item drops from "X got Y" messages.
     * This is a fallback detection method in case item entity scanning misses drops.
     */
    private fun handleChatMessage(plainText: String) {
        // Look for " got " pattern (e.g., "PlayerName got Holy Cross")
        val gotIndex = plainText.lastIndexOf(" got ")
        if (gotIndex == -1) return
        
        // Extract player name
        val spaceBeforePlayer = plainText.lastIndexOf(' ', gotIndex - 1)
        if (spaceBeforePlayer == -1) return
        
        val playerName = plainText.substring(spaceBeforePlayer + 1, gotIndex)
        
        // Only process if it's the current player
        val currentPlayerName = Melinoe.mc.player?.gameProfile?.name ?: return
        if (playerName != currentPlayerName) return
        
        // Find the longest matching item display name after " got "
        var droppedItem: Item? = null
        for (item in Item.values()) {
            val itemDisplayName = item.displayName
            val itemStartIndex = gotIndex + 5 // " got ".length
            
            if (plainText.indexOf(itemDisplayName, itemStartIndex) == itemStartIndex) {
                // Prefer longer matches (e.g., "Lost Treasure Scripture" over "Lost Treasure")
                if (droppedItem == null || itemDisplayName.length > droppedItem.displayName.length) {
                    droppedItem = item
                }
            }
        }
        
        if (droppedItem == null) return
        
        // Resolve contextual item (hardmode variants)
        droppedItem = resolveContextualItemByDisplayName(droppedItem)
        
        // Only process high-value items
        val rarity = droppedItem.rarity
        if (rarity != Item.Rarity.ROYAL &&
            rarity != Item.Rarity.BLOODSHOT &&
            rarity != Item.Rarity.VOIDBOUND &&
            rarity != Item.Rarity.UNHOLY &&
            rarity != Item.Rarity.COMPANION) {
            return
        }
        
        // Check if we already detected this item recently (avoid duplicate processing)
        if (detectedItems.contains(droppedItem)) {
            Melinoe.logger.debug("Item ${droppedItem.displayName} already detected via entity scan, skipping chat detection")
            return
        }
        
        // Cache pity before reset
        val pityKey = TrackingKey.PityCounter(droppedItem.name)
        val preResetPity = TypeSafeDataAccess.get(pityKey) ?: 0
        recentPityCache[droppedItem] = preResetPity
        recentPityCacheTime[droppedItem] = System.currentTimeMillis()
        
        // Send chat notification
        if (chatNotificationsEnabled) {
            sendPityResetMessage(droppedItem, preResetPity)
        }
        
        // Reset pity for this item
        TypeSafeDataAccess.reset(pityKey)
        Melinoe.logger.info("Detected and reset pity for ${droppedItem.displayName} via chat message (was at $preResetPity)")
        
        // Mark as detected
        detectedItems.add(droppedItem)
    }
    
    /**
     * Resolve contextual item from texture path.
     * Handles hardmode variants (True Ophan, True Seraph, etc.)
     */
    private fun resolveContextualItem(texturePath: String): Item? {
        // Find default item by texture path
        val defaultItem = Item.values().find { it.texturePath == texturePath } ?: return null
        
        try {
            val currentArea = LocalAPI.getCurrentCharacterArea()
            
            // Build list of contextual items based on current area
            val contextItems = mutableListOf<Item>()
            when (currentArea) {
                "Dawn of Creation" -> {
                    // True Ophan area
                    BossData.TRUE_OPHAN.items.forEach { contextItems.add(it) }
                }
                "Seraph's Domain" -> {
                    // True Seraph area
                    BossData.TRUE_SERAPH.items.forEach { contextItems.add(it) }
                }
                "Celestial's Province" -> {
                    // Asmodeus + Seraphim
                    BossData.ASMODEUS.items.forEach { contextItems.add(it) }
                    BossData.SERAPHIM.items.forEach { contextItems.add(it) }
                }
                "Rustborn Kingdom" -> {
                    // Valerion + Nebula + Ophanim
                    BossData.VALERION.items.forEach { contextItems.add(it) }
                    BossData.NEBULA.items.forEach { contextItems.add(it) }
                    BossData.OPHANIM.items.forEach { contextItems.add(it) }
                }
            }
            
            // Check if any contextual item matches the texture path
            for (item in contextItems) {
                if (item.texturePath == defaultItem.texturePath) {
                    return item
                }
            }
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to resolve contextual item: ${e.message}")
        }
        
        return defaultItem
    }
    
    /**
     * Resolve contextual item from display name.
     * Handles hardmode variants (True Ophan, True Seraph, etc.) when detecting from chat messages.
     */
    private fun resolveContextualItemByDisplayName(defaultItem: Item): Item {
        try {
            val currentArea = LocalAPI.getCurrentCharacterArea()
            
            // Build list of contextual items based on current area
            val contextItems = mutableListOf<Item>()
            when (currentArea) {
                "Dawn of Creation" -> {
                    // True Ophan area
                    BossData.TRUE_OPHAN.items.forEach { contextItems.add(it) }
                }
                "Seraph's Domain" -> {
                    // True Seraph area
                    BossData.TRUE_SERAPH.items.forEach { contextItems.add(it) }
                }
                "Celestial's Province" -> {
                    // Asmodeus + Seraphim
                    BossData.ASMODEUS.items.forEach { contextItems.add(it) }
                    BossData.SERAPHIM.items.forEach { contextItems.add(it) }
                }
                "Rustborn Kingdom" -> {
                    // Valerion + Nebula + Ophanim
                    BossData.VALERION.items.forEach { contextItems.add(it) }
                    BossData.NEBULA.items.forEach { contextItems.add(it) }
                    BossData.OPHANIM.items.forEach { contextItems.add(it) }
                }
            }
            
            // Check if any contextual item matches the display name
            for (item in contextItems) {
                if (item.displayName == defaultItem.displayName) {
                    return item
                }
            }
        } catch (e: Exception) {
            Melinoe.logger.warn("Failed to resolve contextual item by display name: ${e.message}")
        }
        
        return defaultItem
    }
    
    /**
     * Send pity reset message for a specific item.
     */
    private fun sendPityResetMessage(item: Item, pityCount: Int) {
        val mc = Melinoe.mc
        if (mc.player == null) return
        
        val lootboost = TabListUtils.getLootboostPercentage() ?: 0
        val bossName = BossData.findByKey(currentBoss)?.label ?: currentBoss
        
        // Get rarity prefix and colors
        val (prefix, indicatorColor, indicatorSymbol) = when (item.rarity) {
            Item.Rarity.BLOODSHOT -> Triple("BLOODSHOT", 0x9D0000, "𕌜")
            Item.Rarity.UNHOLY -> Triple("UNHOLY", 0x5D6069, "𕑦")
            Item.Rarity.VOIDBOUND -> Triple("NIHILITY", 0x56167C, "𖈵")
            Item.Rarity.ROYAL -> Triple("ROYAL", 0x7d1775, "𕑩")
            Item.Rarity.COMPANION -> Triple("COMPANION", 0xae9000, "𕑰")
            Item.Rarity.RUNE -> Triple("RUNE", 0x555555, "𕑱")
            else -> Triple(item.rarity.name, 0xFFFFFF, "")
        }
        
        // Build gradient prefix
        val gradientPrefix = buildGradientPrefix(prefix)
        
        // Build the message
        val messageBuilder = net.minecraft.network.chat.Component.literal("")
            .append(net.minecraft.network.chat.Component.literal("$indicatorSymbol ").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFFFF))
            })
            .append(gradientPrefix)
            .append(net.minecraft.network.chat.Component.literal(" - ").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
            })
            .append(net.minecraft.network.chat.Component.literal("Dropped ").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
            })
            .append(net.minecraft.network.chat.Component.literal(item.displayName).withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(indicatorColor)).withUnderlined(true)
            })
            .append(net.minecraft.network.chat.Component.literal(" at ").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
            })
            .append(net.minecraft.network.chat.Component.literal("$pityCount").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF00))
            })
            .append(net.minecraft.network.chat.Component.literal(" pity").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
            })
        
        // Only show loot boost if > 0
        if (lootboost > 0) {
            messageBuilder.append(net.minecraft.network.chat.Component.literal(" [+$lootboost% Loot Boost]").withStyle { style ->
                style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF00))
            })
        }
        
        // Create chat indicator
        val chatIndicator = net.minecraft.client.GuiMessageTag(
            indicatorColor,
            null,
            net.minecraft.network.chat.Component.literal("$indicatorSymbol $prefix Drop"),
            "$prefix Drop"
        )
        
        mc.gui?.chat?.addMessage(messageBuilder, null, chatIndicator)
        val logMessage = if (lootboost > 0) {
            "Sent pity reset message: [$prefix] Dropped ${item.displayName} at $pityCount pity [+$lootboost% Loot Boost]"
        } else {
            "Sent pity reset message: [$prefix] Dropped ${item.displayName} at $pityCount pity"
        }
        Melinoe.logger.info(logMessage)
    }
    
    /**
     * Build gradient prefix for rarity.
     */
    private fun buildGradientPrefix(prefix: String): net.minecraft.network.chat.Component {
        return when (prefix) {
            "BLOODSHOT" -> {
                net.minecraft.network.chat.Component.literal("")
                    .append(net.minecraft.network.chat.Component.literal("B").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x9D0000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xB10000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xC40000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xD00000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("D").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDC0000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("S").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xE80000)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xF00909)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xF71111)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("T").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF1A1A)).withBold(true) })
            }
            "UNHOLY" -> {
                net.minecraft.network.chat.Component.literal("")
                    .append(net.minecraft.network.chat.Component.literal("U").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x5D6069)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("N").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x6F7680)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x868F91)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("O").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x9CA8A2)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xBCC8BC)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("Y").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDCE8D5)).withBold(true) })
            }
            "NIHILITY" -> {
                net.minecraft.network.chat.Component.literal("")
                    .append(net.minecraft.network.chat.Component.literal("N").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x56167C)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x6B2C8B)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("H").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x804299)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x985AAB)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("L").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xB072BC)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("I").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xBF81C7)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("T").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xCE91D2)).withBold(true) })
                    .append(net.minecraft.network.chat.Component.literal("Y").withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xDDA0DD)).withBold(true) })
            }
            else -> net.minecraft.network.chat.Component.literal(prefix).withStyle { it.withBold(true) }
        }
    }
}
