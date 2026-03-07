package me.melinoe.features.impl.combat

import me.melinoe.Melinoe
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.events.DungeonChangeEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.TickEvent
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.utils.ItemUtils
import me.melinoe.utils.Message
import me.melinoe.utils.createSoundSettings
import me.melinoe.utils.equalsOneOf
import me.melinoe.utils.playSoundSettings
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

/**
 * Nature's Gift Module - plays a sound notification when player health drops below a threshold.
 * Requires UT or EX Nature's Gift boots equipped to function.
 * Automatically detects item type and adjusts health threshold and cooldown accordingly.
 */
object NaturesGiftModule : Module(
    name = "Nature's Gift",
    category = Category.COMBAT,
    description = "Plays a sound when health drops below threshold (requires Nature's Gift boots)"
) {
    
    // Settings
    private val playSound by BooleanSetting("Play Sound", true, desc = "Enable sound notification")
    private val soundSettings = createSoundSettings(
        name = "Sound",
        default = "minecraft:item.totem.use",
        dependencies = { playSound }
    )
    private val showInfoMessage by BooleanSetting("Show Info Message", true, desc = "Display chat message when Nature's Gift procs")
    private val compactMode by BooleanSetting("Compact Mode", false, desc = "Show only icon and timer/checkmark")
    
    // HUD element for displaying the status
    private val statusHud by HUDSetting(
        name = "Status Display",
        x = 10,
        y = 10,
        scale = 1f,
        toggleable = false,
        description = "Position of the Nature's Gift status display.",
        module = this
    ) { example ->
        val textRenderer = mc.font
        
        // Check if player has Nature's Gift boots equipped
        val equippedItem = getEquippedNatureGift()
        val itemType = equippedItem?.let { ItemUtils.ItemType.fromItemStack(it) }
        
        // If no Nature's Gift equipped and not in example mode, don't render anything
        if (!example && itemType == null) {
            return@HUDSetting 0 to 0
        }
        
        // Create example item for preview mode
        val displayItem = if (example) {
            ItemStack(net.minecraft.world.item.Items.CHAINMAIL_BOOTS).apply {
                set(
                    net.minecraft.core.component.DataComponents.ITEM_MODEL,
                    ResourceLocation.fromNamespaceAndPath("telos", "material/armour/light/boots/ut-nature")
                )
            }
        } else {
            equippedItem!!
        }
        
        val text = if (example) {
            if (compactMode) "✔" else "Nature's Gift: Ready!"
        } else if (isOnCooldown) {
            val remainingSeconds = getTimeUntilNextNotification()
            if (compactMode) "${remainingSeconds}s" else "Nature's Gift: ${remainingSeconds}s"
        } else {
            if (compactMode) "✔" else "Nature's Gift: Ready!"
        }
        
        // Color based on state: green when ready, red when on cooldown
        val color = if (example || !isOnCooldown) 0xFF55FF55.toInt() else 0xFFAA0000.toInt()
        
        val iconSize = 16
        val iconPadding = 2
        val textWidth = textRenderer.width(text)
        val textHeight = textRenderer.lineHeight
        
        // Render the item icon (aligned with text)
        renderItem(displayItem, 0, 0)
        
        // Draw the text next to the icon (vertically centered with icon)
        val textYOffset = (iconSize - textHeight) / 2
        drawString(textRenderer, text, iconSize + iconPadding, textYOffset, color, true)
        
        (iconSize + iconPadding + textWidth) to iconSize
    }
    
    // State tracking
    private var wasHealthBelowThreshold = false
    private var hadRegen = false
    private var isOnCooldown = false
    private var cooldownEndTime = 0L
    
    // Cached item data
    private var cachedHealthThreshold = 30.0f
    private var cachedCooldownDuration = 360.0f
    
    init {
        // Register tick event for health monitoring
        on<TickEvent.End> {
            if (!enabled) return@on
            
            val player = Melinoe.mc.player ?: return@on
            
            // Check if player has Nature's Gift boots equipped
            val equippedItem = getEquippedNatureGift()
            if (equippedItem == null) {
                // Clear state memory if unequipped so it doesn't instantly proc upon re-equipping
                wasHealthBelowThreshold = false
                hadRegen = false
                return@on
            }
            
            // Get item type and update thresholds
            val itemType = ItemUtils.ItemType.fromItemStack(equippedItem)
            updateThresholdsFromItem(itemType, equippedItem)
            
            // Check health threshold
            val currentHealth = player.health
            val maxHealth = player.maxHealth
            val healthPercentage = (currentHealth / maxHealth) * 100.0f
            val isHealthBelowThreshold = healthPercentage <= cachedHealthThreshold
            
            // Check regeneration effect applied
            val regenEffect = player.getEffect(MobEffects.REGENERATION)
            val hasRegen5 = regenEffect != null && regenEffect.amplifier == 4
            
            // Determine if the ability just procced
            val shouldTrigger = (isHealthBelowThreshold && !wasHealthBelowThreshold) || (hasRegen5 && !hadRegen)
            
            if (shouldTrigger && !isOnCooldown) {
                // Activate ability and start cooldown
                startCooldown()
                // Play sound when ability is activated
                playNotificationSound()
                // Show info message
                showProcMessage()
            }
            
            // Update previous state trackers
            wasHealthBelowThreshold = isHealthBelowThreshold
            hadRegen = hasRegen5
            
            // Update cooldown progress
            if (isOnCooldown) {
                val currentTime = System.currentTimeMillis()
                if (currentTime >= cooldownEndTime) {
                    // Cooldown finished
                    isOnCooldown = false
                }
            }
        }
        
        // Register dungeon event handlers for automatic cooldown resets
        // Nature's Gift doesn't have a trackable cooldown like other items,
        // so we reset on dungeon transitions to ensure accuracy
        on<DungeonEntryEvent> {
            resetCooldown()
        }
        
        on<DungeonExitEvent> {
            resetCooldown()
        }
        
        on<DungeonChangeEvent> {
            resetCooldown()
        }
        
        // Register world load event for cooldown reset
        on<WorldLoadEvent> {
            resetCooldown()
        }
    }
    
    /**
     * Get the equipped Nature's Gift boots from the player's feet slot
     */
    private fun getEquippedNatureGift(): ItemStack? {
        val player = Melinoe.mc.player ?: return null
        val bootsSlot = player.getItemBySlot(EquipmentSlot.FEET)
        
        if (bootsSlot.isEmpty) return null
        
        // Check if it's a Nature's Gift item
        val itemType = ItemUtils.ItemType.fromItemStack(bootsSlot)
        return if (itemType.equalsOneOf(ItemUtils.ItemType.UT_NATURE, ItemUtils.ItemType.EX_NATURE)) {
            bootsSlot
        } else {
            null
        }
    }
    
    /**
     * Update health threshold and cooldown duration based on equipped item
     */
    private fun updateThresholdsFromItem(itemType: ItemUtils.ItemType?, item: ItemStack) {
        // Set health threshold based on item type
        cachedHealthThreshold = when (itemType) {
            ItemUtils.ItemType.UT_NATURE -> 30.0f
            ItemUtils.ItemType.EX_NATURE -> 35.0f
            else -> 30.0f
        }
        
        // Try to parse cooldown from lore
        val parsedCooldown = ItemUtils.parseItemCooldown(item)
        if (parsedCooldown > 0) {
            cachedCooldownDuration = parsedCooldown
        } else {
            // Default cooldown if not found in lore
            cachedCooldownDuration = 360.0f
        }
    }
    
    /**
     * Start the cooldown period
     */
    private fun startCooldown() {
        cooldownEndTime = System.currentTimeMillis() + (cachedCooldownDuration * 1000).toLong()
        isOnCooldown = true
    }
    
    /**
     * Show proc message in chat
     */
    private fun showProcMessage() {
        if (!showInfoMessage) return
        
        try {
            // Create a styled message using Message utility
            val message = Component.literal("Nature's Gift ")
                .withStyle(net.minecraft.ChatFormatting.GREEN)
                .append(Component.literal("activated! ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal("(+9 HP over 1.5s)")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW))
            
            Message.chat(message)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Play the notification sound.
     */
    private fun playNotificationSound() {
        if (!playSound) return
        
        try {
            playSoundSettings(soundSettings())
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Reset the cooldown
     */
    private fun resetCooldown() {
        isOnCooldown = false
        cooldownEndTime = 0
        wasHealthBelowThreshold = false
        hadRegen = false
    }
    
    /**
     * Get time until next notification is available (in seconds)
     */
    private fun getTimeUntilNextNotification(): Long {
        if (!isOnCooldown) return 0
        
        val currentTime = System.currentTimeMillis()
        val remainingMs = cooldownEndTime - currentTime
        return kotlin.math.max(0, kotlin.math.ceil(remainingMs / 1000.0).toLong())
    }
    
    override fun onDisable() {
        super.onDisable()
        // Clear cooldown state when disabled
        cooldownEndTime = 0
        isOnCooldown = false
        wasHealthBelowThreshold = false
        hadRegen = false
    }
}