package me.melinoe.features.impl.misc

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.utils.BossBarUtils
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.ItemUtils
import me.melinoe.utils.Message
import me.melinoe.utils.equalsOneOf
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.ui.RealmSelectorScreen
import me.melinoe.events.InputEvent
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Keybinds Module - Provides configurable keybinds for various commands and menus
 */
object KeybindsModule : Module(
    name = "Keybinds",
    category = Category.MISC,
    description = "Configurable keybinds for commands and menus"
) {

    // Keybind settings with onPress callbacks
    private val realmSelectorKey by KeybindSetting("Realm Selector", GLFW.GLFW_KEY_C, desc = "Open the realm selector menu")
        .onPress { handleRealmSelector() }
    
    private val togglePlayersKey by KeybindSetting("Toggle Players", GLFW.GLFW_KEY_P, desc = "Toggle player visibility")
        .onPress { handleTogglePlayers() }
    
    private val spawnMountKey by KeybindSetting("Spawn Mount", GLFW.GLFW_KEY_V, desc = "Spawn your mount")
        .onPress { handleSpawnMount() }
    
    private val bossPhaseKey by KeybindSetting("Boss Phase", GLFW.GLFW_KEY_F6, desc = "Send Ophanim/True Ophan phase message")
        .onPress { handleBossPhase() }
    
    private val petsMenuKey by KeybindSetting("Pets Menu", GLFW.GLFW_KEY_UNKNOWN, desc = "Open pets menu")
        .onPress { handlePetsMenu() }
    
    private val itemInfoKeySetting = KeybindSetting("Item Info", GLFW.GLFW_KEY_I, desc = "Show item ID info for hovered item in any GUI")
    
    init {
        // Register the item info keybind setting
        registerSetting(itemInfoKeySetting)
        
        // Register screen keyboard event handler for GUI keybinds
        // This will be registered for each screen when it opens
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
                ScreenKeyboardEvents.afterKeyPress(screen).register { _, keyInput ->
                    handleScreenKeyPress(screen, keyInput.key())
                }
            }
        }
    }
    
    /**
     * Handle keyboard input in screens (GUIs)
     */
    private fun handleScreenKeyPress(screen: net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>, key: Int) {
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
        
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Realm selector")
            return
        }
        
        // Open realm selector screen on the render thread
        mc.execute {
            mc.setScreen(RealmSelectorScreen)
        }
    }

    /**
     * Handle toggle players keybind
     */
    private fun handleTogglePlayers() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Toggle players")
            return
        }
        
        player.connection.sendCommand("toggleplayers")
    }

    /**
     * Handle spawn mount keybind
     */
    private fun handleSpawnMount() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Mount")
            return
        }
        
        player.connection.sendCommand("spawnmount")
    }

    /**
     * Handle boss phase keybind - sends Ophanim/True Ophan phase message
     */
    private fun handleBossPhase() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Boss phase")
            return
        }
        
        // Check if in correct dungeon
        val currentArea = LocalAPI.getCurrentCharacterArea()
        val currentDungeon = DungeonData.findByKey(currentArea)
        
        if (currentDungeon == null || 
            !currentDungeon.equalsOneOf(DungeonData.RUSTBORN_KINGDOM, DungeonData.DAWN_OF_CREATION)) {
            Message.error("No True Ophan or Ophanim phase detected.")
            return
        }
        
        // Get current boss
        val currentBoss = LocalAPI.getCurrentCharacterFighting()
        if (currentBoss.isEmpty() || !currentBoss.equalsOneOf("Ophanim", "True Ophan")) {
            Message.error("No True Ophan or Ophanim phase detected.")
            return
        }
        
        // Get boss health percentage
        val healthPercentage = getBossHealthPercentage()
        val currentPhase = getCurrentPhase(healthPercentage)
        
        // Send message to chat
        val message = "$currentBoss is at $healthPercentage% HP - $currentPhase"
        player.connection.sendChat(message)
    }

    /**
     * Get the health percentage of the current boss from boss bars
     */
    private fun getBossHealthPercentage(): Int {
        val bossBars = BossBarUtils.getBossBarMap().values.toList()
        
        // The boss bar is typically at index 1 when there are 5 boss bars
        if (bossBars.size == 5) {
            val bossBar = bossBars[1]
            val progress = bossBar.progress
            return (progress * 100).toInt()
        }
        
        // Fallback: search through all boss bars for one with health info
        for (bossBar in bossBars) {
            val progress = bossBar.progress
            if (progress > 0.0f && progress <= 1.0f) {
                return (progress * 100).toInt()
            }
        }
        
        return 100 // Default to 100% if we can't determine health
    }

    /**
     * Determine the current phase based on boss health percentage
     */
    private fun getCurrentPhase(healthPercentage: Int): String {
        return when {
            healthPercentage in 86..100 -> "First Phase"
            healthPercentage in 61..85 -> "Walls/Eyeballs"
            healthPercentage in 41..60 -> "Clock"
            healthPercentage in 16..40 -> "Wheel/America"
            healthPercentage in 0..15 -> "Grid"
            else -> "Unknown Phase"
        }
    }

    /**
     * Handle pets menu keybind
     */
    private fun handlePetsMenu() {
        if (!enabled) return
        val player = mc.player ?: return
        
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            sendTelosOnlyError("Pets menu")
            return
        }
        
        player.connection.sendCommand("pets")
    }
    
    /**
     * Handle item info keybind - shows item ID info for hovered item
     */
    private fun handleItemInfo() {
        val player = mc.player ?: return
        
        // Check if we're in a screen with slots
        val screen = mc.screen
        if (screen !is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
            return
        }
        
        // Get the hovered slot using accessor
        val accessor = screen as me.melinoe.mixin.accessors.AbstractContainerScreenAccessor
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

    /**
     * Send an error message for features that only work on Telos
     */
    private fun sendTelosOnlyError(featureName: String) {
        Message.error("$featureName is only available on §nᴛᴇʟᴏѕʀᴇᴀʟᴍѕ.ᴄᴏᴍ§r")
    }
}

