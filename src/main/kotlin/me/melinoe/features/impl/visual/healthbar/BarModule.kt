package me.melinoe.features.impl.visual.healthbar

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.player.Player
import kotlin.math.roundToInt

/**
 * Unified Health Bar Module - Combines 3D world-space and HUD projection rendering.
 */
object HealthBarModule : Module(
    name = "Health Bar",
    category = Category.VISUAL,
    description = "Displays a health bar above the player with multiple rendering modes."
) {
    // Helper classes that handle the drawing and math
    private val healthState = HealthState()
    private val renderer3D = Renderer3D(mc)
    private val rendererHUD = RendererHUD(mc)
    
    // Main settings
    private val renderMode by SelectorSetting("Render Mode", "HUD Projection", arrayListOf("3D World", "HUD Projection"), desc = "Choose between 3D world-space or HUD-based rendering")
    private val renderStyle by SelectorSetting("Render Style", "Solid", arrayListOf("Gradient", "Solid", "Text Only"), desc = "Style of the health bar")
    
    // Extras for pretty code
    private val is3D get() = renderMode == 0
    private val isHUD get() = renderMode == 1
    private val isTextOnly get() = renderStyle == 2
    private val hasBars get() = renderStyle != 2
    private val needsDynamicColors get() = hasBars || !staticTextColor
    
    // Display & Positioning settings
    private val displayDropdown by DropdownSetting("Display & Positioning", false)
    private val healthDisplay by SelectorSetting("Health Format", "Numerical", arrayListOf("Numerical", "Decimals", "Percent", "None"), desc = "How to display health").withDependency { displayDropdown }
    private val showMaxHealth by BooleanSetting("Show Max Health", false, desc = "Show current/max health (e.g., 20 / 40)").withDependency { displayDropdown }
    private val textPosition by SelectorSetting("Text Position", "Center", arrayListOf("Left", "Center", "Right", "Above", "Off"), desc = "Position of health text").withDependency { displayDropdown && hasBars }
    private val yPosition by NumberSetting("Y Position Offset", -0.5, -3.0, 3.0, 0.1, desc = "Vertical position offset").withDependency { displayDropdown }
    private val showInFirstPerson by BooleanSetting("Show in First Person", false, desc = "Display health bar in first person view").withDependency { displayDropdown && isHUD }
    
    // Text Customization settings
    private val textDropdown by DropdownSetting("Text Customization", false)
    private val staticTextColor by BooleanSetting("Static Text Color", true, desc = "Use fixed color, or dynamic health colors").withDependency { textDropdown && isTextOnly }
    private val textColor by ColorSetting("Text Color", Color(0xFFFFFFFF.toInt()), true, desc = "Color of the health text").withDependency { textDropdown && (hasBars || staticTextColor) }
    private val textOutline by SelectorSetting("Text Style", "Outline", arrayListOf("None", "Shadow", "Outline"), desc = "Text rendering style").withDependency { textDropdown }
    private val textScale3D by NumberSetting("Text Scale (3D)", 0.02, 0.01, 0.2, 0.01, desc = "Scale of the health text").withDependency { textDropdown && is3D }
    private val textScaleHUD by NumberSetting("Text Scale (HUD)", 1.0, 0.5, 3.0, 0.1, desc = "Scale of the health text").withDependency { textDropdown && isHUD }
    
    // Colors & Animations settings
    private val colorDropdown by DropdownSetting("Colors & Animations", false).withDependency { needsDynamicColors }
    private val smoothHealth by BooleanSetting("Smooth Interpolation", true, desc = "Smoothly animate health changes").withDependency { colorDropdown && needsDynamicColors }
    private val damageFlash by BooleanSetting("Damage Flash", true, desc = "Flash custom color when taking damage").withDependency { colorDropdown && needsDynamicColors }
    private val damageFlashColor by ColorSetting("Damage Flash Color", Color(0xAAFF0000.toInt()), true, desc = "Color of the damage flash").withDependency { colorDropdown && damageFlash && needsDynamicColors }
    private val highHealthColor by ColorSetting("High HP Color", Color(0xFF00FF00.toInt()), true, desc = "Color when health is above mid threshold").withDependency { colorDropdown && needsDynamicColors }
    private val midHealthColor by ColorSetting("Mid HP Color", Color(0xFFFFFF00.toInt()), true, desc = "Color when health is below mid threshold").withDependency { colorDropdown && needsDynamicColors }
    private val lowHealthColor by ColorSetting("Low HP Color", Color(0xFFFF0000.toInt()), true, desc = "Color when health is below low threshold").withDependency { colorDropdown && needsDynamicColors }
    private val backgroundColor by ColorSetting("Background Color", Color(0xAA888888.toInt()), true, desc = "Color of the empty bar background").withDependency { colorDropdown && hasBars }
    private val borderColor by ColorSetting("Border Color", Color(0xCC222222.toInt()), true, desc = "Color of the health bar border").withDependency { colorDropdown && hasBars }
    private val midColorThreshold by NumberSetting("Mid HP Threshold %", 50.0, 0.0, 100.0, 1.0, desc = "Health percentage to switch to mid color").withDependency { colorDropdown && needsDynamicColors }
    private val lowColorThreshold by NumberSetting("Low HP Threshold %", 30.0, 0.0, 100.0, 1.0, desc = "Health percentage to switch to low color").withDependency { colorDropdown && needsDynamicColors }
    
    // Bar Dimension settings
    private val dimensionDropdown by DropdownSetting("Bar Dimensions", false).withDependency { hasBars }
    private val barWidth3D by NumberSetting("Bar Width (3D)", 1.5, 0.1, 5.0, 0.1, desc = "Width of the 3D health bar").withDependency { dimensionDropdown && is3D && hasBars }
    private val barHeight3D by NumberSetting("Bar Height (3D)", 0.3, 0.01, 1.0, 0.01, desc = "Height of the 3D health bar").withDependency { dimensionDropdown && is3D && hasBars }
    private val barWidthHUD by NumberSetting("Bar Width (HUD)", 80.0, 20.0, 200.0, 5.0, desc = "Width of the HUD health bar").withDependency { dimensionDropdown && isHUD && hasBars }
    private val barHeightHUD by NumberSetting("Bar Height (HUD)", 15.0, 4.0, 40.0, 1.0, desc = "Height of the HUD health bar").withDependency { dimensionDropdown && isHUD && hasBars }
    
    // Caches for performance
    private var cachedHealthText: String = ""
    private var lastHealth: Float = -1f
    private var lastMaxHealth: Float = -1f
    private var lastDisplaySettings: Int = -1
    
    init {
        on<RenderEvent.Last> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            
            if (mc.options.cameraType.isFirstPerson && (renderMode == 0 || !showInFirstPerson)) return@on
            
            val currentHealth = player.health
            val maxHealth = player.maxHealth
            val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(false) // Used for smooth animations
            
            // Updates the health state so the bar slides smoothly when taking damage
            healthState.initialize(currentHealth)
            healthState.checkDamage(currentHealth, damageFlash)
            healthState.updateHealth(currentHealth, smoothHealth)
            
            val healthPercentage = (healthState.displayedHealth / maxHealth).coerceIn(0f, 1f)
            
            // Figure out what colors to use based on HP and settings
            val baseBarColor = calculateBarColor(player, healthPercentage, tickDelta)
            val finalTextColor = if (renderStyle == 2 && !staticTextColor) baseBarColor else textColor.rgba
            
            // If gradient is on, make the top lighter and the bottom darker
            val barColorTop = if (renderStyle == 0) lighten(baseBarColor, 0.2f) else baseBarColor
            val barColorBottom = if (renderStyle == 0) darken(baseBarColor, 0.2f) else baseBarColor
            
            val healthText = getCachedHealthText(player, healthPercentage)
            
            // Force text to center if we are only showing text, otherwise use the setting
            val actualTextPosition = if (renderStyle == 2) 1 else textPosition
            
            if (renderMode == 0) {
                render3D(context, player, healthPercentage, barColorTop, barColorBottom, finalTextColor, actualTextPosition, healthText, tickDelta)
            } else {
                renderHUD(player, healthPercentage, tickDelta)
            }
        }
    }
    
    private fun render3D(context: WorldRenderContext, player: Player, pct: Float, topColor: Int, bottomColor: Int, finalTextColor: Int, finalPosition: Int, text: String, tickDelta: Float) {
        val matrices = context.matrices() ?: return
        val camera = context.gameRenderer().mainCamera ?: return
        val bufferSource = context.consumers() as? net.minecraft.client.renderer.MultiBufferSource.BufferSource ?: return
        
        renderer3D.render(
            matrices, camera, player, tickDelta, pct, topColor, bottomColor, backgroundColor.rgba, borderColor.rgba,
            barWidth3D, barHeight3D, yPosition, renderStyle,
            healthDisplay, finalPosition, text, textScale3D,
            finalTextColor, textOutline, bufferSource
        )
    }
    
    private fun renderHUD(player: Player, pct: Float, tickDelta: Float) {
        val camera = mc.gameRenderer.mainCamera
        rendererHUD.calculateScreenPosition(player, camera, tickDelta, pct, yPosition, showInFirstPerson)
    }
    
    // Draws the HUD element
    fun renderHud(guiGraphics: GuiGraphics) {
        if (!enabled || renderMode != 1) return
        val player = mc.player ?: return
        if (mc.options.cameraType.isFirstPerson && !showInFirstPerson) return
        
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val healthPercentage = (healthState.displayedHealth / player.maxHealth).coerceIn(0f, 1f)
        
        val baseBarColor = calculateBarColor(player, healthPercentage, tickDelta)
        val finalTextColor = if (renderStyle == 2 && !staticTextColor) baseBarColor else textColor.rgba
        
        val barColorTop = if (renderStyle == 0) lighten(baseBarColor, 0.2f) else baseBarColor
        val barColorBottom = if (renderStyle == 0) darken(baseBarColor, 0.2f) else baseBarColor
        
        val healthText = getCachedHealthText(player, healthPercentage)
        val actualTextPosition = if (renderStyle == 2) 1 else textPosition
        
        rendererHUD.renderHud(
            guiGraphics, healthPercentage, barColorTop, barColorBottom, backgroundColor.rgba, borderColor.rgba, barWidthHUD, barHeightHUD,
            renderStyle, healthDisplay, actualTextPosition, healthText,
            textScaleHUD, finalTextColor, textOutline
        )
    }
    
    private fun calculateBarColor(player: Player, pct: Float, tickDelta: Float): Int {
        val percentHundred = pct * 100f
        val mid = midColorThreshold.toFloat()
        val low = lowColorThreshold.toFloat()
        
        // Blend the colors correctly if enabled, otherwise instantly snap
        val baseColor = if (smoothHealth) {
            when {
                percentHundred >= mid -> {
                    val range = 100f - mid
                    val ratio = if (range > 0f) (percentHundred - mid) / range else 1f
                    blendColors(midHealthColor.rgba, highHealthColor.rgba, ratio.coerceIn(0f, 1f))
                }
                percentHundred >= low -> {
                    val range = mid - low
                    val ratio = if (range > 0f) (percentHundred - low) / range else 1f
                    blendColors(lowHealthColor.rgba, midHealthColor.rgba, ratio.coerceIn(0f, 1f))
                }
                else -> {
                    lowHealthColor.rgba
                }
            }
        } else {
            when {
                percentHundred <= low -> lowHealthColor.rgba
                percentHundred <= mid -> midHealthColor.rgba
                else -> highHealthColor.rgba
            }
        }
        
        // Return normal color if not taking damage
        if (!damageFlash || player.hurtTime <= 0) return baseColor
        
        // Calculate how intense the damage flash should be
        val flashIntensity = ((player.hurtTime.toFloat() - tickDelta) / 10f).coerceIn(0f, 1f)
        
        // Blend the normal color with the damage flash color
        return blendColors(baseColor, damageFlashColor.rgba, flashIntensity)
    }
    
    // Mixes two colors together
    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val inverseRatio = 1.0f - ratio
        val a = (((base shr 24 and 0xFF) * inverseRatio) + ((overlay shr 24 and 0xFF) * ratio)).toInt()
        val r = (((base shr 16 and 0xFF) * inverseRatio) + ((overlay shr 16 and 0xFF) * ratio)).toInt()
        val g = (((base shr 8 and 0xFF) * inverseRatio) + ((overlay shr 8 and 0xFF) * ratio)).toInt()
        val b = (((base and 0xFF) * inverseRatio) + ((overlay and 0xFF) * ratio)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    // Mixes a color with white to make it lighter
    private fun lighten(color: Int, fraction: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val newR = r + ((255 - r) * fraction).toInt()
        val newG = g + ((255 - g) * fraction).toInt()
        val newB = b + ((255 - b) * fraction).toInt()
        return (a shl 24) or (newR shl 16) or (newG shl 8) or newB
    }
    
    // Mixes a color with black to make it darker
    private fun darken(color: Int, fraction: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val newR = (r * (1f - fraction)).toInt()
        val newG = (g * (1f - fraction)).toInt()
        val newB = (b * (1f - fraction)).toInt()
        return (a shl 24) or (newR shl 16) or (g shl 8) or newB
    }
    
    // Formats the health text, only recalculates if the health or settings changed
    private fun getCachedHealthText(player: Player, healthPercentage: Float): String {
        // Create a unique number representing the current display settings
        val currentDisplaySettings = healthDisplay.hashCode() * 31 + showMaxHealth.hashCode()
        
        // If nothing changed since the last frame, return the saved text
        if (player.health == lastHealth && player.maxHealth == lastMaxHealth && lastDisplaySettings == currentDisplaySettings) {
            return cachedHealthText
        }
        
        // Format the current health
        val baseText = when (healthDisplay) {
            0 -> player.health.roundToInt().toString()
            1 -> ((player.health * 10f).roundToInt() / 10f).toString()
            2 -> "${(healthPercentage * 100f).roundToInt()}%"
            else -> ""
        }
        
        // Add max health if the setting is turned on
        cachedHealthText = if (showMaxHealth && baseText.isNotEmpty()) {
            val maxText = when (healthDisplay) {
                0 -> player.maxHealth.roundToInt().toString()
                1 -> ((player.maxHealth * 10f).roundToInt() / 10f).toString()
                2 -> "100%"
                else -> ""
            }
            "$baseText / $maxText"
        } else {
            baseText
        }
        
        // Save the new values for the next frame
        lastHealth = player.health
        lastMaxHealth = player.maxHealth
        lastDisplaySettings = currentDisplaySettings
        
        return cachedHealthText
    }
    
    override fun onDisable() {
        super.onDisable()
        healthState.reset()
        rendererHUD.clearCache()
        lastHealth = -1f
        lastMaxHealth = -1f
    }
}