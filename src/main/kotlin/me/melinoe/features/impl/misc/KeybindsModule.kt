package me.melinoe.features.impl.misc

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.mixin.accessors.AbstractContainerScreenAccessor
import me.melinoe.utils.*
import me.melinoe.utils.data.PortalData
import me.melinoe.utils.ui.RealmSelectorScreen
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Evoker
import org.lwjgl.glfw.GLFW
import java.util.Locale

/**
 * Keybinds Module - Provides configurable keybinds for various commands and menus
 */
object KeybindsModule : Module(
    name = "Keybinds",
    category = Category.MISC,
    description = "Configurable keybinds for commands and menus"
) {

    private val calloutKey by KeybindSetting("Boss/Portal Callout", GLFW.GLFW_KEY_F6, desc = "Send a boss/dungeon/portal callout message")
        .onPress { handleBossPhase() }
    
    private val bossesMenuKey by KeybindSetting("Bosses Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open bosses menu")
        .onPress { sendTelosCommand("bosses", "Bosses menu") }

    private val mountsMenuKey by KeybindSetting("Mounts Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open mounts menu")
        .onPress { sendTelosCommand("mounts", "Mounts menu") }
    
    private val openBackpackKey by KeybindSetting("Open backpack", GLFW.GLFW_KEY_UNKNOWN, desc = "Open your backpack")
        .onPress { sendTelosCommand("backpack", "Open backpack") }

    private val petsMenuKey by KeybindSetting("Pets Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open pets menu")
        .onPress { sendTelosCommand("pets", "Pets menu") }

    private val realmSelectorKey by KeybindSetting("Realm Selector", GLFW.GLFW_KEY_C, desc = "Open the realm selector menu")
        .onPress { handleRealmSelector() }

    private val spawnMountKey by KeybindSetting("Spawn Mount", GLFW.GLFW_KEY_V, desc = "Spawn your mount")
        .onPress { sendTelosCommand("spawnmount", "Spawn mount") }

    private val useStickerKey by KeybindSetting("Use Sticker", GLFW.GLFW_KEY_UNKNOWN, desc = "Use your sticker")
        .onPress { sendTelosCommand("showtheselectedsticker", "Use sticker") }

    // Toggle commands
    private val togglesDropdown by DropdownSetting("Toggles", false)

    private val toggleMountsKey by KeybindSetting("Toggle Mounts", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle mount visibility")
        .withDependency { togglesDropdown }
        .onPress { sendTelosCommand("togglemounts", "Toggle mounts") }

    private val togglePetsKey by KeybindSetting("Toggle Pets", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle pet visibility")
        .withDependency { togglesDropdown }
        .onPress { sendTelosCommand("togglepets", "Toggle pets") }

    private val togglePlayersKey by KeybindSetting("Toggle Players", GLFW.GLFW_KEY_P, desc = "Toggle player visibility")
        .withDependency { togglesDropdown }
        .onPress { sendTelosCommand("toggleplayers", "Toggle players") }

    private val toggleStickersKey by KeybindSetting("Toggle Stickers", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle sticker visibility")
        .withDependency { togglesDropdown }
        .onPress { sendTelosCommand("togglestickers", "Toggle stickers") }

    // Supporter only commands
    private val supporterDropdown by DropdownSetting("Supporter", false)

    private val openStashKey by KeybindSetting("Open Stash", GLFW.GLFW_KEY_UNKNOWN, desc = "Open your stash")
        .withDependency { supporterDropdown }
        .onPress { sendTelosCommand("stash", "Stash") }

    private val teleportCentreKey by KeybindSetting("Teleport (Centre)", GLFW.GLFW_KEY_UNKNOWN, desc = "Teleport to the centre")
        .withDependency { supporterDropdown }
        .onPress { sendTelosCommand("centre", "Teleport (Centre)") }

    private val teleportSpawnKey by KeybindSetting("Teleport (Spawn)", GLFW.GLFW_KEY_UNKNOWN, desc = "Teleport to spawn")
        .withDependency { supporterDropdown }
        .onPress { sendTelosCommand("spawn", "Teleport (Spawn)") }

    private val itemInfoKeySetting =
        KeybindSetting("Item Info (Dev)", GLFW.GLFW_KEY_I, desc = "Show item ID info for hovered item in any GUI")

    // Safety checks
    private var realmSelectorPressCount = 0
    private var realmSelectorLastPress = 0L
    
    init {
        // Register the item info keybind setting
        registerSetting(itemInfoKeySetting)

        // Register screen keyboard event handler for GUI keybinds
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*>) {
                ScreenKeyboardEvents.afterKeyPress(screen).register { _, keyInput ->
                    handleScreenKeyPress(screen, keyInput.key())
                }
            }
        }
    }

    /**
     * Handle telos related commands
     */
    private fun sendTelosCommand(command: String, featureName: String) {
        if (!enabled) return
        val player = mc.player ?: return

        if (!ServerUtils.isOnTelos()) {
            sendTelosOnlyError(featureName)
            return
        }

        player.connection.sendCommand(command)
    }

    /**
     * Handle keyboard input in screens (GUIs)
     */
    private fun handleScreenKeyPress(screen: AbstractContainerScreen<*>, key: Int) {
        if (!enabled) return

        // Item info keybind - works in any container screen
        if (key == itemInfoKeySetting.value.value) {
            handleItemInfo()
        }
    }

    /**
     * Handle realm selector keybind
     */
    private fun handleRealmSelector() {
        if (!enabled) return
        if (mc.player == null) return

        if (!ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Realm selector")
            return
        }
        
        // Dungeon safety check
        if (LocalAPI.isInDungeon()) {
            val currentTime = System.currentTimeMillis()
            
            // If more than 5 seconds have passed, reset the sequence
            if (currentTime - realmSelectorLastPress > 5000) {
                realmSelectorPressCount = 1
                realmSelectorLastPress = currentTime
                Message.actionBar("${getMelinoeWatermark()} ${Message.Colors.ERROR}<bold>Warning:</bold> You are in a dungeon! Press 2 more times within 5s to open the Realm Selector.")
                return // Prevent opening menu
            } else {
                realmSelectorPressCount++
                if (realmSelectorPressCount < 3) {
                    val remaining = 3 - realmSelectorPressCount
                    Message.actionBar("${getMelinoeWatermark()} ${Message.Colors.ERROR}Press $remaining more time to open the Realm Selector.")
                    return // Prevent opening menu
                }
                // If we reach here, 3 presses completed successfully, reset
                realmSelectorPressCount = 0
            }
        }

        // Open realm selector screen on the render thread
        mc.execute {
            mc.setScreen(RealmSelectorScreen)
        }
    }
    
    private var lastUsedTime: Long = 0L
    private val COOLDOWN_MS: Long = 10000L
    private val PORTAL_REGEX = Regex("-=\\[(.*?)]=-")
    
    /**
     * Handle boss phase keybind - sends current phase or dungeon status
     */
    private fun handleBossPhase() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Boss/Dungeon callout")
            return
        }
        
        if (LocalAPI.isInNexus()) {
            Message.error("No boss or portal detected!")
            return
        }
        
        // Cooldown handling
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUsedTime < COOLDOWN_MS) {
            val remainingSeconds = (COOLDOWN_MS - (currentTime - lastUsedTime)) / 1000.0
            Message.error(String.format("Please wait %.1fs before using this again.", remainingSeconds))
            return
        }
        
        val currentArea = LocalAPI.getCurrentCharacterArea()
        val hp = getBossHealthPercentage()
        val currentBoss = LocalAPI.getCurrentCharacterFighting()
        
        if (LocalAPI.isInDungeon()) {
            if (hp <= 0) {
                player.connection.sendChat("Currently in $currentArea")
                return
            }
            
            val currentPhase = getCurrentPhase(currentBoss, hp)
            if (currentPhase != null) {
                player.connection.sendChat("$currentBoss is at $hp% HP - $currentPhase")
            } else {
                player.connection.sendChat("$currentBoss is at $hp% HP - $currentArea")
            }
            lastUsedTime = currentTime
        } else {
            if (hp > 0) {
                player.connection.sendChat("Teleport for $currentBoss - $hp% HP")
                lastUsedTime = currentTime
            } else {
                val level = mc.level ?: return
                
                // Sequences are used for better memory usage
                val closestPortal = level.getEntitiesOfClass(
                    Evoker::class.java,
                    player.boundingBox.inflate(10.0)
                ).asSequence()
                    .filter { it.distanceToSqr(player) <= 100.0 }
                    .mapNotNull { evoker ->
                        // Evokers are used as they are the entity behind portals
                        val headItem = evoker.getItemBySlot(EquipmentSlot.HEAD)
                        val itemModel = headItem.get(DataComponents.ITEM_MODEL)?.toString()
                            ?: return@mapNotNull null
                        
                        val rawName = itemModel.substringAfterLast("/").uppercase(Locale.ROOT)
                        
                        // Ignore return portals
                        if (rawName == "RETURN") return@mapNotNull null
                        
                        Pair(evoker, rawName)
                    }
                    .sortedBy { it.first.distanceToSqr(player) }
                    .firstNotNullOfOrNull { (evoker, rawName) ->
                        // Get the armor stands around the detected portal to find how many seconds are left
                        val armorStands = level.getEntitiesOfClass(
                            ArmorStand::class.java,
                            evoker.boundingBox.inflate(4.0, 10.0, 4.0)
                        )
                        
                        val validStand = armorStands.asSequence()
                            .filter { it.y >= evoker.y && it.hasCustomName() }
                            .minByOrNull { it.distanceToSqr(evoker) } ?: return@firstNotNullOfOrNull null
                        
                        val standName = validStand.customName?.string ?: return@firstNotNullOfOrNull null
                        val match = PORTAL_REGEX.find(standName) ?: return@firstNotNullOfOrNull null
                        
                        // Pass along the evoker, the rawName, and the timer value
                        Triple(evoker, rawName, match.groupValues[1])
                    }
                
                if (closestPortal != null) {
                    val (_, rawName, value) = closestPortal
                    
                    // runCatching prevents a game crash if the enum doesn't exist
                    val cleanName = runCatching { PortalData.valueOf(rawName).label }.getOrNull()
                    
                    if (cleanName != null) {
                        player.connection.sendChat("Teleport for $cleanName - ${value}s left")
                        lastUsedTime = currentTime
                    } else {
                        Melinoe.logger.error("Unknown portal type detected: $rawName")
                    }
                } else {
                    Message.error("No boss or portal detected!")
                }
            }
        }
    }
    
    /**
     * Get the health percentage of the current boss from boss bars safely
     */
    private fun getBossHealthPercentage(): Int {
        val bossBarMap = BossBarUtils.getBossBarMap()
        if (bossBarMap.isEmpty()) return -1
        
        val bossBars = bossBarMap.values.toList()
        
        // The boss bar is typically at index 1 when there are 5 boss bars
        if (bossBars.size == 5) {
            val progress = bossBars.elementAtOrNull(1)?.progress ?: 0.0f
            if (progress > 0.0f) {
                return kotlin.math.round(progress * 100.0f).toInt()
            }
        }
        
        // Search through all boss bars for one with valid health info
        for (bossBar in bossBars) {
            val progress = bossBar.progress
            if (progress > 0.0f && progress <= 1.0f) {
                return kotlin.math.round(progress * 100.0f).toInt()
            }
        }
        
        return -1
    }
    
    /**
     * Determine the current phase based on boss name, health percentage, and dungeon
     */
    private fun getCurrentPhase(bossName: String, hp: Int): String? {
        return when {
            bossName == "Ophanim" || bossName == "True Ophan" -> {
                when (hp) {
                    in 86..100 -> "First Phase"
                    in 61..85 -> "Walls/Eyeballs"
                    in 41..60 -> "Clock"
                    in 16..40 -> "Wheel/America"
                    in 0..15 -> "Grid"
                    else -> "Unknown Phase"
                }
            }
            bossName == "Asmodeus" -> {
                when (hp) {
                    in 76..100 -> "First Phase"
                    in 51..75 -> "Second Phase"
                    in 21..50 -> "Third Phase"
                    in 0..20 -> "Fourth Phase"
                    else -> "Unknown Phase"
                }
            }
            bossName == "Seraphim" || bossName == "True Seraph" -> {
                when (hp) {
                    in 82..100 -> "First Phase"
                    in 80..81 -> "Chicken"
                    in 52..79 -> "Slow Beams/Dance"
                    in 50..51 -> "QR Code"
                    in 21..49 -> "Fast Beams/Rain"
                    in 0..20 -> "Desperation"
                    else -> "Unknown Phase"
                }
            }
            bossName == "Voided Omnipotent" -> {
                when (hp) {
                    100 -> "Unchaining"
                    in 86..99 -> "Second Phase"
                    in 66..85 -> "Chase"
                    in 31..65 -> "Snakes/Pillars/Black Holes"
                    in 16..30 -> "Bells"
                    in 0..15 -> "Desperation"
                    else -> "Unknown Phase"
                }
            }
            bossName == "Raphael" -> {
                when (hp) {
                    in 77..100 -> "First Phase"
                    in 75..76 -> "Memorise"
                    in 52..74 -> "Swords/Beams"
                    in 50..51 -> "Bell"
                    in 16..49 -> "Dash/Denmark/Tridents"
                    in 0..15 -> "Desperation"
                    else -> "Unknown Phase"
                }
            }
            bossName == "Sylvaris" -> {
                when (hp) {
                    in 76..100 -> "First Phase"
                    in 51..75 -> "Shulker"
                    in 26..50 -> "Third Phase"
                    in 0..25 -> "Arrows"
                    else -> "Unknown Phase"
                }
            }
            else -> null
        }
    }

    /**
     * Handle item info keybind - shows item ID info for hovered item
     */
    private fun handleItemInfo() {
        val player = mc.player ?: return

        // Check if we're in a screen with slots
        val screen = mc.screen
        if (screen !is AbstractContainerScreen<*>) {
            return
        }

        // Get the hovered slot using accessor
        val accessor = screen as AbstractContainerScreenAccessor
        val hoveredSlot = accessor.hoveredSlot ?: return

        val heldItem = hoveredSlot.item
        if (heldItem.isEmpty) {
            return
        }

        // Get the item's base ID
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(heldItem.item).toString()

        // Get custom model data if present
        val customModel = heldItem.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)

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

        // Build the message
        val message = buildString {
            append("<#AAAAAA>Item ID Information\n")
            append("<#555555><bold>›</bold> <#FFD700>Display Name: <#FFFFFF>$displayName\n")
            append("<#555555><bold>›</bold> <#FFD700>Base ID: <#FFFFFF>$itemId\n")

            // Show Unicode character info
            if (unicodeChar != null && unicodeChar.isNotEmpty()) {
                append("<#555555><bold>›</bold> <#FFD700>Unicode Char: <#FFFFFF>$unicodeChar\n")

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
                append("<#555555><bold>›</bold> <#FFD700>Unicode Escape: <#FFFFFF>$escapeSequence\n")
            }

            // Show parsed range from lore
            if (parsedRange > 0) {
                append("<#555555><bold>›</bold> <#FFD700>Lore Range: <#00FF00>${parsedRange}f\n")
            }

            // Show ItemType match status
            if (itemType != null) {
                append("<#555555><bold>›</bold> <#FFD700>ItemType: <#00FF00>${itemType.name}\n")
                val (range, offset) = ItemUtils.getItemRangeWithOffset(heldItem)
                append("<#555555><bold>›</bold> <#FFD700>Range: <#00FF00>${range}f <#AAAAAA>(offset: <#00FF00>${offset}f<#AAAAAA>)\n")
            } else {
                append("<#555555><bold>›</bold> <#FFD700>ItemType: <#AAAAAA>Not found\n")
            }

            // Show custom model info
            if (customModel != null) {
                append("<#555555><bold>›</bold> <#FFD700>Custom Model: <#FFFFFF>$customModel\n")
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
                append("\n<#AAAAAA>$enumName <#555555>-> <#AAAAAA>\"$escapeSequence\"")
            } else if (itemType != null) {
                append("\n<#00FF00>✔ Item matched with utils")
            }
        }

        Message.dev(message)
    }

    /**
     * Send an error message for features that only work on Telos
     */
    private fun sendTelosOnlyError(featureName: String) {
        Message.error("$featureName is only available on <underlined>ᴛᴇʟᴏѕʀᴇᴀʟᴍѕ.ᴄᴏᴍ</underlined><reset>")
    }
}