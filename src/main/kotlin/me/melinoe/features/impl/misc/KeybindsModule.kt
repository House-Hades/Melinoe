package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.mixin.accessors.AbstractContainerScreenAccessor
import me.melinoe.utils.*
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.ui.RealmSelectorScreen
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

/**
 * Keybinds Module - Provides configurable keybinds for various commands and menus
 */
object KeybindsModule : Module(
    name = "Keybinds",
    category = Category.MISC,
    description = "Configurable keybinds for commands and menus"
) {

    private val bossPhaseKey by KeybindSetting("Boss Phase", GLFW.GLFW_KEY_F6, desc = "Send boss/dungeon phase message")
        .onPress { handleBossPhase() }
    
    private val bossesMenuKey by KeybindSetting("Bosses Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open bosses menu")
        .onPress { sendTelosCommand("bosses", "Bosses menu") }

    private val mountsMenuKey by KeybindSetting("Mounts Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open mounts menu")
        .onPress { sendTelosCommand("mounts", "Mounts menu") }

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

        // Open realm selector screen on the render thread
        mc.execute {
            mc.setScreen(RealmSelectorScreen)
        }
    }
    
    private var lastUsedTime: Long = 0L
    private val COOLDOWN_MS: Long = 10000L
    
    /**
     * Handle boss phase keybind - sends current phase or dungeon status
     */
    private fun handleBossPhase() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Boss phase")
            return
        }
        
        // Cooldown handling
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUsedTime < COOLDOWN_MS) {
            val remainingSeconds = (COOLDOWN_MS - (currentTime - lastUsedTime)) / 1000.0
            Message.error(String.format("Please wait %.1fs before using this again.", remainingSeconds))
            return
        }
        
        // Check if in correct dungeon
        if (LocalAPI.isInDungeon()) {
            Message.error("You are not currently in a dungeon.")
            return
        }
        
        val currentArea = LocalAPI.getCurrentCharacterArea()
        
        val currentDungeon = DungeonData.findByKey(currentArea)
        if (currentDungeon == null) {
            Message.error("You are not currently in a dungeon.")
            return
        }
        
        val hp = getBossHealthPercentage()
        lastUsedTime = currentTime
        
        if (hp <= 0) {
            player.connection.sendChat("Currently in $currentArea")
            return
        }
        
        var currentBoss = LocalAPI.getCurrentCharacterFighting() ?: ""
        
        // If LocalAPI fails to fetch the boss name, force it from the dungeon
        if (currentBoss.trim().isEmpty()) {
            currentBoss = when (currentDungeon) {
                DungeonData.RUSTBORN_KINGDOM -> "Ophanim"
                DungeonData.DAWN_OF_CREATION -> "True Ophan"
                DungeonData.SERAPHS_DOMAIN -> "True Seraph"
                DungeonData.TENEBRIS -> "Voided Omnipotent"
                DungeonData.RAPHS_CHAMBER -> "Raphael"
                DungeonData.DREADWOOD_THICKET -> "Sylvaris"
                DungeonData.CELESTIALS_PROVINCE -> "Seraphim"
                else -> "The boss"
            }
        }
        
        val currentPhase = getCurrentPhase(currentBoss, hp, currentDungeon)
        
        // Send phase or fallback message
        if (currentPhase != null) {
            player.connection.sendChat("$currentBoss is at $hp% HP - $currentPhase")
        } else {
            player.connection.sendChat("$currentBoss is at $hp% HP - $currentArea")
        }
    }
    
    /**
     * Get the health percentage of the current boss from boss bars safely
     */
    private fun getBossHealthPercentage(): Int {
        val bossBarMap = BossBarUtils.getBossBarMap()
        if (bossBarMap.isNullOrEmpty()) return -1
        
        val bossBars = bossBarMap.values.toList()
        
        // The boss bar is typically at index 1 when there are 5 boss bars
        if (bossBars.size == 5) {
            val progress = bossBars.elementAtOrNull(1)?.progress ?: 0.0f
            if (progress > 0.0f) {
                return kotlin.math.round(progress * 100.0f).toInt()
            }
        }
        
        // Fallback: search through all boss bars for one with valid health info
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
    private fun getCurrentPhase(bossName: String, hp: Int, dungeon: DungeonData): String? {
        val name = bossName.lowercase()
        
        // Use contains to bypass any hidden color codes or weird spacing LocalAPI might return
        return when {
            name.contains("ophan") -> {
                if (dungeon != DungeonData.RUSTBORN_KINGDOM && dungeon != DungeonData.DAWN_OF_CREATION) return null
                when (hp) {
                    in 86..100 -> "First Phase"
                    in 61..85 -> "Walls/Eyeballs"
                    in 41..60 -> "Clock"
                    in 16..40 -> "Wheel/America"
                    in 0..15 -> "Grid"
                    else -> "Unknown Phase"
                }
            }
            name.contains("asmodeus") -> {
                if (dungeon != DungeonData.CELESTIALS_PROVINCE) return null
                when (hp) {
                    in 76..100 -> "First Phase"
                    in 51..75 -> "Second Phase"
                    in 21..50 -> "Third Phase"
                    in 0..20 -> "Fourth Phase"
                    else -> "Unknown Phase"
                }
            }
            name.contains("seraph") -> {
                if (dungeon != DungeonData.CELESTIALS_PROVINCE && dungeon != DungeonData.SERAPHS_DOMAIN) return null
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
            name.contains("omnipotent") || name.contains("voided") -> {
                if (dungeon != DungeonData.TENEBRIS) return null
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
            name.contains("raphael") -> {
                if (dungeon != DungeonData.RAPHS_CHAMBER) return null
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
            name.contains("sylvaris") -> {
                if (dungeon != DungeonData.DREADWOOD_THICKET) return null
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
            append("<gray>Item ID Information\n")
            append("<dark_gray><bold>›</bold> <gold>Display Name: <white>$displayName\n")
            append("<dark_gray><bold>›</bold> <gold>Base ID: <white>$itemId\n")

            // Show Unicode character info
            if (unicodeChar != null && unicodeChar.isNotEmpty()) {
                append("<dark_gray><bold>›</bold> <gold>Unicode Char: <white>$unicodeChar\n")

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
                append("<dark_gray><bold>›</bold> <gold>Unicode Escape: <white>$escapeSequence\n")
            }

            // Show parsed range from lore
            if (parsedRange > 0) {
                append("<dark_gray><bold>›</bold> <gold>Lore Range: <green>${parsedRange}f\n")
            }

            // Show ItemType match status
            if (itemType != null) {
                append("<dark_gray><bold>›</bold> <gold>ItemType: <green>${itemType.name}\n")
                val (range, offset) = ItemUtils.getItemRangeWithOffset(heldItem)
                append("<dark_gray><bold>›</bold> <gold>Range: <green>${range}f <gray>(offset: <green>${offset}f<gray>)\n")
            } else {
                append("<dark_gray><bold>›</bold> <gold>ItemType: <gray>Not found\n")
            }

            // Show custom model info
            if (customModel != null) {
                append("<dark_gray><bold>›</bold> <gold>Custom Model: <white>$customModel\n")
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
                append("\n<gray>$enumName <dark_gray>-> <gray>\"$escapeSequence\"")
            } else if (itemType != null) {
                append("\n<green>✔ Item matched with utils")
            }
        }

        Message.dev(message)
    }

    /**
     * Send an error message for features that only work on Telos
     */
    private fun sendTelosOnlyError(featureName: String) {
        Message.error("$featureName is only available on §nᴛᴇʟᴏѕʀᴇᴀʟᴍѕ.ᴄᴏᴍ§r")
    }
}