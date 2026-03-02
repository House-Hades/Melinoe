package me.melinoe.features.impl.combat

import me.melinoe.Melinoe
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.StringSetting
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
    private const val BAR_BG_COLOR = 0x80101010.toInt()
    private const val BAR_BORDER_COLOR = 0xA0C8C8C8.toInt()
    private const val BAR_LEFT_OFFSET = 22
    private const val EXPONENT = 0.65
    
    private const val COLOR_READY_R = 64
    private const val COLOR_READY_G = 204
    private const val COLOR_READY_B = 64
    private const val COLOR_COOLING_R = 204
    private const val COLOR_COOLING_G = 48
    private const val COLOR_COOLING_B = 48

    val titleText = registerSetting(StringSetting("Title Text", "Ability Ready!", desc = "Text to display in title popup"))
    val duration = registerSetting(NumberSetting("Duration", 60.0f, 10.0f, 100.0f, desc = "Duration of title display in ticks"))
    val titleColor = registerSetting(ColorSetting("Title Color", Color(0xFFFFFFFF.toInt()), desc = "Color of the title text"))
    
    private val titleHud by HUDSetting(
        name = "Title Display",
        x = 400,
        y = 200,
        scale = 2f,
        toggleable = true,
        description = "Position of the ability ready title popup. Toggle to show/hide.",
        module = this
    ) { example ->
        if (customTitle == null && !example) return@HUDSetting 0 to 0

        val title = if (example) {
            buildStyledTitleText(titleText.value, titleColor.value.rgba)
        } else {
            customTitle!!
        }
        val textRenderer = mc.font
        val textWidth = textRenderer.width(title)
        val textHeight = textRenderer.lineHeight

        drawString(textRenderer, title, 0, 0, titleColor.value.rgba, true)

        textWidth to textHeight
    }
    
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

            previousCooldownProgress = cooldownProgress
            cooldownProgress = player.cooldowns.getCooldownPercent(trackedAbility, 0f)

            val heldAbility = getCurrentPlayerAbility()

            if (heldAbility.isEmpty) {
                displayedAbility = heldAbility
                return@on
            }

            displayedAbility = heldAbility

            if (!ItemStack.matches(heldAbility, trackedAbility)) {
                trackedAbility = heldAbility.copy()
            }

            if (previousCooldownProgress > 0 && cooldownProgress == 0f && !trackedAbility.isEmpty) {
                indicatorStartTime = System.currentTimeMillis()
                
                if (titleHud.enabled) {
                    customTitle = buildStyledTitleText(titleText.value, titleColor.value.rgba)
                    titleDisplayTicks = duration.value.toInt()

                    if (playSound.enabled) {
                        playNotificationSound()
                    }
                }
            }
            
            if (previousCooldownProgress == 0f && cooldownProgress > 0f) {
                indicatorStartTime = 0L
                customTitle = null
                titleDisplayTicks = 0
            }

            if (titleDisplayTicks > 0) {
                titleDisplayTicks--
                if (titleDisplayTicks <= 0) {
                    customTitle = null
                }
            }
        }
    }

    fun renderHud(context: GuiGraphics) {
        if (!enabled) return
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
            
            context.hLine(barX1, barX2 - 1, barY1, BAR_BORDER_COLOR)
            context.hLine(barX1, barX2 - 1, barY2 - 1, BAR_BORDER_COLOR)
            context.vLine(barX1, barY1, barY2 - 1, BAR_BORDER_COLOR)
            context.vLine(barX2 - 1, barY1, barY2 - 1, BAR_BORDER_COLOR)
            
            val innerX1 = barX1 + 1
            val innerY1 = barY1 + 1
            val innerX2 = barX2 - 1
            val innerY2 = barY2 - 1
            
            context.fill(innerX1, innerY1, innerX2, innerY2, BAR_BG_COLOR)
            
            if (progress > 0.0f) {
                val remaining = (1.0f - progress).coerceIn(0f, 1f)
                val shaped = Math.pow(remaining.toDouble(), EXPONENT)
                val alpha = 255
                
                val tcol = remaining.toDouble()
                val r = Math.round(COLOR_READY_R * tcol + COLOR_COOLING_R * (1.0 - tcol)).toInt()
                val g = Math.round(COLOR_READY_G * tcol + COLOR_COOLING_G * (1.0 - tcol)).toInt()
                val b = Math.round(COLOR_READY_B * tcol + COLOR_COOLING_B * (1.0 - tcol)).toInt()
                
                val clampedR = r.coerceIn(0, 255)
                val clampedG = g.coerceIn(0, 255)
                val clampedB = b.coerceIn(0, 255)
                
                val barColor = (alpha shl 24) or (clampedR shl 16) or (clampedG shl 8) or clampedB
                
                val innerHeight = innerY2 - innerY1
                val barHeight = Math.max(0, Math.round(innerHeight * shaped).toInt())
                
                if (barHeight > 0) {
                    val barFillY = innerY2 - barHeight
                    context.fill(innerX1, barFillY, innerX2, innerY2, barColor)
                    
                    val edgeR = Math.min(255, Math.round(clampedR * 1.2).toInt())
                    val edgeG = Math.min(255, Math.round(clampedG * 1.2).toInt())
                    val edgeB = Math.min(255, Math.round(clampedB * 1.2).toInt())
                    val edgeColor = (alpha shl 24) or (edgeR shl 16) or (edgeG shl 8) or edgeB
                    
                    val edgeY = barFillY.coerceAtLeast(innerY1)
                    context.fill(innerX1, edgeY, innerX2, (barFillY + 1).coerceAtMost(innerY2), edgeColor)
                }
            } else {
                val alpha = 255
                val barColor = (alpha shl 24) or (COLOR_READY_R shl 16) or (COLOR_READY_G shl 8) or COLOR_READY_B
                
                context.fill(innerX1, innerY1, innerX2, innerY2, barColor)
                
                val edgeR = Math.min(255, Math.round(COLOR_READY_R * 1.2).toInt())
                val edgeG = Math.min(255, Math.round(COLOR_READY_G * 1.2).toInt())
                val edgeB = Math.min(255, Math.round(COLOR_READY_B * 1.2).toInt())
                val edgeColor = (alpha shl 24) or (edgeR shl 16) or (edgeG shl 8) or edgeB
                
                context.fill(innerX1, innerY1, innerX2, innerY1 + 1, edgeColor)
            }
        }
        
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
        
        val iconX = barX + BAR_HEIGHT + 1
        val iconY = barY + (BAR_WIDTH - 16) / 2
        context.renderItem(displayedAbility, iconX, iconY)
    }
    
    private fun drawExclamationIndicator(context: GuiGraphics, cx: Int, cy: Int, alpha: Float) {
        val a = (alpha * 255f).toInt() shl 24
        val vibrantGreen = 0x0000FF00 or a
        val white = 0x00FFFFFF or a
        val radius = 5
        
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, vibrantGreen)
                    context.fill(cx + dx + 1, cy + dy, cx + dx + 2, cy + dy + 1, vibrantGreen)
                }
            }
        }
        
        for (dy in -3..1) {
            context.fill(cx, cy + dy, cx + 2, cy + dy + 1, white)
        }
        context.fill(cx, cy + 3, cx + 2, cy + 4, white)
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
