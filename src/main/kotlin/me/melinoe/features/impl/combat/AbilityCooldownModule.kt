package me.melinoe.features.impl.combat

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.createSoundSettings
import me.melinoe.utils.playSoundSettings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack

/**
 * Ability Cooldown Module - visual cooldown bar with title popups.
 */
object AbilityCooldownModule : Module(
    name = "Ability Cooldown",
    category = Category.COMBAT,
    description = "Displays ability cooldown with visual notifications."
) {
    
    private const val BAR_WIDTH = 16
    private const val BAR_HEIGHT = 5
    private const val BAR_BG_COLOR = 0xC0404040.toInt()
    private const val BAR_BORDER_COLOR = 0xFF252326.toInt()
    private const val BAR_LEFT_OFFSET = 22
    
    val showHud = registerSetting(BooleanSetting("Show HUD", true, desc = "Toggle the visual cooldown bar and icon"))
    
    private val titleHud by HUDSetting(
        name = "Title Display",
        x = 400,
        y = 200,
        scale = 2f,
        toggleable = true,
        default = true,
        description = "Position of the ability ready title popup. Toggle to show/hide.",
        module = this
    ) { example ->
        if (customTitle == null && !example) return@HUDSetting 0 to 0
        
        val title = if (example) {
            buildStyledTitleText(titleText, titleColor.rgba)
        } else {
            customTitle!!
        }
        val textRenderer = mc.font
        val textWidth = textRenderer.width(title)
        val textHeight = textRenderer.lineHeight
        
        drawString(textRenderer, title, 0, 0, titleColor.rgba, true)
        
        textWidth to textHeight
    }
    
    val titleText: String by StringSetting("Title Text", "Ability Ready!", desc = "Text to display in title popup").withDependency { titleHud.enabled }
    val duration: Float by NumberSetting("Duration", 60.0f, 10.0f, 100.0f, desc = "Duration of title display in ticks").withDependency { titleHud.enabled }
    val titleColor: Color by ColorSetting("Title Color", Color(0xFF7CFFB2.toInt()), desc = "Color of the title text").withDependency { titleHud.enabled }
    
    val playSound = registerSetting(BooleanSetting("Play Sound", true, desc = "Play sound when ability is ready"))
    private val soundSettings = createSoundSettings(
        name = "Sound",
        default = "entity.player.levelup",
        dependencies = { playSound.value }
    )
    
    private var trackedAbility = ItemStack.EMPTY
    private var displayedAbility = ItemStack.EMPTY
    private var cooldownProgress = 0f
    private var previousCooldownProgress = 0f
    private var indicatorStartTime = 0L
    private const val INDICATOR_FADE_MS = 250L
    private var customTitle: Component? = null
    private var titleDisplayTicks = 0
    
    init {
        on<TickEvent.End> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            
            val heldAbility = getCurrentPlayerAbility()
            displayedAbility = heldAbility
            
            if (!heldAbility.isEmpty && !ItemStack.matches(heldAbility, trackedAbility)) {
                trackedAbility = heldAbility.copy()
            }
            
            // Track cooldown even if we switched items
            if (!trackedAbility.isEmpty) {
                previousCooldownProgress = cooldownProgress
                cooldownProgress = player.cooldowns.getCooldownPercent(trackedAbility, 0f)
                
                // Triggers exactly when it hits 0.0f
                if (previousCooldownProgress > 0f && cooldownProgress == 0f) {
                    indicatorStartTime = System.currentTimeMillis()
                    
                    if (titleHud.enabled) {
                        customTitle = buildStyledTitleText(titleText, titleColor.rgba)
                        titleDisplayTicks = duration.toInt()
                    }
                    
                    if (playSound.enabled) {
                        playNotificationSound()
                    }
                }
            }
            
            if (previousCooldownProgress == 0f && cooldownProgress > 0f) {
                titleDisplayTicks = 0
                customTitle = null
            }
            
            // Title display duration
            if (titleDisplayTicks > 0) {
                titleDisplayTicks--
                if (titleDisplayTicks <= 0) {
                    customTitle = null
                }
            }
        }
    }
    
    
    /**
     * Color interpolation
     */
    private fun getCooldownColor(progress: Float): Int {
        val redR = 139f; val redG = 0f; val redB = 0f
        val orgR = 250f; val orgG = 129f; val orgB = 42f
        
        // Linear interpolation
        val r = (orgR + (redR - orgR) * progress).toInt()
        val g = (orgG + (redG - orgG) * progress).toInt()
        val b = (orgB + (redB - orgB) * progress).toInt()
        
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    fun renderHud(context: GuiGraphics) {
        if (!enabled) return
        if (!showHud.value) return
        if (displayedAbility.isEmpty) return
        
        val windowWidth = mc.window.guiScaledWidth
        val windowHeight = mc.window.guiScaledHeight
        val halfWindowWidth = windowWidth / 2
        val halfWindowHeight = windowHeight / 2
        
        val barX = halfWindowWidth + BAR_LEFT_OFFSET
        val barY = halfWindowHeight - BAR_WIDTH / 2
        val progress = cooldownProgress.coerceIn(0f, 1f)
        
        val currentTime = System.currentTimeMillis()
        val timeSinceIndicator = currentTime - indicatorStartTime
        val indicatorActive = timeSinceIndicator < INDICATOR_FADE_MS && indicatorStartTime > 0
        
        val shouldShowBar = progress > 0.0f || (progress == 0.0f && !indicatorActive && indicatorStartTime > 0) || (progress == 0.0f && indicatorStartTime == 0L)
        
        if (shouldShowBar) {
            val barX1 = barX
            val barY1 = barY
            val barX2 = barX + BAR_HEIGHT
            val barY2 = barY + BAR_WIDTH
            
            // Draw outline
            context.hLine(barX1, barX2 - 1, barY1, BAR_BORDER_COLOR)
            context.hLine(barX1, barX2 - 1, barY2 - 1, BAR_BORDER_COLOR)
            context.vLine(barX1, barY1, barY2 - 1, BAR_BORDER_COLOR)
            context.vLine(barX2 - 1, barY1, barY2 - 1, BAR_BORDER_COLOR)
            
            val innerX1 = barX1 + 1
            val innerY1 = barY1 + 1
            val innerX2 = barX2 - 1
            val innerY2 = barY2 - 1
            
            // Draw background
            context.fill(innerX1, innerY1, innerX2, innerY2, BAR_BG_COLOR)
            
            if (progress > 0.0f) {
                val innerHeight = innerY2 - innerY1
                val emptySpace = kotlin.math.ceil(innerHeight * progress).toInt()
                val barFillY = innerY1 + emptySpace
                
                if (barFillY < innerY2) {
                    val cooldownColor = getCooldownColor(progress)
                    context.fill(innerX1, barFillY, innerX2, innerY2, cooldownColor)
                }
            } else {
                // Solid green when ready
                val readyColor = 0xFF02963D.toInt()
                context.fill(innerX1, innerY1, innerX2, innerY2, readyColor)
            }
        }
        
        // Draw indicator
        if (indicatorActive) {
            val indicatorAlpha = 1.0f - (timeSinceIndicator.toFloat() / INDICATOR_FADE_MS)
            if (indicatorAlpha > 0.0f) {
                val barX1 = barX
                val barY1 = barY
                val barX2 = barX + BAR_HEIGHT
                val barY2 = barY + BAR_WIDTH
                
                val centerX = (barX1 + barX2) / 2 - 1
                val centerY = (barY1 + barY2) / 2
                drawExclamationIndicator(context, centerX, centerY, indicatorAlpha)
            }
        }
        
        // Draw Item Icon
        val iconX = barX + BAR_HEIGHT + 1
        val iconY = barY + (BAR_WIDTH - 16) / 2
        context.renderItem(displayedAbility, iconX, iconY)
    }
    
    private fun drawExclamationIndicator(context: GuiGraphics, cx: Int, cy: Int, alpha: Float) {
        val a = (alpha * 255f).toInt() shl 24
        val surroundingColor = 0x0000FF00 or a
        val exclamationColor = 0x00252326 or a
        val radius = 5
        
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, surroundingColor)
                    context.fill(cx + dx + 1, cy + dy, cx + dx + 2, cy + dy + 1, surroundingColor)
                }
            }
        }
        
        for (dy in -3..1) {
            context.fill(cx, cy + dy, cx + 2, cy + dy + 1, exclamationColor)
        }
        context.fill(cx, cy + 3, cx + 2, cy + 4, exclamationColor)
    }
    
    private fun buildStyledTitleText(text: String, textColor: Int): Component {
        if (text.isEmpty()) {
            return Component.literal("")
        }
        return Component.literal(text).withStyle(Style.EMPTY.withColor(textColor))
    }
    
    private fun playNotificationSound() {
        try {
            playSoundSettings(soundSettings())
        } catch (e: Exception) {
            Melinoe.logger.warn("Ability Cooldown: Failed to play sound: ${e.message}")
        }
    }
    
    private fun getCurrentPlayerAbility(): ItemStack {
        val player = mc.player ?: return ItemStack.EMPTY
        
        val mainHandStack = player.mainHandItem
        val offHandStack = player.offhandItem
        
        if (isAbility(mainHandStack)) {
            return mainHandStack
        }
        
        if (isAbility(offHandStack)) {
            return offHandStack
        }
        
        return ItemStack.EMPTY
    }
    
    private fun isAbility(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return itemId.endsWith("_hoe")
    }
    
    override fun onDisable() {
        super.onDisable()
        trackedAbility = ItemStack.EMPTY
        displayedAbility = ItemStack.EMPTY
        cooldownProgress = 0f
        previousCooldownProgress = 0f
        customTitle = null
        titleDisplayTicks = 0
        indicatorStartTime = 0L
    }
}