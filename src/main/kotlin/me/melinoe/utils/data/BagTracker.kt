package me.melinoe.utils.data

import me.melinoe.Melinoe
import me.melinoe.features.impl.tracking.PityCounterModule
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.TabListUtils
import me.melinoe.utils.data.persistence.TrackingKey
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.toNative
import net.minecraft.client.GuiMessageTag
import net.minecraft.world.entity.item.ItemEntity

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
                if (PityCounterModule.useCustomMsg) {
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

        // Extract player name and check against current player
        // We do this check to ensure we only track our own drops
        val playerName = plainText.substring(spaceBeforePlayer + 1, gotIndex)
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
        if (PityCounterModule.useCustomMsg) {
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

        // Configuration for rarity styles
        data class RarityStyle(val indicatorColor: Int, val prefix: String, val itemNameColor: String, val logName: String)

        val style = when (item.rarity) {
            Item.Rarity.IRRADIATED -> RarityStyle(
                0x189506,
                "<white>\uD814\uDF19 </white><bold><gradient:#189506:#15cd15>IRRADIATED</bold>",
                "<#189506>",
                "ROYAL"
            )
            Item.Rarity.GILDED -> RarityStyle(
                0xb93f12,
                "<white>\uD818\uDCF1 </white><bold><gradient:#b93f12:#df5320>GILDED</bold>",
                "<#b93f12>",
                "ROYAL"
            )
            Item.Rarity.ROYAL -> RarityStyle(
                0x7d1775,
                "<white>\uD814\uDF1B </white><bold><gradient:#7d1775:#aa00aa>ROYAL</bold>",
                "<#7d1775>",
                "ROYAL"
            )
            Item.Rarity.BLOODSHOT -> RarityStyle(
                0x9D0000,
                "<white>\uD814\uDF1C </white><bold><gradient:#9D0000:#FF1A1A>BLOODSHOT</gradient></bold>",
                "<#9D0000>",
                "BLOODSHOT"
            )
            Item.Rarity.VOIDBOUND -> RarityStyle(
                0x8d15f0,
                "<white>\uD818\uDE35 </white><bold><gradient:#8d15f0:#be74fb>VOIDBOUND</gradient></bold>",
                "<#8d15f0>",
                "NIHILITY"
            )
            Item.Rarity.UNHOLY -> RarityStyle(
                0x5D6069,
                "<white>\uD815\uDC66 </white><bold><gradient:#5D6069:#DCE8D5>UNHOLY</gradient></bold>",
                "<#5D6069>",
                "UNHOLY"
            )
            Item.Rarity.COMPANION -> RarityStyle(
                0xae9000,
                "<white>\uD814\uDF1A </white><bold><gradient:#ae9000:#ffaa00>COMPANION</bold>",
                "<#ae9000>",
                "COMPANION"
            )
            Item.Rarity.RUNE -> RarityStyle(
                0x555555,
                "<white>\uD815\uDC65 </white><bold><gradient:#555555:#616161>RUNE</bold>",
                "<#555555>",
                "RUNE"
            )
            else -> RarityStyle(
                0xFFFFFF,
                "<bold><white>${item.rarity.name}</bold>",
                "<white>",
                item.rarity.name
            )
        }

        val lootBoostStr = if (lootboost > 0) " <yellow>[+$lootboost% LB]" else ""
        val m = Message.Colors.MUTED

        // Build message using MiniMessage
        var message = "${style.prefix} $m- <gray>Dropped <underlined>${style.itemNameColor}${item.displayName}</underlined> <gray>at <yellow>$pityCount</yellow> <gray>from ${style.itemNameColor} <gray>pity$lootBoostStr"
        if (PityCounterModule.showAnnounceButton) {
            val shareText = "[${item.rarity}] Dropped ${item.displayName} at ${pityCount} pity!"

            message += " <click:suggest_command:'${shareText}'><hover:show_text:\"<gray>Click to share in chat!</gray>\"><gray><b>⧉</b></gray></hover></click>"
        }

        val chatIndicator = GuiMessageTag(
            style.indicatorColor,
            null,
            "${style.prefix} Drop".toNative(),
            "${style.logName} Drop"
        )

        mc.gui?.chat?.addMessage(message.toNative(), null, chatIndicator)

        val logMessage = "Sent pity reset message: Dropped ${item.displayName} at $pityCount pity${if (lootboost > 0) " [+$lootboost% Loot Boost]" else ""}"
        Melinoe.logger.info(logMessage)
    }
}