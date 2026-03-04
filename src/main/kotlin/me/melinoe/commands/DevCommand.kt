package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.features.impl.visual.dungeontimer.PityCounterConfig
import me.melinoe.features.impl.visual.dungeontimer.TimerModule
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
import kotlin.random.Random

val devCommand = Commodore("melinoedev", "mdev") {

    // Check if dev mode is enabled before executing any command
    runs {
        if (!ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }
        Message.dev("<gray>Use <gold>/mdev help <gray>for available commands")
    }

    literal("help").runs {
        if (!ClickGUIModule.devMode) {
            Message.error("Dev mode is disabled. Enable it in ClickGUI settings.")
            return@runs
        }

        Message.dev("""
            <gray>Dev Command Help:
            <dark_gray><bold>›</bold> <gold>/mdev itemid <dark_gray>- <gray>Shows item ID and model data of held item
            <dark_gray><bold>›</bold> <gold>/mdev simulate \<dungeon> <dark_gray>- <gray>Simulates a dungeon completion message
            <dark_gray>  Available dungeons: <gray>${DungeonData.entries.joinToString("<dark_gray>, <gray>") { it.name.lowercase() }}
            <dark_gray><bold>›</bold> <gold>/mdev testbag \<type> <dark_gray>- <gray>Simulates a bag drop and increments stats
            <dark_gray>  Available types: <gray>bloodshot, unholy, voidbound, royal, companion, event
            <dark_gray><bold>›</bold> <gold>/mdev testboss \<boss> <dark_gray>- <gray>Simulates a boss defeat and increments counters
            <dark_gray>  Examples: <gray>raphael, trueseraph, voidedomnipotent
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

            // Create the appropriate item stack with the correct model path for the totem animation
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
                        Message.dev("<dark_red>Bloodshot</dark_red> <gray>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.BloodshotBags) ?: 0
                    }
                    "unholy" -> {
                        Message.dev("<white>Unholy <gray>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.UnholyBags) ?: 0
                    }
                    "voidbound" -> {
                        Message.dev("<light_purple>Voidbound <gray>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.VoidboundBags) ?: 0
                    }
                    "royal" -> {
                        Message.dev("<gold>Royal <gray>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.RoyalBags) ?: 0
                    }
                    "companion" -> {
                        Message.dev("<yellow>Companion <gray>bag animation triggered!")
                        TypeSafeDataAccess.get(TrackingKey.LifetimeStat.CompanionBags) ?: 0
                    }
                    "event" -> {
                        Message.dev("<dark_purple>Event <gray>bag animation triggered!")
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
                    "sylvaris", "voidedomnipotent", "kurvaros", "shadowflare",
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

            // Map common names to proper boss names
            val properBossName = when (bossName.lowercase().replace(" ", "")) {
                "trueseraph" -> "True Seraph"
                "trueophan" -> "True Ophan"
                "voidedomnipotent" -> "Voided Omnipotent"
                else -> bossName.replaceFirstChar { it.uppercase() }
            }

            // Simulate boss defeat
            BagTracker.onBossDefeat(properBossName)

            val totalRuns = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.TotalRuns) ?: 0
            Message.dev("<gray>Simulated boss defeat: <light_purple>$properBossName")
            Message.dev("  <dark_gray>› <gray>Total runs: <aqua>$totalRuns")

            // Show pity counters for all items this boss can drop
            val boss = BossData.findByKey(properBossName)
            if (boss != null && boss.items.isNotEmpty()) {
                Message.dev("  <dark_gray>› <gray>Pity counters incremented for ${boss.items.size} items:")
                boss.items.take(3).forEach { item ->
                    val pityCount = TypeSafeDataAccess.get(TrackingKey.PityCounter(item.name)) ?: 0
                    Message.dev("    <dark_gray>- <gray>${item.displayName}: <red>$pityCount")
                }
                if (boss.items.size > 3) {
                    Message.dev("    <dark_gray>... and ${boss.items.size - 3} more")
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

        // Build the message dynamically with MiniMessage tags
        val message = buildString {
            append("<gray>Item ID Information\n")
            append("<dark_gray><bold>›</bold> <reset><gold>Display Name: <white>$displayName\n")
            append("<dark_gray><bold>›</bold> <reset><gold>Base ID: <white>$itemId\n")

            // Show Unicode character info
            if (!unicodeChar.isNullOrEmpty()) {
                append("<dark_gray><bold>›</bold> <reset><gold>Unicode Char: <white>$unicodeChar\n")

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
                append("<dark_gray><bold>›</bold> <reset><gold>Unicode Escape: <white>$escapeSequence\n")
            }

            // Show parsed range from lore
            if (parsedRange > 0) {
                append("<dark_gray><bold>›</bold> <reset><gold>Lore Range: <green>${parsedRange}f\n")
            }

            // Show ItemType match status
            if (itemType != null) {
                append("<dark_gray><bold>›</bold> <reset><gold>ItemType: <green>${itemType.name}\n")
                val (range, offset) = ItemUtils.getItemRangeWithOffset(heldItem)
                append("<dark_gray><bold>›</bold> <reset><gold>Range: <green>${range}f <gray>(offset: <green>${offset}f<gray>)\n")
            } else {
                append("<dark_gray><bold>›</bold> <reset><gold>ItemType: <gray>Not found\n")
            }

            // Show custom model info
            if (customModel != null) {
                append("<dark_gray><bold>›</bold> <reset><gold>Custom Model: <white>$customModel\n")
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
                append("\n<gray>$enumName <dark_gray>-> <gray>\"$escapeSequence\"")
            } else if (itemType != null) {
                append("\n<green>✔ Item matched with utils")
            }
        }

        Message.dev(message.toString())
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
            // Find the dungeon by name (case-insensitive)
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
    val statusStr = if (isNewPB) "<green><bold>NEW PB!</bold>" else "<red><bold>Not PB</bold>"

    Message.dev("<gray>Simulated <gold>${dungeon.areaName}<gray> completion: <aqua>$timeStr <dark_gray>(PB: $pbStr) <reset>$statusStr")
}

/**
 * Simulates a split dungeon completion (Rustborn Kingdom or Celestial's Province)
 */
private fun simulateSplitDungeon(dungeon: DungeonData, player: LocalPlayer) {
    // Get the specific bosses for this split dungeon
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

        val newPbString = if (isNewPB) "<green><bold>NEW PB!</bold><reset>" else "<red><bold>Not PB</bold><reset>"
        bossResults.add("${boss.label}: ${PersonalBestManager.formatTimeWithDecimals(splitTime)} $newPbString")

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
    Message.dev("<gray>Simulated <gold>${dungeon.areaName}<gray> split completion: <aqua>$totalTimeStr <gray>total")
    bossResults.forEach { result ->
        Message.dev("  <dark_gray>› <gray>$result")
    }
}

/**
 * Builds the pity counter line for specific dungeons (NOT centered - centering handled by Message.centeredRaw)
 */
private fun buildPityCounterLine(dungeon: DungeonData, boss: BossData): String {
    return PityCounterConfig.buildPityLine(dungeon, boss)
}