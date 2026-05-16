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
import me.melinoe.utils.emoji.EmojiReplacer
import me.melinoe.utils.playSoundSettings
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Weapon Cooldown Module - visual cooldown bar with title popups.
 */
object WeaponCooldownModule : Module(
    name = "Weapon Cooldown",
    category = Category.COMBAT,
    description = "Displays weapon cooldown with visual notifications."
) {
    
    private const val BAR_WIDTH = 16
    private const val BAR_HEIGHT = 5
    private const val BAR_BG_COLOR = 0xC0404040.toInt()
    private const val BAR_BORDER_COLOR = 0xFF252326.toInt()
    private const val BAR_READY_COLOR = 0xFF02963D.toInt()
    private const val BAR_LEFT_OFFSET = 22
    private const val INDICATOR_FADE_MS = 250L
    
    // Color Interpolation Constants
    private const val RED_R = 139f
    private const val RED_G = 0f
    private const val RED_B = 0f
    private const val ORG_R = 250f
    private const val ORG_G = 129f
    private const val ORG_B = 42f
    
    val showHud = registerSetting(BooleanSetting("Show HUD", true, desc = "Toggle the visual cooldown bar and icon"))
    
    private val titleHud by HUDSetting(
        name = "Title Display",
        x = 400,
        y = 200,
        scale = 2f,
        toggleable = true,
        default = true,
        description = "Position of the weapon ready title popup. Toggle to show/hide.",
        module = this
    ) { example ->
        val currentTitle = customTitle
        if (currentTitle == null && !example) return@HUDSetting 0 to 0
        
        val title = if (example) {
            buildStyledTitleText(titleText, titleColor.rgba)
        } else {
            currentTitle!!
        }
        
        val textRenderer = mc.font
        val textWidth = textRenderer.width(title)
        val textHeight = textRenderer.lineHeight
        
        text(textRenderer, title, 0, 0, titleColor.rgba, true)
        
        textWidth to textHeight
    }
    
    val titleText: String by StringSetting("Title Text", "Weapon Ready!", desc = "Text to display in title popup").withDependency { titleHud.enabled }
    val duration: Float by NumberSetting("Duration", 60.0f, 10.0f, 100.0f, desc = "Duration of title display in ticks").withDependency { titleHud.enabled }
    val titleColor: Color by ColorSetting("Title Color", Color(0xFF7CFFB2.toInt()), desc = "Color of the title text").withDependency { titleHud.enabled }
    
    val playSound = registerSetting(BooleanSetting("Play Sound", true, desc = "Play sound when weapon is ready"))
    private val soundSettings = createSoundSettings(
        name = "Sound",
        default = "block.note_block.banjo",
        dependencies = { playSound.value }
    )
    
    // State Variables
    private var trackedWeapon = ItemStack.EMPTY
    private var displayedWeapon = ItemStack.EMPTY
    private var cooldownProgress = 0f
    private var previousCooldownProgress = 0f
    private var indicatorStartTime = 0L
    private var customTitle: Component? = null
    private var titleDisplayTicks = 0
    
    // Caches to prevent registry lookups & string allocations every tick
    private val weaponItemCache = IdentityHashMap<Item, Boolean>()
    
    init {
        on<TickEvent.End> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            
            val heldWeapon = getCurrentPlayerWeapon(player)
            displayedWeapon = heldWeapon
            
            if (!heldWeapon.isEmpty && !ItemStack.isSameItemSameComponents(heldWeapon, trackedWeapon)) {
                trackedWeapon = heldWeapon.copy()
            }
            
            // Track cooldown even if we switched items
            if (!trackedWeapon.isEmpty) {
                previousCooldownProgress = cooldownProgress
                cooldownProgress = player.cooldowns.getCooldownPercent(trackedWeapon, 0f)
                
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
        // Linear interpolation using constants
        val r = (ORG_R + (RED_R - ORG_R) * progress).toInt()
        val g = (ORG_G + (RED_G - ORG_G) * progress).toInt()
        val b = (ORG_B + (RED_B - ORG_B) * progress).toInt()
        
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    fun renderHud(context: GuiGraphicsExtractor) {
        if (!enabled) return
        if (!showHud.value) return
        if (displayedWeapon.isEmpty) return
        
        val window = mc.window
        // Mirrored to the left side
        val barX = (window.guiScaledWidth / 2) - BAR_LEFT_OFFSET - BAR_HEIGHT
        val barY = (window.guiScaledHeight / 2) - (BAR_WIDTH / 2)
        val progress = cooldownProgress.coerceIn(0f, 1f)
        
        val currentTime = System.currentTimeMillis()
        val timeSinceIndicator = currentTime - indicatorStartTime
        val indicatorActive = timeSinceIndicator < INDICATOR_FADE_MS && indicatorStartTime > 0
        
        if (progress > 0.0f || (progress == 0.0f && !indicatorActive && indicatorStartTime > 0) || (progress == 0.0f && indicatorStartTime == 0L)) {
            val barX2 = barX + BAR_HEIGHT
            val barY2 = barY + BAR_WIDTH
            
            // Draw outline
            context.horizontalLine(barX, barX2 - 1, barY, BAR_BORDER_COLOR)
            context.horizontalLine(barX, barX2 - 1, barY2 - 1, BAR_BORDER_COLOR)
            context.verticalLine(barX, barY, barY2 - 1, BAR_BORDER_COLOR)
            context.verticalLine(barX2 - 1, barY, barY2 - 1, BAR_BORDER_COLOR)
            
            val innerX1 = barX + 1
            val innerY1 = barY + 1
            val innerX2 = barX2 - 1
            val innerY2 = barY2 - 1
            
            // Draw background
            context.fill(innerX1, innerY1, innerX2, innerY2, BAR_BG_COLOR)
            
            if (progress > 0.0f) {
                val innerHeight = innerY2 - innerY1
                val emptySpace = ceil(innerHeight * progress).toInt()
                val barFillY = innerY1 + emptySpace
                
                if (barFillY < innerY2) {
                    context.fill(innerX1, barFillY, innerX2, innerY2, getCooldownColor(progress))
                }
            } else {
                // Solid green when ready
                context.fill(innerX1, innerY1, innerX2, innerY2, BAR_READY_COLOR)
            }
        }
        
        // Draw indicator
        if (indicatorActive) {
            val indicatorAlpha = 1.0f - (timeSinceIndicator.toFloat() / INDICATOR_FADE_MS)
            if (indicatorAlpha > 0.0f) {
                val centerX = barX + (BAR_HEIGHT / 2) - 1
                val centerY = barY + (BAR_WIDTH / 2)
                drawExclamationIndicator(context, centerX, centerY, indicatorAlpha)
            }
        }
        
        // Draw Item Icon (mirrored to the left of the bar)
        val iconX = barX - 16 - 1
        val iconY = barY + (BAR_WIDTH - 16) / 2
        context.item(displayedWeapon, iconX, iconY)
    }
    
    private fun drawExclamationIndicator(context: GuiGraphicsExtractor, cx: Int, cy: Int, alpha: Float) {
        val a = (alpha * 255f).toInt() shl 24
        val surroundingColor = 0x0000FF00 or a
        val exclamationColor = 0x00252326 or a
        val radius = 5
        
        for (dy in -radius..radius) {
            val span = sqrt((radius * radius - dy * dy).toDouble()).toInt()
            context.fill(cx - span, cy + dy, cx + span + 2, cy + dy + 1, surroundingColor)
        }
        
        // Draw Exclamation Mark
        context.fill(cx, cy - 3, cx + 2, cy + 2, exclamationColor)
        context.fill(cx, cy + 3, cx + 2, cy + 4, exclamationColor)
    }
    
    private fun buildStyledTitleText(text: String, textColor: Int): Component {
        if (text.isEmpty()) return Component.literal("")
        return EmojiReplacer.replaceIn(Component.literal(text).withStyle(Style.EMPTY.withColor(textColor)))
    }
    
    private fun playNotificationSound() {
        try {
            playSoundSettings(soundSettings())
        } catch (e: Exception) {
            Melinoe.logger.warn("Weapon Cooldown: Failed to play sound: ${e.message}")
        }
    }
    
    private fun getCurrentPlayerWeapon(player: LocalPlayer): ItemStack {
        val mainHandStack = player.mainHandItem
        if (isWeapon(mainHandStack)) return mainHandStack
        
        return ItemStack.EMPTY
    }
    
    private fun isWeapon(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        
        return weaponItemCache.getOrPut(item) {
            BuiltInRegistries.ITEM.getKey(item).path.endsWith("_shovel")
        }
    }
    
    override fun onDisable() {
        super.onDisable()
        trackedWeapon = ItemStack.EMPTY
        displayedWeapon = ItemStack.EMPTY
        cooldownProgress = 0f
        previousCooldownProgress = 0f
        customTitle = null
        titleDisplayTicks = 0
        indicatorStartTime = 0L
    }
}