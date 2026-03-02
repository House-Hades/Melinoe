package me.melinoe.features.impl.visual.healthbar

import com.mojang.blaze3d.vertex.PoseStack
import me.melinoe.utils.addVec
import me.melinoe.utils.render.CustomRenderLayer
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles 3D world-space rendering of the health bar.
 */
class Renderer3D(private val mc: Minecraft) {
    
    /**
     * Render health bar in 3D world space
     */
    fun render(
        matrices: PoseStack,
        camera: Camera,
        player: Player,
        tickDelta: Float,
        healthPercentage: Float,
        healthBarColor: Int,
        barWidth: Double,
        barHeight: Double,
        yPosition: Double,
        showBarOnly: Boolean,
        healthDisplay: Int,
        textPosition: Int,
        healthText: String,
        textScale: Double,
        textColor: Int,
        textOutline: Boolean,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource
    ) {
        // Calculate player position with interpolation
        val playerPos = Vec3(
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.xOld, player.x),
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.yOld, player.y),
            net.minecraft.util.Mth.lerp(tickDelta.toDouble(), player.zOld, player.z)
        )
        
        // Calculate render position relative to camera
        val cameraPos = camera.position
        var renderPos = playerPos.subtract(cameraPos.x, cameraPos.y, cameraPos.z)
        
        // Apply Y position offset with pitch-based arc rotation for negative Y
        if (yPosition < 0) {
            val playerYaw = player.getYRot(tickDelta)
            val baseDistance = abs(yPosition)
            val cameraPitch = camera.xRot
            val pitchRatio = ((cameraPitch + 90f) / 180f).coerceIn(0f, 1f)
            val arcAngleRad = Math.toRadians((1f - pitchRatio) * 180.0)
            val horizontalDistance = baseDistance * cos(arcAngleRad)
            val verticalOffset = baseDistance * sin(arcAngleRad)
            val yawRad = Math.toRadians((playerYaw + 180).toDouble())
            val offsetX = -sin(yawRad) * horizontalDistance
            val offsetZ = cos(yawRad) * horizontalDistance
            renderPos = renderPos.addVec(offsetX, -verticalOffset, offsetZ)
        } else {
            renderPos = renderPos.addVec(y = yPosition)
        }
        
        matrices.pushPose()
        matrices.translate(renderPos.x, renderPos.y, renderPos.z)
        
        // Billboarding - always face the camera
        matrices.mulPose(org.joml.Quaternionf().rotationY(Math.toRadians((-camera.yRot + 180).toDouble()).toFloat()))
        matrices.mulPose(org.joml.Quaternionf().rotationX(Math.toRadians((-camera.xRot).toDouble()).toFloat()))
        
        val matrix = matrices.last().pose()
        
        // Render bar
        if (!showBarOnly) {
            drawSimpleHealthBar(
                matrix,
                bufferSource,
                barWidth.toFloat(),
                barHeight.toFloat(),
                healthPercentage,
                healthBarColor
            )
            bufferSource.endBatch()
        }
        
        // Draw health text
        if (healthDisplay != 3 && textPosition != 4 && healthText.isNotEmpty()) {
            drawHealthText(
                matrices,
                matrix,
                bufferSource,
                healthText,
                barWidth.toFloat(),
                barHeight.toFloat(),
                textScale.toFloat(),
                textColor,
                textOutline,
                textPosition
            )
        }
        
        matrices.popPose()
    }
    
    /**
     * Draw simple health bar with border and solid color fill
     */
    private fun drawSimpleHealthBar(
        matrix: org.joml.Matrix4f,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        width: Float,
        height: Float,
        healthPercentage: Float,
        healthBarColor: Int
    ) {
        val borderWidth = Constants.BORDER_WIDTH_3D
        val innerWidth = width - borderWidth * 2
        val innerHeight = height - borderWidth * 2
        val filledWidth = innerWidth * healthPercentage
        
        // Draw border
        drawRectCentered(matrix, bufferSource, width, height, Constants.BORDER_COLOR, 0.001f)
        bufferSource.endBatch()
        
        // Draw background
        drawRectCentered(matrix, bufferSource, innerWidth, innerHeight, Constants.BACKGROUND_COLOR, 0.002f)
        bufferSource.endBatch()
        
        // Draw health bar
        if (filledWidth > 0) {
            drawRectStart(matrix, bufferSource, filledWidth, innerWidth, innerHeight, healthBarColor, 0.003f)
            bufferSource.endBatch()
        }
    }
    
    /**
     * Draw a centered rectangle
     */
    private fun drawRectCentered(
        matrix: org.joml.Matrix4f,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        width: Float,
        height: Float,
        color: Int,
        z: Float
    ) {
        val buffer = bufferSource.getBuffer(CustomRenderLayer.QUADS)
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f
        
        buffer.addVertex(matrix, halfWidth, halfHeight, z).setColor(color)
        buffer.addVertex(matrix, -halfWidth, halfHeight, z).setColor(color)
        buffer.addVertex(matrix, -halfWidth, -halfHeight, z).setColor(color)
        buffer.addVertex(matrix, halfWidth, -halfHeight, z).setColor(color)
    }
    
    /**
     * Draw a rectangle starting from the left (for health bar fill)
     */
    private fun drawRectStart(
        matrix: org.joml.Matrix4f,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        width: Float,
        fullWidth: Float,
        fullHeight: Float,
        color: Int,
        z: Float
    ) {
        val buffer = bufferSource.getBuffer(CustomRenderLayer.QUADS)
        val halfWidth = fullWidth * 0.5f
        val halfHeight = fullHeight * 0.5f
        val left = -halfWidth
        val right = left + width
        
        buffer.addVertex(matrix, right, halfHeight, z).setColor(color)
        buffer.addVertex(matrix, left, halfHeight, z).setColor(color)
        buffer.addVertex(matrix, left, -halfHeight, z).setColor(color)
        buffer.addVertex(matrix, right, -halfHeight, z).setColor(color)
    }
    
    /**
     * Draw health text with optional outline
     */
    private fun drawHealthText(
        matrices: PoseStack,
        matrix: org.joml.Matrix4f,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        healthText: String,
        barWidth: Float,
        barHeight: Float,
        textScale: Float,
        textColor: Int,
        textOutline: Boolean,
        textPosition: Int
    ) {
        matrices.pushPose()
        matrices.scale(textScale, -textScale, textScale)
        
        val font = mc.font
        val scaledMatrix = matrices.last().pose()
        val textWidth = font.width(healthText)
        
        val borderWidth = Constants.BORDER_WIDTH_3D
        val innerWidth = barWidth - borderWidth * 2
        
        // Calculate X position
        val textX = when (textPosition) {
            0 -> -(innerWidth / (2 * textScale))
            1 -> -textWidth * 0.5f
            2 -> (innerWidth / (2 * textScale)) - textWidth
            3 -> -textWidth * 0.5f
            else -> -textWidth * 0.5f
        }
        
        // Calculate Y position
        val textY = if (textPosition == 3) {
            val barTopInTextSpace = (barHeight / 2f) / textScale
            -barTopInTextSpace - (font.lineHeight / 2f)
        } else {
            -font.lineHeight * 0.4f
        }
        
        // Draw outline
        if (textOutline) {
            val outlineColor = 0xFF000000.toInt()
            for (offsetX in -1..1) {
                for (offsetY in -1..1) {
                    if (offsetX == 0 && offsetY == 0) continue
                    font.drawInBatch(
                        healthText,
                        textX + offsetX,
                        textY + offsetY,
                        outlineColor,
                        false,
                        scaledMatrix,
                        bufferSource,
                        net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                        0,
                        net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
                    )
                }
            }
        }
        
        // Draw main text
        font.drawInBatch(
            healthText,
            textX,
            textY,
            textColor,
            false,
            scaledMatrix,
            bufferSource,
            net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0,
            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
        )
        
        bufferSource.endBatch()
        matrices.popPose()
    }
}
