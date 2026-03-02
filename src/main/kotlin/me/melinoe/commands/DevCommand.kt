package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import me.melinoe.features.impl.visual.dungeontimer.TimerModule
import me.melinoe.features.impl.visual.dungeontimer.TimerState
import me.melinoe.features.impl.visual.dungeontimer.PityCounterConfig
import me.melinoe.utils.*
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.PersonalBestManager
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey
import net.minecraft.network.chat.Component
import kotlin.random.Random

val devCommand = Commodore("melinoedev", "mdev") {
    
    // Check if dev mode is enabled before executing any command
    runs {
        if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        val helpMessage = "§7Use §6/mdev help §7for available commands"
        Message.dev(helpMessage)
    }
    
    literal("help").runs {
        if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        val helpMessage = buildString {
            append("§7Dev Command Help:\n")
            append("§8§l› §6/mdev itemid §8- §7Shows item ID and model data of held item\n")
            append("§8§l› §6/mdev simulate <dungeon> §8- §7Simulates a dungeon completion message\n")
            append("§8  Available dungeons: §7")
            append(DungeonData.entries.joinToString("§8, §7") { it.name.lowercase() })
            append("\n§8§l› §6/mdev testbag <type> §8- §7Simulates a bag drop and increments stats\n")
            append("§8  Available types: §7bloodshot, unholy, voidbound, royal, companion, event\n")
            append("§8§l› §6/mdev testboss <boss> §8- §7Simulates a boss defeat and increments counters\n")
            append("§8  Examples: §7raphael, trueseraph, voidedomnipotent")
        }
        
        Message.dev(helpMessage)
    }
    
    literal("testbag").executable {
        param("type") {
            suggests { listOf("bloodshot", "unholy", "voidbound", "royal", "companion", "event") }
        }
        
        runs { bagType: String ->
            if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            val player = me.melinoe.Melinoe.mc.player
            if (player == null) {
                Message.error("Player not found")
                return@runs
            }
            
            // Create the appropriate item stack with the correct model path for the totem animation
            val itemStack = when (bagType.lowercase()) {
                "bloodshot" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/bloodshot_totem")
                    )
                    stack
                }
                "unholy" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/unholy_totem")
                    )
                    stack
                }
                "voidbound" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/voidbound_totem")
                    )
                    stack
                }
                "royal" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/royal_totem")
                    )
                    stack
                }
                "companion" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/companion")
                    )
                    stack
                }
                "event" -> {
                    val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)
                    stack.set(
                        net.minecraft.core.component.DataComponents.ITEM_MODEL,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/halloween_totem")
                    )
                    stack
                }
                else -> null
            }
            
            // Trigger the totem animation if we have an item
            // The GameRendererMixin will detect the animation and call the appropriate handler
            if (itemStack != null) {
                me.melinoe.Melinoe.mc.gameRenderer.displayItemActivation(itemStack)
                me.melinoe.Melinoe.mc.particleEngine.createTrackingEmitter(player, net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, 30)
                
                // Show confirmation message
                val count = when (bagType.lowercase()) {
                    "bloodshot" -> {
                        // Wait a tick for the handler to process
                        Message.dev("§4Bloodshot §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.BloodshotBags) ?: 0
                    }
                    "unholy" -> {
                        Message.dev("§fUnholy §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.UnholyBags) ?: 0
                    }
                    "voidbound" -> {
                        Message.dev("§dVoidbound §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.VoidboundBags) ?: 0
                    }
                    "royal" -> {
                        Message.dev("§6Royal §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.RoyalBags) ?: 0
                    }
                    "companion" -> {
                        Message.dev("§eCompanion §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.CompanionBags) ?: 0
                    }
                    "event" -> {
                        Message.dev("§5Event §7bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.EventBags) ?: 0
                    }
                    else -> 0
                }
            } else {
                Message.error("Unknown bag type: $bagType")
                Message.error("Available types: bloodshot, unholy, voidbound, royal, companion, event")
            }
        }
    }
    
    literal("testboss").executable {
        param("boss") {
            suggests { 
                listOf("raphael", "seraphim", "ophanim", "trueseraph", "trueophan", 
                       "sylvaris", "voidedomnipotent", "kurvaros", "shadowflare", 
                       "valerion", "nebula", "prismara", "omnipotent", "silex",
                       "chronos", "warden", "herald", "reaper", "defender", "asmodeus")
            }
        }
        
        runs { bossName: String ->
            if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            val player = me.melinoe.Melinoe.mc.player
            if (player == null) {
                Message.error("Player not found")
                return@runs
            }
            
            // Map common names to proper boss names
            val properBossName = when (bossName.lowercase().replace(" ", "")) {
                "trueseraph" -> "True Seraph"
                "trueophan" -> "True Ophan"
                "voidedomnipotent" -> "Voided Omnipotent"
                else -> bossName.replaceFirstChar { it.uppercase() }
            }
            
            // Simulate boss defeat
            me.melinoe.utils.data.BagTracker.onBossDefeat(properBossName)
            
            val totalRuns = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.TotalRuns) ?: 0
            Message.dev("§7Simulated boss defeat: §d$properBossName")
            Message.dev("  §8› §7Total runs: §b$totalRuns")
            
            // Show pity counters for all items this boss can drop
            val boss = me.melinoe.utils.data.BossData.findByKey(properBossName)
            if (boss != null && boss.items.isNotEmpty()) {
                Message.dev("  §8› §7Pity counters incremented for ${boss.items.size} items:")
                boss.items.take(3).forEach { item ->
                    val pityCount = TypeSafeDataAccess.get(TrackingKey.PityCounter(item.name)) ?: 0
                    Message.dev("    §8- §7${item.displayName}: §c$pityCount")
                }
                if (boss.items.size > 3) {
                    Message.dev("    §8... and ${boss.items.size - 3} more")
                }
            }
        }
    }
    

    
    literal("itemid").runs {
        if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        val player = me.melinoe.Melinoe.mc.player
        if (player == null) {
            Message.error("Player not found")
            return@runs
        }
        
        val heldItem = player.mainHandItem
        if (heldItem.isEmpty) {
            Message.error("You must be holding an item!")
            return@runs
        }
        
        // Get the item's base ID
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(heldItem.item).toString()
        
        // Get custom model data if present
        val customModel = heldItem.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)
        
        // Get item name (with formatting)
        val itemName = heldItem.hoverName.string
        
        // Get plain name (no formatting, but keeps Unicode)
        val plainName = me.melinoe.utils.ItemUtils.getPlainName(heldItem)
        
        // Get display name without Unicode characters
        val displayName = me.melinoe.utils.ItemUtils.getDisplayName(heldItem)
        
        // Extract Unicode character from plain name
        val unicodeChar = if (plainName.length >= 2) {
            plainName.substring(1, plainName.length - 1)
        } else {
            null
        }
        
        // Check if this matches an ItemType
        val itemType = me.melinoe.utils.ItemUtils.ItemType.fromItemStack(heldItem)
        
        // Parse range from lore if available
        val parsedRange = me.melinoe.utils.ItemUtils.parseItemRange(heldItem)
        
        // Build the message
        val message = buildString {
            append("§7Item ID Information\n")
            append("§8§l› §r§6Display Name: §f$displayName\n")
            append("§8§l› §r§6Base ID: §f$itemId\n")
            
            // Show Unicode character info
            if (unicodeChar != null && unicodeChar.isNotEmpty()) {
                append("§8§l› §r§6Unicode Char: §f$unicodeChar\n")
                
                // Show Unicode escape sequence (properly handle surrogate pairs)
                val codePoints = unicodeChar.codePoints().toArray()
                val escapeSequence = if (codePoints.size == 1 && codePoints[0] > 0xFFFF) {
                    // Surrogate pair - convert to two \uXXXX sequences
                    val codePoint = codePoints[0]
                    val high = ((codePoint - 0x10000) shr 10) + 0xD800
                    val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
                    "\\u${String.format("%04X", high)}\\u${String.format("%04X", low)}"
                } else {
                    // Regular character or already surrogate pairs
                    unicodeChar.toCharArray().joinToString("") { 
                        "\\u${String.format("%04X", it.code)}" 
                    }
                }
                append("§8§l› §r§6Unicode Escape: §f$escapeSequence\n")
            }
            
            // Show parsed range from lore
            if (parsedRange > 0) {
                append("§8§l› §r§6Lore Range: §a${parsedRange}f\n")
            }
            
            // Show ItemType match status
            if (itemType != null) {
                append("§8§l› §r§6ItemType: §a${itemType.name}\n")
                val (range, offset) = me.melinoe.utils.ItemUtils.getItemRangeWithOffset(heldItem)
                append("§8§l› §r§6Range: §a${range}f §7(offset: §a${offset}f§7)\n")
            } else {
                append("§8§l› §r§6ItemType: §7Not found\n")
            }
            
            // Show custom model info
            if (customModel != null) {
                append("§8§l› §r§6Custom Model: §f$customModel\n")
            }
            
            // Generate code snippets for ItemUtils if not already added
            if (itemType == null && unicodeChar != null && unicodeChar.isNotEmpty()) {
                // Generate enum name suggestion
                val modelPath = customModel?.toString() ?: ""
                val enumName = if (modelPath.startsWith("telos:")) {
                    val shortPath = modelPath.removePrefix("telos:")
                    val parts = shortPath.split("/")
                    if (parts.size >= 2) {
                        val prefix = if (parts.last().startsWith("ut-")) "UT" else if (parts.last().startsWith("ex-")) "EX" else ""
                        val baseName = parts.last()
                            .removePrefix("ut-")
                            .removePrefix("ex-")
                            .uppercase()
                            .replace("-", "_")
                        if (prefix.isNotEmpty()) "${prefix}_${baseName}" else baseName
                    } else {
                        "NEW_ITEM"
                    }
                } else {
                    "NEW_ITEM"
                }
                
                // Show simplified message with enum name and unicode
                val codePoints = unicodeChar.codePoints().toArray()
                val escapeSequence = if (codePoints.size == 1 && codePoints[0] > 0xFFFF) {
                    val codePoint = codePoints[0]
                    val high = ((codePoint - 0x10000) shr 10) + 0xD800
                    val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
                    "\\u${String.format("%04X", high)}\\u${String.format("%04X", low)}"
                } else {
                    unicodeChar.toCharArray().joinToString("") { 
                        "\\u${String.format("%04X", it.code)}" 
                    }
                }
                append("\n§7$enumName §8-> §7\"$escapeSequence\"")
            } else if (itemType != null) {
                append("\n§a✔ Item matched with utils")
            }
        }
        
        Message.dev(message)
    }
    
    literal("simulate").executable {
        param("dungeon") {
            suggests { DungeonData.entries.map { it.name.lowercase() } }
        }
        
        runs { dungeonName: String ->
            if (!me.melinoe.features.impl.ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            // Find the dungeon by name (case-insensitive)
            val dungeon = DungeonData.entries.find { 
                it.name.equals(dungeonName, ignoreCase = true) 
            }
            
            if (dungeon == null) {
                Message.error("Unknown dungeon: $dungeonName")
                return@runs
            }
            
            val player = me.melinoe.Melinoe.mc.player
            if (player == null) {
                Message.error("Player not found")
                return@runs
            }
            
            // Check if this is a split dungeon (Rustborn Kingdom or Celestial's Province)
            val isSplitDungeon = dungeon.equalsOneOf(DungeonData.RUSTBORN_KINGDOM, DungeonData.CELESTIALS_PROVINCE)
            
            if (isSplitDungeon) {
                // Simulate split dungeon with multiple bosses
                simulateSplitDungeon(dungeon, player)
            } else {
                // Simulate regular dungeon
                simulateRegularDungeon(dungeon, player)
            }
        }
    }
    
    literal("copy").runs { greedyString: com.github.stivais.commodore.utils.GreedyString ->
        setClipboardContent(greedyString.string)
        Message.success("Copied to clipboard!")
    }

}

/**
 * Simulates a regular dungeon completion
 */
private fun simulateRegularDungeon(dungeon: DungeonData, player: net.minecraft.client.player.LocalPlayer) {
    // Generate random time between 1-10 minutes
    val randomTime = Random.nextFloat() * 540f + 60f // 60-600 seconds (1-10 minutes)
    
    // Get current PB (if exists)
    val currentPB = PersonalBestManager.getDungeonPersonalBest(dungeon)
    
    // Randomly decide if this is a new PB (50% chance if PB exists, always true if no PB)
    val isNewPB = if (currentPB == -1f) {
        true
    } else {
        Random.nextBoolean()
    }
    
    // Adjust time based on whether it's a new PB
    val simulatedTime = if (isNewPB && currentPB != -1f) {
        // New PB should be faster than current PB
        currentPB - (Random.nextFloat() * 30f + 1f) // 1-31 seconds faster
    } else if (!isNewPB && currentPB != -1f) {
        // Not a PB should be slower than current PB
        currentPB + (Random.nextFloat() * 60f + 1f) // 1-61 seconds slower
    } else {
        randomTime
    }.coerceAtLeast(1f) // Ensure time is at least 1 second
    
    // Show dungeon name with gradient header (centered, raw, no watermark)
    TimerModule.showSimulatedHeader(dungeon)
    
    // Show pity counter if applicable (centered)
    val pityLine = buildPityCounterLine(dungeon, dungeon.finalBoss)
    if (pityLine.isNotEmpty()) {
        Message.centeredRaw(pityLine)
    }
    
    // Build and display the completion message (centered, raw, no watermark)
    TimerModule.showSimulatedCompletionMessage(
        dungeon, 
        simulatedTime, 
        currentPB, 
        isNewPB
    )
    
    // Add separator line after completion message
    Message.separator()
    
    // Send dev confirmation
    val timeStr = PersonalBestManager.formatTimeWithDecimals(simulatedTime)
    val pbStr = if (currentPB == -1f) "None" else PersonalBestManager.formatTimeWithDecimals(currentPB)
    val statusStr = if (isNewPB) "§a§lNEW PB!" else "§c§lNot PB"
    
    Message.dev("§7Simulated §6${dungeon.areaName}§7 completion: §b$timeStr §8(PB: $pbStr) $statusStr")
}

/**
 * Simulates a split dungeon completion (Rustborn Kingdom or Celestial's Province)
 */
private fun simulateSplitDungeon(dungeon: DungeonData, player: net.minecraft.client.player.LocalPlayer) {
    // Get the specific bosses for this split dungeon
    val bosses = when (dungeon) {
        DungeonData.RUSTBORN_KINGDOM -> {
            // Rustborn Kingdom: Valerion, Nebula, Ophanim (final)
            listOf(
                me.melinoe.utils.data.BossData.VALERION,
                me.melinoe.utils.data.BossData.NEBULA,
                me.melinoe.utils.data.BossData.OPHANIM
            )
        }
        DungeonData.CELESTIALS_PROVINCE -> {
            // Celestial's Province: Asmodeus, Seraphim (final)
            listOf(
                me.melinoe.utils.data.BossData.ASMODEUS,
                me.melinoe.utils.data.BossData.SERAPHIM
            )
        }
        else -> {
            Message.error("Not a split dungeon: ${dungeon.areaName}")
            return
        }
    }
    
    var totalTime = 0f
    val bossResults = mutableListOf<String>()
    val bossDefeats = mutableListOf<TimerState.BossDefeat>()
    
    // Simulate each boss defeat
    for ((index, boss) in bosses.withIndex()) {
        // Generate random split time (30-180 seconds per boss)
        val splitTime = Random.nextFloat() * 150f + 30f
        totalTime += splitTime
        
        // Get current PB for this boss
        val currentPB = PersonalBestManager.getBossPersonalBest(boss)
        
        // Randomly decide if this is a new PB
        val isNewPB = if (currentPB == -1f) {
            Random.nextBoolean() // 50% chance even for first time
        } else {
            Random.nextBoolean()
        }
        
        bossDefeats.add(TimerState.BossDefeat(boss, splitTime, isNewPB, currentPB))
        bossResults.add("${boss.label}: ${PersonalBestManager.formatTimeWithDecimals(splitTime)} ${if (isNewPB) "§a§lNEW PB!" else "§c§lNot PB"}")
        
        // For intermediate bosses (not the final boss), show the mini split message
        if (index < bosses.size - 1) {
            // Show dungeon name with gradient header (centered, raw, no watermark)
            TimerModule.showSimulatedHeader(dungeon)
            
            // Add pity counter line for this specific boss (centered)
            val pityLine = buildPityCounterLine(dungeon, boss)
            if (pityLine.isNotEmpty()) {
                Message.centeredRaw(pityLine)
            }
            
            // Show boss split message
            TimerModule.showSimulatedBossSplitMessage(dungeon, boss, splitTime, currentPB, isNewPB)
            
            // Add divider after boss split message
            Message.separator()
        }
    }
    
    // Now show the final completion summary with all bosses
    // Show dungeon name with gradient header (centered, raw, no watermark)
    TimerModule.showSimulatedHeader(dungeon)
    
    // Show pity counter only for the final boss (last defeat in the list)
    if (bossDefeats.isNotEmpty()) {
        val finalBoss = bossDefeats.last().boss
        val pityLine = buildPityCounterLine(dungeon, finalBoss)
        if (pityLine.isNotEmpty()) {
            Message.centeredRaw(pityLine)
        }
    }
    
    // Show all boss defeats in the final summary
    for (defeat in bossDefeats) {
        TimerModule.showSimulatedBossSplitSummaryMessage(defeat, dungeon)
    }
    
    // Add separator line after all split messages
    Message.separator()
    
    // Send dev confirmation
    val totalTimeStr = PersonalBestManager.formatTimeWithDecimals(totalTime)
    Message.dev("§7Simulated §6${dungeon.areaName}§7 split completion: §b$totalTimeStr §7total")
    bossResults.forEach { result ->
        Message.dev("  §8› §7$result")
    }
}

/**
 * Builds the pity counter line for specific dungeons (NOT centered - centering handled by Message.centeredRaw)
 */
private fun buildPityCounterLine(dungeon: DungeonData, boss: me.melinoe.utils.data.BossData): String {
    return PityCounterConfig.buildPityLine(dungeon, boss)
}
