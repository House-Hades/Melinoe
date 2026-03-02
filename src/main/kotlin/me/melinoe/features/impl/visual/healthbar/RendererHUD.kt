package me.melinoe.features.impl.visual.healthbar

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles HUD-based rendering with world-to-screen projection.
 */
class RendererHUD(private val mc: Minecraft) {
    // Cache for HUD rendering
    private var cachedScreenPos: Vec3? = null
    private var cachedHealthPercentage: Float = 1f
    private var cachedPlayer: Player? = null
    
    /**
     * Calculate and cache screen position for HUD rendering
     */
    fun calculateScreenPosition(
        player: Player,
        camera: Camera,
        tickDelta: Float,
        healthPercentage: Float,
        yPosition: Double,
        showInFirstPerson: Boolean
    ) {
        // In first person, use fixed position
        if (mc.options.cameraType.isFirstPerson && showInFirstPerson) {
            val window = mc.window
            val screenWidth = window.guiScaledWidth
            val screenHeight = window.guiScaledHeight
            cachedScreenPos = Vec3(screenWidth / 2.0, (screenHeight / 2.0) + 30.0, 0.0)
            cachedHealthPercentage = healthPercentage
            cachedPlayer = player
            return
        }
        
        // Calculate world position for third person
        val playerPos = Vec3(
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.xOld, player.x),
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.yOld, player.y),
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.zOld, player.z)
        )
        
        val targetWorldPos = if (yPosition >= 0) {
            playerPos.add(0.0, yPosition, 0.0)
        } else {
            playerPos
        }
        
        // Project to screen and cache
        cachedScreenPos = worldToScreen(targetWorldPos, camera, yPosition)
        cachedHealthPercentage = healthPercentage
        cachedPlayer = player
    }
    
    /**
     * Render cached health bar on HUD
     */
    fun renderHud(
        guiGraphics: GuiGraphics,
        healthBarColor: Int,
        barWidth: Double,
        barHeight: Double,
        showBarOnly: Boolean,
        healthDisplay: Int,
        textPosition: Int,
        healthText: String,
        textScale: Double,
        textColor: Int,
        textOutline: Boolean
    ) {
        val screenPos = cachedScreenPos ?: return
        val player = cachedPlayer ?: return
        
        val matrices = guiGraphics.pose()
        matrices.pushMatrix()
        matrices.translate(screenPos.x.toFloat(), screenPos.y.toFloat())
        matrices.scale(textScale.toFloat())
        
        val actualWidth = barWidth.toFloat()
        val actualHeight = barHeight.toFloat()
        
        // Render bar
        if (!showBarOnly) {
            drawSimpleHealthBarHUD(guiGraphics, actualWidth, actualHeight, cachedHealthPercentage, healthBarColor)
        }
        
        // Draw health text
        if (healthDisplay != 3 && textPosition != 4 && healthText.isNotEmpty()) {
            drawHealthTextHUD(guiGraphics, healthText, actualWidth, actualHeight, textColor, textOutline, textPosition)
        }
        
        matrices.popMatrix()
    }
    
    /**
     * Project world coordinates to screen coordinates
     */
    private fun worldToScreen(worldPos: Vec3, camera: Camera, yPosition: Double): Vec3? {
        val cameraPos = camera.position
        val relativeX = worldPos.x - cameraPos.x
        val relativeY = worldPos.y - cameraPos.y
        val relativeZ = worldPos.z - cameraPos.z
        
        val yaw = Math.toRadians(camera.yRot.toDouble())
        val pitch = Math.toRadians(camera.xRot.toDouble())
        
        // Transform to camera space
        val cosYaw = cos(-yaw)
        val sinYaw = sin(-yaw)
        val x1 = relativeX * cosYaw - relativeZ * sinYaw
        val z1 = relativeX * sinYaw + relativeZ * cosYaw
        
        val cosPitch = cos(-pitch)
        val sinPitch = sin(-pitch)
        val y2 = relativeY * cosPitch - z1 * sinPitch
        val z2 = relativeY * sinPitch + z1 * cosPitch
        
        if (z2 <= 0.1) return null
        
        // Project to screen
        val window = mc.window
        val fov = mc.options.fov().get().toDouble()
        val aspectRatio = window.guiScaledWidth.toDouble() / window.guiScaledHeight.toDouble()
        val fovRad = Math.toRadians(fov / 2.0)
        val scale = 1.0 / Math.tan(fovRad)
        
        val screenX = (x1 / z2) * scale / aspectRatio
        val screenY = (y2 / z2) * scale
        
        var finalX = (screenX + 1.0) * 0.5 * window.guiScaledWidth
        var finalY = (1.0 - screenY) * 0.5 * window.guiScaledHeight
        
        // Apply screen-space offset for negative Y
        if (yPosition < 0) {
            val cameraPitch = camera.xRot
            val pitchNormalized = cameraPitch / 90.0
            val baseOffset = -yPosition * (window.guiScaledHeight * 0.1)
            val pitchMultiplier = 0.9 + (pitchNormalized * 0.05) + (abs(pitchNormalized) * 0.25)
            finalY += baseOffset * pitchMultiplier
        }
        
        // Check if on screen
        if (finalX < -100 || finalX > window.guiScaledWidth + 100 || 
            finalY < -100 || finalY > window.guiScaledHeight + 100) {
            return null
        }
        
        return Vec3(finalX, finalY, 0.0)
    }
    
    /**
     * Draw health bar using HUD rendering
     */
    private fun drawSimpleHealthBarHUD(
        guiGraphics: GuiGraphics,
        width: Float,
        height: Float,
        healthPercentage: Float,
        healthBarColor: Int
    ) {
        val borderWidth = Constants.BORDER_WIDTH_HUD
        val innerWidth = width - borderWidth * 2
        val innerHeight = height - borderWidth * 2
        val filledWidth = innerWidth * healthPercentage
        
        val centerX = -width / 2
        val centerY = -height / 2
        
        // Draw border
        guiGraphics.fill(
            centerX.toInt(),
            centerY.toInt(),
            (centerX + width).toInt(),
            (centerY + height).toInt(),
            Constants.BORDER_COLOR
        )
        
        // Draw background
        guiGraphics.fill(
            (centerX + borderWidth).toInt(),
            (centerY + borderWidth).toInt(),
            (centerX + width - borderWidth).toInt(),
            (centerY + height - borderWidth).toInt(),
            Constants.BACKGROUND_COLOR
        )
        
        // Draw health fill
        if (filledWidth > 0) {
            guiGraphics.fill(
                (centerX + borderWidth).toInt(),
                (centerY + borderWidth).toInt(),
                (centerX + borderWidth + filledWidth).toInt(),
                (centerY + height - borderWidth).toInt(),
                healthBarColor
            )
        }
    }
    
    /**
     * Draw health text using HUD rendering
     */
    private fun drawHealthTextHUD(
        guiGraphics: GuiGraphics,
        healthText: String,
        barWidth: Float,
        barHeight: Float,
        textColor: Int,
        textOutline: Boolean,
        textPosition: Int
    ) {
        val font = mc.font
        val textWidth = font.width(healthText)
        
        val borderWidth = Constants.BORDER_WIDTH_HUD
        val innerWidth = barWidth - borderWidth * 2
        
        val textX = when (textPosition) {
            0 -> -innerWidth / 2
            1 -> -textWidth / 2f
            2 -> innerWidth / 2 - textWidth
            3 -> -textWidth / 2f
            else -> -textWidth / 2f
        }
        
        val textY = if (textPosition == 3) {
            val barTopInTextSpace = barHeight / 2
            -barTopInTextSpace - (font.lineHeight / 2f)
        } else {
            -font.lineHeight / 2f
        }
        
        // Draw outline
        if (textOutline) {
            val outlineColor = 0xFF000000.toInt()
            for (offsetX in -1..1) {
                for (offsetY in -1..1) {
                    if (offsetX == 0 && offsetY == 0) continue
                    guiGraphics.drawString(
                        font,
                        healthText,
                        (textX + offsetX).toInt(),
                        (textY + offsetY).toInt(),
                        outlineColor,
                        false
                    )
                }
            }
        }
        
        // Draw main text
        guiGraphics.drawString(
            font,
            healthText,
            textX.toInt(),
            textY.toInt(),
            textColor,
            false
        )
    }
    
    /**
     * Clear cached data
     */
    fun clearCache() {
        cachedScreenPos = null
        cachedPlayer = null
    }
}
