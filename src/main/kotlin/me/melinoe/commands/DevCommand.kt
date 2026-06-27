package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import me.melinoe.Melinoe
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.*
import me.melinoe.utils.data.BagTracker
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.persistence.TrackingKey
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries

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
            
            // Map bag types to TelosItemUtils identifiers
            val pouchIdentifier = when (bagType.lowercase()) {
                "bloodshot" -> TelosItemUtils.POUCH_BLOODSHOT
                "unholy" -> TelosItemUtils.POUCH_UNHOLY
                "voidbound" -> TelosItemUtils.POUCH_VOIDBOUND
                "royal" -> TelosItemUtils.POUCH_ROYAL
                "companion" -> TelosItemUtils.POUCH_COMPANION
                "halloween", "event" -> TelosItemUtils.POUCH_HALLOWEEN
                "valentine" -> TelosItemUtils.POUCH_VALENTINE
                "christmas" -> TelosItemUtils.POUCH_CHRISTMAS
                "shiny" -> TelosItemUtils.POUCH_SHINY
                else -> null
            }
            
            if (pouchIdentifier == null) {
                Message.error("Invalid bag type. Valid types: bloodshot, unholy, voidbound, royal, companion, halloween, valentine, christmas, shiny")
                return@runs
            }
            
            // Create the item stack using TelosItemUtils
            val itemStack = TelosItemUtils.createItemStack(pouchIdentifier)
            
            // Trigger the totem animation
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
                "halloween", "valentine", "christmas", "event" -> {
                    Message.dev("<#AA00AA>Event <#AAAAAA>bag animation triggered!")
                    TypeSafeDataAccess.get(TrackingKey.LifetimeStat.EventBags) ?: 0
                }
                "shiny" -> {
                    Message.dev("<#00FFFF>Shiny <#AAAAAA>bag animation triggered!")
                    TypeSafeDataAccess.get(TrackingKey.LifetimeStat.ShinyBags) ?: 0
                }
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
        val parsedRangeInfo = ItemUtils.parseItemRange(heldItem)
        
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
            if (parsedRangeInfo.baseRange > 0) {
                if (parsedRangeInfo.hasModifier) {
                    // Calculate the modifier value
                    val modifier = if (parsedRangeInfo.maxRange > parsedRangeInfo.baseRange) {
                        parsedRangeInfo.maxRange - parsedRangeInfo.baseRange
                    } else {
                        parsedRangeInfo.minRange - parsedRangeInfo.baseRange
                    }
                    val modifierSign = if (modifier >= 0) "+" else ""
                    append("<#555555><bold>›</bold> <reset><#FFD700>Lore Range: <#00FF00>${parsedRangeInfo.baseRange}f <#AAAAAA>($modifierSign<#00FF00>${modifier}f<#AAAAAA>) = <#00FF00>${if (modifier >= 0) parsedRangeInfo.maxRange else parsedRangeInfo.minRange}f\n")
                } else {
                    append("<#555555><bold>›</bold> <reset><#FFD700>Lore Range: <#00FF00>${parsedRangeInfo.baseRange}f\n")
                }
            }
            
            // Show ItemType match status
            if (itemType != null) {
                append("<#555555><bold>›</bold> <reset><#FFD700>ItemType: <#00FF00>${itemType.name}\n")
                val (rangeInfo, offset) = ItemUtils.getItemRangeWithOffset(heldItem)
                if (rangeInfo.hasModifier) {
                    // Calculate the modifier value
                    val modifier = if (rangeInfo.maxRange > rangeInfo.baseRange) {
                        rangeInfo.maxRange - rangeInfo.baseRange
                    } else {
                        rangeInfo.minRange - rangeInfo.baseRange
                    }
                    val modifierSign = if (modifier >= 0) "+" else ""
                    val effectiveRange = if (modifier >= 0) rangeInfo.maxRange else rangeInfo.minRange
                    append("<#555555><bold>›</bold> <reset><#FFD700>Range: <#00FF00>${rangeInfo.baseRange}f <#AAAAAA>($modifierSign<#00FF00>${modifier}f<#AAAAAA>) = <#00FF00>${effectiveRange}f <#AAAAAA>(offset: <#00FF00>${offset}f<#AAAAAA>)\n")
                } else {
                    append("<#555555><bold>›</bold> <reset><#FFD700>Range: <#00FF00>${rangeInfo.baseRange}f <#AAAAAA>(offset: <#00FF00>${offset}f<#AAAAAA>)\n")
                }
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
    
    literal("copy").runs { greedyString: GreedyString ->
        setClipboardContent(greedyString.string)
        Message.success("Copied to clipboard!")
    }

}