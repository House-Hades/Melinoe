package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.features.impl.visual.dungeontimer.GradientTextBuilder
import me.melinoe.features.impl.visual.dungeontimer.MessageFormatter
import me.melinoe.features.impl.visual.dungeontimer.PityCounterConfig
import me.melinoe.features.impl.visual.dungeontimer.TimerState
import me.melinoe.utils.*
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.BagTracker
import me.melinoe.utils.data.persistence.TrackingKey
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.chat.Component
import kotlin.random.Random

val devCommand = Commodore("melinoedev", "mdev") {

    // Check if dev mode is enabled before executing any command
    runs {
        if (!ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        Message.dev("<#AAAAAA>Use <#FFD700>/mdev help <#AAAAAA>for available commands")
    }
    
    literal("help").runs {
        if (!ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        
        Message.dev("""
            <#AAAAAA>Dev Command Help:
            <#555555><bold>›</bold> <#FFD700>/mdev itemid <#555555>- <#AAAAAA>Shows item ID and model data of held item
            <#555555><bold>›</bold> <#FFD700>/mdev simulate \<dungeon> <#555555>- <#AAAAAA>Simulates a dungeon completion message
            <#555555>  Available dungeons: <#AAAAAA>${DungeonData.entries.joinToString("<#555555>, <#AAAAAA>") { it.name.lowercase() }}
            <#555555><bold>›</bold> <#FFD700>/mdev testbag \<type> <#555555>- <#AAAAAA>Simulates a bag drop and increments stats
            <#555555>  Available types: <#AAAAAA>bloodshot, unholy, voidbound, royal, companion, event
            <#555555><bold>›</bold> <#FFD700>/mdev testboss \<boss> <#555555>- <#AAAAAA>Simulates a boss defeat and increments counters
            <#555555>  Examples: <#AAAAAA>raphael, trueseraph, voidedomnipotent
        """.trimIndent())
    }
    
    literal("testbag").executable {
        param("type") {
            suggests { listOf("bloodshot", "unholy", "voidbound", "royal", "companion", "event") }
        }
        
        runs { bagType: String ->
            if (!ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            val player = Melinoe.mc.player
            if (player == null) {
                Message.error("Player not found")
                return@runs
            }
            
            val itemStack = when (bagType.lowercase()) {
                "bloodshot" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/bloodshot_totem"))
                    stack
                }
                "unholy" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/unholy_totem"))
                    stack
                }
                "voidbound" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/voidbound_totem"))
                    stack
                }
                "royal" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/royal_totem"))
                    stack
                }
                "companion" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/companion"))
                    stack
                }
                "event" -> {
                    val stack = ItemStack(Items.STICK)
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("telos", "entity/pouch/halloween_totem"))
                    stack
                }
                else -> null
            }
            
            // Trigger the totem animation if we have an item
            // The GameRendererMixin will detect the animation and call the appropriate handler
            if (itemStack != null) {
                Melinoe.mc.gameRenderer.displayItemActivation(itemStack)
                Melinoe.mc.particleEngine.createTrackingEmitter(player, ParticleTypes.TOTEM_OF_UNDYING, 30)
                
                // Show confirmation message
                when (bagType.lowercase()) {
                    "bloodshot" -> {
                        Message.dev("<#AA0000>Bloodshot</#AA0000> <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.BloodshotBags) ?: 0
                    }
                    "unholy" -> {
                        Message.dev("<#FFFFFF>Unholy <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.UnholyBags) ?: 0
                    }
                    "voidbound" -> {
                        Message.dev("<#AA00FF>Voidbound <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.VoidboundBags) ?: 0
                    }
                    "royal" -> {
                        Message.dev("<#FFD700>Royal <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.RoyalBags) ?: 0
                    }
                    "companion" -> {
                        Message.dev("<#FFFF00>Companion <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.CompanionBags) ?: 0
                    }
                    "event" -> {
                        Message.dev("<#AA00AA>Event <#AAAAAA>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.EventBags) ?: 0
                    }
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
                    "sylvaris", "voidedomnipotent", "kurvaros", "solarflare",
                    "valerion", "nebula", "prismara", "omnipotent", "silex",
                    "chronos", "warden", "herald", "reaper", "defender", "asmodeus")
            }
        }
        
        runs { bossName: String ->
            if (!ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            val player = Melinoe.mc.player
            if (player == null) {
                Message.error("Player not found")
                return@runs
            }
            
            val properBossName = when (bossName.lowercase().replace(" ", "")) {
                "trueseraph" -> "True Seraph"
                "trueophan" -> "True Ophan"
                "voidedomnipotent" -> "Voided Omnipotent"
                else -> bossName.replaceFirstChar { it.uppercase() }
            }
            
            // Simulate boss defeat
            BagTracker.onBossDefeat(properBossName)
            
            val totalRuns = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.TotalRuns) ?: 0
            Message.dev("<#AAAAAA>Simulated boss defeat: <#AA00FF>$properBossName")
            Message.dev("  <#555555>› <#AAAAAA>Total runs: <#55FFFF>$totalRuns")
            
            // Show pity counters for all items this boss can drop
            val boss = BossData.findByKey(properBossName)
            if (boss != null && boss.items.isNotEmpty()) {
                Message.dev("  <#555555>› <#AAAAAA>Pity counters incremented for ${boss.items.size} items:")
                boss.items.take(3).forEach { item ->
                    val pityCount = TypeSafeDataAccess.get(TrackingKey.PityCounter(item.name)) ?: 0
                    Message.dev("    <#555555>- <#AAAAAA>${item.displayName}: <#FF3333>$pityCount")
                }
                if (boss.items.size > 3) {
                    Message.dev("    <#555555>... and ${boss.items.size - 3} more")
                }
            }
        }
    }
    
    literal("itemid").runs {
        if (!ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        val player = Melinoe.mc.player
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
        val itemId = BuiltInRegistries.ITEM.getKey(heldItem.item).toString()
        
        // Get custom model data if present
        val customModel = heldItem.get(DataComponents.ITEM_MODEL)
        
        // Get plain name (no formatting, but keeps Unicode)
        val plainName = ItemUtils.getPlainName(heldItem)
        
        // Get display name without Unicode characters
        val displayName = ItemUtils.getDisplayName(heldItem)
        
        // Extract Unicode character from plain name
        val unicodeChar = if (plainName.length >= 2) {
            plainName.substring(1, plainName.length - 1)
        } else {
            null
        }
        
        // Check if this matches an ItemType
        val itemType = ItemUtils.ItemType.fromItemStack(heldItem)
        
        // Parse range from lore if available
        val parsedRange = ItemUtils.parseItemRange(heldItem)
        
        // Build the message dynamically
        val message = buildString {
            append("<#AAAAAA>Item ID Information\n")
            append("<#555555><bold>›</bold> <reset><#FFD700>Display Name: <#FFFFFF>$displayName\n")
            append("<#555555><bold>›</bold> <reset><#FFD700>Base ID: <#FFFFFF>$itemId\n")
            
            // Show Unicode character info
            if (!unicodeChar.isNullOrEmpty()) {
                append("<#555555><bold>›</bold> <reset><#FFD700>Unicode Char: <#FFFFFF>$unicodeChar\n")
                
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
                append("<#555555><bold>›</bold> <reset><#FFD700>Unicode Escape: <#FFFFFF>$escapeSequence\n")
            }
            
            // Show parsed range from lore
            if (parsedRange > 0) {
                append("<#555555><bold>›</bold> <reset><#FFD700>Lore Range: <#00FF00>${parsedRange}f\n")
            }
            
            // Show ItemType match status
            if (itemType != null) {
                append("<#555555><bold>›</bold> <reset><#FFD700>ItemType: <#00FF00>${itemType.name}\n")
                val (range, offset) = ItemUtils.getItemRangeWithOffset(heldItem)
                append("<#555555><bold>›</bold> <reset><#FFD700>Range: <#00FF00>${range}f <#AAAAAA>(offset: <#00FF00>${offset}f<#AAAAAA>)\n")
            } else {
                append("<#555555><bold>›</bold> <reset><#FFD700>ItemType: <#AAAAAA>Not found\n")
            }
            
            // Show custom model info
            if (customModel != null) {
                append("<#555555><bold>›</bold> <reset><#FFD700>Custom Model: <#FFFFFF>$customModel\n")
            }
            
            // Generate code snippets for ItemUtils if not already added
            if (itemType == null && !unicodeChar.isNullOrEmpty()) {
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
                append("\n<#AAAAAA>$enumName <#555555>-> <#AAAAAA>\"$escapeSequence\"")
            } else if (itemType != null) {
                append("\n<#00FF00>✔ Item matched with utils")
            }
        }
        
        Message.dev(message)
    }
    
    literal("simulate").executable {
        param("dungeon") {
            suggests { DungeonData.entries.map { it.name.lowercase() } }
        }
        
        runs { dungeonName: String ->
            if (!ClickGUIModule.devMode) {
                Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
                return@runs
            }
            
            val dungeon = DungeonData.entries.find {
                it.name.equals(dungeonName, ignoreCase = true)
            }
            
            if (dungeon == null) {
                Message.error("Unknown dungeon: $dungeonName")
                return@runs
            }
            
            val player = Melinoe.mc.player
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
    
    literal("copy").runs { greedyString: GreedyString ->
        setClipboardContent(greedyString.string)
        Message.success("Copied to clipboard!")
    }
    
}

/**
 * Helper to safely center a Component utilizing string lengths to bypass tag calculation bugs.
 */
private fun sendCenteredComponent(component: Component) {
    val plainText = component.string.noControlCodes
    val spaces = getCenteredText(plainText).takeWhile { it == ' ' }
    Melinoe.mc.execute {
        Melinoe.mc.gui?.chat?.addMessage(Component.literal(spaces).append(component))
    }
}

/**
 * Helper to safely center a MiniMessage string
 */
private fun sendCenteredMM(mmString: String) {
    val plainText = mmString.replace(Regex("<[^>]*>"), "").noControlCodes
    val spaces = getCenteredText(plainText).takeWhile { it == ' ' }
    Melinoe.mc.execute {
        Melinoe.mc.gui?.chat?.addMessage("$spaces$mmString".toNative())
    }
}

/**
 * Simulates a regular dungeon completion
 */
private fun simulateRegularDungeon(dungeon: DungeonData, player: LocalPlayer) {
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
    }.coerceAtLeast(1f)
    
    // Layout
    Message.separator()
    
    val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
    sendCenteredComponent(headerComponent)
    
    // Show pity counter if applicable (centered)
    val pityLine = buildPityCounterLine(dungeon, dungeon.finalBoss)
    if (pityLine.isNotEmpty()) {
        Message.centeredRaw(pityLine)
    }
    
    // Completion messag
    val compMsg = MessageFormatter.formatCompletionMessage(dungeon, simulatedTime, currentPB, isNewPB)
    sendCenteredComponent(compMsg)
    
    Message.separator()
    
    // Simulate Leaderboard damage stat
    sendCenteredMM("<#FFD700>𕑱 ${player.scoreboardName}<reset> <#555555>—</#555555> <#FF3333>100.0% (5420)<reset>")
    
    Message.separator()
    
    // Send dev confirmation
    val timeStr = PersonalBestManager.formatTimeWithDecimals(simulatedTime)
    val pbStr = if (currentPB == -1f) "None" else PersonalBestManager.formatTimeWithDecimals(currentPB)
    val statusStr = if (isNewPB) "<#00FF00><bold>NEW PB!</bold>" else "<#FF3333><bold>Not PB</bold>"
    
    Message.dev("<#AAAAAA>Simulated <#FFD700>${dungeon.areaName}<#AAAAAA> completion: <#55FFFF>$timeStr <#555555>(PB: $pbStr) <reset>$statusStr")
}

/**
 * Simulates a split dungeon completion (Rustborn Kingdom or Celestial's Province)
 */
private fun simulateSplitDungeon(dungeon: DungeonData, player: LocalPlayer) {
    val bosses = when (dungeon) {
        DungeonData.RUSTBORN_KINGDOM -> {
            // Rustborn Kingdom: Valerion, Nebula, Ophanim (final)
            listOf(BossData.VALERION, BossData.NEBULA, BossData.OPHANIM)
        }
        DungeonData.CELESTIALS_PROVINCE -> {
            // Celestial's Province: Asmodeus, Seraphim (final)
            listOf(BossData.ASMODEUS, BossData.SERAPHIM)
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
        
        val newPbString = if (isNewPB) "<#00FF00><bold>NEW PB!</bold><reset>" else "<#FF3333><bold>Not PB</bold><reset>"
        bossResults.add("${boss.label}: ${PersonalBestManager.formatTimeWithDecimals(splitTime)} $newPbString")
        
        // For intermediate bosses (not the final boss), show the mini split message
        if (index < bosses.size - 1) {
            Message.separator()
            
            val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
            sendCenteredComponent(headerComponent)
            
            // Add pity counter line for this specific boss (centered)
            val pityLine = buildPityCounterLine(dungeon, boss)
            if (pityLine.isNotEmpty()) {
                Message.centeredRaw(pityLine)
            }
            
            // Show boss split message
            val splitMsg = MessageFormatter.formatSplitMessage(dungeon, boss, splitTime, currentPB, isNewPB)
            sendCenteredComponent(splitMsg)
            
            Message.separator()
            sendCenteredMM("<#FFD700>𕑱 ${player.scoreboardName}<reset> <#555555>—</#555555> <#FF3333>100.0% (5420)<reset>")
            Message.separator()
        }
    }
    
    Message.separator()
    
    val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
    sendCenteredComponent(headerComponent)
    
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
        val sumMsg = MessageFormatter.formatSplitSummaryMessage(dungeon, defeat.boss, defeat.splitTime, defeat.oldPB, defeat.wasNewPB)
        sendCenteredComponent(sumMsg)
    }
    
    Message.separator()
    sendCenteredMM("<#FFD700>𕑱 ${player.scoreboardName}<reset> <#555555>—</#555555> <#FF3333>100.0% (5420)<reset>")
    Message.separator()
    
    // Send dev confirmation
    val totalTimeStr = PersonalBestManager.formatTimeWithDecimals(totalTime)
    Message.dev("<#AAAAAA>Simulated <#FFD700>${dungeon.areaName}<#AAAAAA> split completion: <#55FFFF>$totalTimeStr <#AAAAAA>total")
    bossResults.forEach { result ->
        Message.dev("  <#555555>› <#AAAAAA>$result")
    }
}

/**
 * Builds the pity counter line for specific dungeons
 */
private fun buildPityCounterLine(dungeon: DungeonData, boss: BossData): String {
    return PityCounterConfig.buildPityLine(dungeon, boss)
}