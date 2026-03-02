package me.melinoe.utils.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.melinoe.Melinoe.mc
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.max

/**
 * Waypoint rendering utilities adapted from SBO-Kotlin
 * Provides high-quality 3D waypoint rendering with beacons, text, and lines
 */
object WaypointRenderer {
    
    /**
     * Render a complete waypoint with box, text, line, and beacon
     */
    fun renderWaypoint(
        context: WorldRenderContext,
        pos: Vec3,
        text: String,
        color: FloatArray,
        alpha: Float = 0.5f,
        showBeam: Boolean = true,
        showLine: Boolean = false,
        showText: Boolean = true,
        textShadow: Boolean = true,
        textScale: Float = 0.7f,
        maxTextScale: Double = 1.0,
        lineWidth: Float = 2.0f,
        textAlpha: Float = 1.0f,
        playerName: String? = null // Optional player name for truncation logic
    ) {
        val poseStack = context.matrices() ?: return
        val bufferSource = context.consumers() ?: return
        val camera = context.gameRenderer().mainCamera?.position ?: return
        
        // Render filled box at waypoint location
        renderFilledBox(poseStack, bufferSource, camera, pos, color, alpha)
        
        // Render beacon beam
        if (showBeam) {
            renderBeaconBeam(context, pos, color, alpha)
        }
        
        // Render line from camera to waypoint
        if (showLine) {
            renderLineFromCamera(poseStack, bufferSource, camera, pos, color, lineWidth, alpha)
        }
        
        // Render text label
        if (showText && text.isNotEmpty()) {
            renderText(context, pos, text, color, textShadow, textScale.toDouble(), maxTextScale, textAlpha, playerName)
        }
    }
    
    /**
     * Render a filled box at the waypoint location
     */
    private fun renderFilledBox(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPos: Vec3,
        pos: Vec3,
        color: FloatArray,
        alpha: Float
    ) {
        poseStack.pushPose()
        poseStack.translate(
            pos.x + 0.5 - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z + 0.5 - cameraPos.z
        )
        
        // Use QUADS render type for filled box
        val buffer = bufferSource.getBuffer(RenderType.debugQuads())
        val matrix = poseStack.last().pose()
        
        val width = 1.0
        val height = 1.0
        val depth = 1.0
        
        val minX = (-width / 2.0).toFloat()
        val minZ = (-depth / 2.0).toFloat()
        val maxX = (width / 2.0).toFloat()
        val maxZ = (depth / 2.0).toFloat()
        
        val minY = 0f
        val maxY = height.toFloat()
        
        // Draw filled box faces using QUADS
        drawBoxFacesQuads(buffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, color[0], color[1], color[2], alpha)
        
        poseStack.popPose()
    }
    
    /**
     * Draw all faces of a box using QUADS mode
     */
    private fun drawBoxFacesQuads(
        buffer: VertexConsumer,
        matrix: Matrix4f,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        // Bottom face (Y-)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a)
        
        // Top face (Y+)
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a)
        
        // North face (Z-)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a)
        
        // South face (Z+)
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a)
        
        // West face (X-)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a)
        
        // East face (X+)
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a)
    }
    
    /**
     * Render a beacon beam at the waypoint - adapted from SBO
     */
    private fun renderBeaconBeam(
        context: WorldRenderContext,
        pos: Vec3,
        color: FloatArray,
        alpha: Float = 1.0f
    ) {
        val player = mc.player ?: return
        val distance = player.position().distanceTo(pos)
        if (distance < 20.0) return // Don't render beam if within 20 blocks (boss arena range)
        
        val poseStack = context.matrices() ?: return
        val bufferSource = context.consumers() ?: return
        val camera = context.gameRenderer().mainCamera?.position ?: return
        
        poseStack.pushPose()
        poseStack.translate(
            pos.x - camera.x,
            pos.y + 1.0 - camera.y,
            pos.z - camera.z
        )
        
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val worldTime = mc.level?.gameTime ?: 0L
        
        renderBeam(poseStack, bufferSource, partialTicks, worldTime, color, alpha)
        
        poseStack.popPose()
    }
    
    /**
     * Render the actual beacon beam geometry - adapted from SBO
     */
    private fun renderBeam(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float,
        worldTime: Long,
        color: FloatArray,
        alpha: Float = 1.0f
    ) {
        val height = 320
        val innerRadius = 0.2f
        val outerRadius = 0.25f
        
        val time = Math.floorMod(worldTime, 40L) + partialTicks
        val fixedTime = -time
        val wavePhase = Mth.frac(fixedTime * 0.2f - Mth.floor(fixedTime * 0.1f).toFloat())
        val animationStep = -1f + wavePhase
        var renderYOffset = height.toFloat() * (0.5f / innerRadius) + animationStep
        
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        
        // Inner rotating beam
        poseStack.pushPose()
        poseStack.mulPose(org.joml.Quaternionf().rotationY(Math.toRadians((time * 2.25f - 45f).toDouble()).toFloat()))
        
        val buffer = bufferSource.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false))
        val innerColor = floatArrayOf(color[0], color[1], color[2], alpha)
        renderBeamLayer(
            poseStack, buffer, innerColor,
            0f, innerRadius, innerRadius,
            0f, -innerRadius, 0f,
            0f, -innerRadius,
            renderYOffset, animationStep
        )
        poseStack.popPose()
        
        // Outer translucent beam
        renderYOffset = height.toFloat() + animationStep
        val translucentBuffer = bufferSource.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true))
        val outerAlpha = 0.125f * alpha // 32/255 * fade alpha
        val outerColor = floatArrayOf(color[0], color[1], color[2], outerAlpha)
        renderBeamLayer(
            poseStack, translucentBuffer, outerColor,
            -outerRadius, -outerRadius, outerRadius,
            -outerRadius, -outerRadius, outerRadius,
            outerRadius, outerRadius,
            renderYOffset, animationStep
        )
        
        poseStack.popPose()
    }
    
    /**
     * Render a single layer of the beacon beam
     */
    private fun renderBeamLayer(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        color: FloatArray,
        x1: Float, z1: Float, x2: Float,
        z2: Float, x3: Float, z3: Float,
        x4: Float, z4: Float,
        v1: Float, v2: Float
    ) {
        val entry = poseStack.last()
        renderBeamFace(entry, buffer, color, x1, z1, x2, z2, v1, v2)
        renderBeamFace(entry, buffer, color, x4, z4, x3, z3, v1, v2)
        renderBeamFace(entry, buffer, color, x2, z2, x4, z4, v1, v2)
        renderBeamFace(entry, buffer, color, x3, z3, x1, z1, v1, v2)
    }
    
    /**
     * Render a single face of the beacon beam
     */
    private fun renderBeamFace(
        entry: PoseStack.Pose,
        buffer: VertexConsumer,
        color: FloatArray,
        x1: Float, z1: Float,
        x2: Float, z2: Float,
        v1: Float, v2: Float
    ) {
        renderBeamVertex(entry, buffer, color, 320, x1, z1, 1f, v1)
        renderBeamVertex(entry, buffer, color, 0, x1, z1, 1f, v2)
        renderBeamVertex(entry, buffer, color, 0, x2, z2, 0f, v2)
        renderBeamVertex(entry, buffer, color, 320, x2, z2, 0f, v1)
    }
    
    /**
     * Render a single vertex of the beacon beam
     */
    private fun renderBeamVertex(
        entry: PoseStack.Pose,
        buffer: VertexConsumer,
        color: FloatArray,
        y: Int,
        x: Float, z: Float,
        u: Float, v: Float
    ) {
        buffer.addVertex(entry.pose(), x, y.toFloat(), z)
            .setColor(color[0], color[1], color[2], if (color.size > 3) color[3] else 1f)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(entry, 0f, 1f, 0f)
    }
    
    /**
     * Render a line from the camera to the waypoint
     */
    private fun renderLineFromCamera(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPos: Vec3,
        target: Vec3,
        color: FloatArray,
        lineWidth: Float,
        alpha: Float
    ) {
        val camera = mc.gameRenderer.mainCamera
        val startPos = cameraPos.add(Vec3.directionFromRotation(camera.xRot, camera.yRot))
        val endPos = target.add(0.5, 0.5, 0.5)
        
        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        
        val prevLineWidth = RenderSystem.getShaderLineWidth()
        RenderSystem.lineWidth(lineWidth)
        
        val buffer = bufferSource.getBuffer(RenderType.lines())
        val matrix = poseStack.last()
        
        buffer.addVertex(matrix.pose(), startPos.x.toFloat(), startPos.y.toFloat(), startPos.z.toFloat())
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(matrix, 0f, 1f, 0f)
        
        buffer.addVertex(matrix.pose(), endPos.x.toFloat(), endPos.y.toFloat(), endPos.z.toFloat())
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(matrix, 0f, 1f, 0f)
        
        RenderSystem.lineWidth(prevLineWidth)
        poseStack.popPose()
    }
    
    /**
     * Render text at the waypoint that faces the camera - adapted from SBO
     * Text is truncated to 3 characters unless looking at/near the waypoint
     * Format: "BossName [PlayerName] [Distance]" or "BossName [Distance]"
     */
    private fun renderText(
        context: WorldRenderContext,
        pos: Vec3,
        text: String,
        color: FloatArray,
        shadow: Boolean,
        scale: Double,
        maxScale: Double,
        textAlpha: Float = 1.0f,
        playerName: String? = null
    ) {
        val poseStack = context.matrices() ?: return
        val bufferSource = context.consumers() ?: return
        val camera = context.gameRenderer().mainCamera ?: return
        val cameraPos = camera.position
        val font = mc.font
        
        val textWorldPos = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val distance = cameraPos.distanceTo(textWorldPos)
        
        // Check if player is looking at/near the waypoint
        // Calculate the angle between camera direction and direction to waypoint
        val cameraDirection = Vec3.directionFromRotation(camera.xRot, camera.yRot)
        val toWaypoint = textWorldPos.subtract(cameraPos).normalize()
        val dotProduct = cameraDirection.dot(toWaypoint)
        
        // Show full name if:
        // 1. Looking directly at it (dot product > 0.95, ~18 degree cone)
        // 2. Very close to it (within 10 blocks)
        val isLookingAt = dotProduct > 0.95
        val isNearby = distance < 10.0
        
        val displayText = if (isLookingAt || isNearby) {
            // Show full text when looking at or near waypoint
            text
        } else {
            // Parse and truncate text
            // Format: "BossName [PlayerName] §6[Timer] §7[Distance]" or "BossName §6[Timer] §7[Distance]" or "BossName [PlayerName] §7[Distance]" or "BossName §7[Distance]"
            
            // Find the distance part (starts with §7[)
            val distanceIndex = text.indexOf(" §7[")
            if (distanceIndex != -1) {
                val beforeDistance = text.substring(0, distanceIndex)
                val distancePart = text.substring(distanceIndex) // " §7[Distance]"
                
                // Check if there's a portal timer (§6[)
                val timerIndex = beforeDistance.indexOf(" §6[")
                val beforeTimer = if (timerIndex != -1) {
                    beforeDistance.substring(0, timerIndex)
                } else {
                    beforeDistance
                }
                val timerPart = if (timerIndex != -1) {
                    beforeDistance.substring(timerIndex) // " §6[Timer]"
                } else {
                    ""
                }
                
                // Check if there's a player name bracket in the beforeTimer part
                val firstBracketIndex = beforeTimer.indexOf(" [")
                if (firstBracketIndex != -1) {
                    // Has player name: "BossName [PlayerName]"
                    val bossName = beforeTimer.substring(0, firstBracketIndex)
                    val playerPart = beforeTimer.substring(firstBracketIndex + 2, beforeTimer.length - 1) // Skip " [" and remove "]"
                    
                    val truncatedBoss = bossName.substring(0, 3.coerceAtMost(bossName.length))
                    val truncatedPlayer = playerPart.substring(0, 3.coerceAtMost(playerPart.length))
                    
                    "$truncatedBoss [$truncatedPlayer]$timerPart$distancePart"
                } else {
                    // No player name, just boss: "BossName"
                    val truncatedBoss = beforeTimer.substring(0, 3.coerceAtMost(beforeTimer.length))
                    "$truncatedBoss$timerPart$distancePart"
                }
            } else {
                // Fallback: just truncate the whole text
                text.substring(0, 3.coerceAtMost(text.length))
            }
        }
        
        val dynamicScale = max(distance, 2.5) * scale * 0.01
        
        // Cap the scale at maxScale to prevent text from getting too large
        val finalScale = kotlin.math.min(dynamicScale, maxScale)
        
        poseStack.pushPose()
        poseStack.translate(
            pos.x + 0.5 - cameraPos.x,
            pos.y + 1.5 - cameraPos.y,
            pos.z + 0.5 - cameraPos.z
        )
        
        // Rotate to face camera
        poseStack.mulPose(Quaternionf().rotationY(Math.toRadians((-camera.yRot).toDouble()).toFloat()))
        poseStack.mulPose(Quaternionf().rotationX(Math.toRadians(camera.xRot.toDouble()).toFloat()))
        
        poseStack.scale(-finalScale.toFloat(), -finalScale.toFloat(), finalScale.toFloat())
        
        val textWidth = font.width(displayText)
        val xOffset = -textWidth / 2f
        
        // Apply alpha to text color
        val alphaInt = (textAlpha * 255).toInt().coerceIn(0, 255)
        val textColor = ((color[0] * 255).toInt() shl 16) or
                       ((color[1] * 255).toInt() shl 8) or
                       (color[2] * 255).toInt() or
                       (alphaInt shl 24)
        
        font.drawInBatch(
            displayText,
            xOffset,
            0f,
            textColor,
            shadow,
            poseStack.last().pose(),
            bufferSource,
            net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        )
        
        poseStack.popPose()
    }
}
