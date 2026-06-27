package me.melinoe.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import me.melinoe.Melinoe.mc
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.sin

/**
 * Waypoint rendering utilities adapted from SBO-Kotlin
 * Provides high-quality 3D waypoint rendering with beacons, text, and lines
 */
data class WaypointData(
    val pos: Vec3,
    val distance: Double,
    val text: String,
    val displayText: String, // Precalculated so we don't do string logic repeatedly
    val color: FloatArray,
    val alpha: Float,
    val textAlpha: Float,
    val icon: Identifier?,
    val isFlashing: Boolean
)

object WaypointCache {
    private val iconCache = mutableMapOf<Identifier, Identifier>()
    private val truncatedTextCache = mutableMapOf<String, String>()
    
    fun getResolvedIcon(icon: Identifier): Identifier {
        return iconCache.getOrPut(icon) {
            var path = icon.path
            if (!path.startsWith("textures/")) path = "textures/$path"
            if (!path.endsWith(".png")) path = "$path.png"
            Identifier.fromNamespaceAndPath(icon.namespace, path)
        }
    }
    
    fun getTruncatedText(text: String): String {
        return truncatedTextCache.getOrPut(text) {
            val distanceIndex = text.indexOf(" §7[")
            if (distanceIndex != -1) {
                val beforeDistance = text.substring(0, distanceIndex)
                val distancePart = text.substring(distanceIndex)
                
                val timerIndex = beforeDistance.indexOf(" §6[")
                val beforeTimer = if (timerIndex != -1) beforeDistance.substring(0, timerIndex) else beforeDistance
                val timerPart = if (timerIndex != -1) beforeDistance.substring(timerIndex) else ""
                
                val firstBracketIndex = beforeTimer.indexOf(" [")
                if (firstBracketIndex != -1) {
                    val bossName = beforeTimer.substring(0, firstBracketIndex)
                    val playerPart = beforeTimer.substring(firstBracketIndex + 2, beforeTimer.length - 1)
                    
                    val truncatedBoss = bossName.substring(0, 3.coerceAtMost(bossName.length))
                    val truncatedPlayer = playerPart.substring(0, 3.coerceAtMost(playerPart.length))
                    
                    "$truncatedBoss [$truncatedPlayer]$timerPart$distancePart"
                } else {
                    val truncatedBoss = beforeTimer.substring(0, 3.coerceAtMost(beforeTimer.length))
                    "$truncatedBoss$timerPart$distancePart"
                }
            } else {
                text.substring(0, 3.coerceAtMost(text.length))
            }
        }
    }
}

object WaypointRenderer {
    
    /**
     * Renders all waypoints in grouped layers (Boxes -> Beams -> Lines -> Icons -> Text)
     * This prevents MultiBufferSource from constantly flushing to the GPU
     */
    fun renderAllBatched(
        context: LevelRenderContext,
        waypoints: List<WaypointData>,
        showBeam: Boolean = true,
        showLine: Boolean = false,
        showText: Boolean = true,
        textScale: Float = 0.7f,
        lineWidth: Float = 2.0f // Kept for API compatibility, though rendering engines enforce fixed widths now.
    ) {
        if (waypoints.isEmpty()) return
        
        val poseStack = context.poseStack()
        val bufferSource = context.bufferSource()
        val cameraObj = context.gameRenderer().mainCamera
        val cameraPos = cameraObj.position()
        
        val time = System.currentTimeMillis()
        
        // Pass 1: Filled Boxes (RenderType.debugQuads)
        if (showBeam) {
            val quadBuffer = bufferSource.getBuffer(RenderTypes.debugQuads())
            for (wp in waypoints) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderFilledBox(poseStack, quadBuffer, cameraPos, wp.pos, wp.color, wp.alpha * alphaMult)
            }
        }
        
        // Pass 2: Beacon Beams (RenderType.beaconBeam)
        if (showBeam) {
            val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val worldTime = mc.level?.gameTime ?: 0L
            val beamBuffer = bufferSource.getBuffer(RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true))
            
            for (wp in waypoints) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderBeaconBeamBatched(poseStack, beamBuffer, cameraPos, partialTicks, worldTime, wp, wp.alpha * alphaMult)
            }
        }
        
        // Pass 3: Lines (RenderType.lines)
        if (showLine) {
            val lineBuffer = bufferSource.getBuffer(RenderTypes.lines())
            
            for (wp in waypoints) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderLineFromCamera(poseStack, lineBuffer, cameraObj, wp.pos, wp.color, wp.alpha * alphaMult)
            }
        }
        
        // Pre-group icons so we don't switch textures constantly
        val iconsMap = mutableMapOf<Identifier, MutableList<WaypointData>>()
        for (wp in waypoints) {
            if (wp.icon != null) {
                val resolvedIcon = WaypointCache.getResolvedIcon(wp.icon)
                iconsMap.getOrPut(resolvedIcon) { mutableListOf() }.add(wp)
            }
        }
        
        // Pass 4: Icons (See-through layer)
        for ((icon, wps) in iconsMap) {
            val seeThroughBuffer = bufferSource.getBuffer(RenderTypes.textSeeThrough(icon))
            for (wp in wps) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderIconLayer(poseStack, seeThroughBuffer, cameraObj, wp, wp.textAlpha * alphaMult * 0.5f, showText)
            }
        }
        
        // Pass 5: Icons (Solid layer)
        for ((icon, wps) in iconsMap) {
            val solidBuffer = bufferSource.getBuffer(RenderTypes.text(icon))
            for (wp in wps) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderIconLayer(poseStack, solidBuffer, cameraObj, wp, wp.textAlpha * alphaMult, showText)
            }
        }
        
        // Pass 6 & 7: Texts
        if (showText) {
            val font = mc.font
            // Draw all see-through text first so MultiBufferSource batches it perfectly
            for (wp in waypoints) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderTextLayerBatched(poseStack, bufferSource, cameraObj, font, wp, wp.textAlpha * alphaMult, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH)
            }
            // Draw all solid text
            for (wp in waypoints) {
                val alphaMult = if (wp.isFlashing) 0.625f + 0.375f * sin((time % 2000L) / 2000.0 * Math.PI * 2).toFloat() else 1.0f
                renderTextLayerBatched(poseStack, bufferSource, cameraObj, font, wp, wp.textAlpha * alphaMult, net.minecraft.client.gui.Font.DisplayMode.NORMAL)
            }
        }
        
        bufferSource.endBatch()
    }
    
    private fun renderFilledBox(poseStack: PoseStack, buffer: VertexConsumer, cameraPos: Vec3, pos: Vec3, color: FloatArray, alpha: Float) {
        poseStack.pushPose()
        poseStack.translate(pos.x + 0.5 - cameraPos.x, pos.y - cameraPos.y, pos.z + 0.5 - cameraPos.z)
        
        val matrix = poseStack.last().pose()
        val w = 1.0f / 2.0f
        
        drawBoxFacesQuads(buffer, matrix, -w, 0f, -w, w, 1f, w, color[0], color[1], color[2], alpha)
        poseStack.popPose()
    }
    
    private fun renderBeaconBeamBatched(
        poseStack: PoseStack, buffer: VertexConsumer, cameraPos: Vec3,
        partialTicks: Float, worldTime: Long, wp: WaypointData, alpha: Float
    ) {
        val fadeStart = 30.0
        val fadeEnd = 15.0
        if (wp.distance <= fadeEnd) return
        
        val distanceAlphaMult = Mth.clamp(((wp.distance - fadeEnd) / (fadeStart - fadeEnd)).toFloat(), 0f, 1f)
        val finalAlpha = alpha * distanceAlphaMult
        if (finalAlpha <= 0.01f) return
        
        val isDefender = wp.text.contains("Defender", ignoreCase = true)
        val heightLimit = mc.level?.maxY ?: 320
        val beamHeight = if (isDefender) 120 else max(0, heightLimit - wp.pos.y.toInt())
        if (beamHeight <= 0) return
        
        poseStack.pushPose()
        poseStack.translate(wp.pos.x - cameraPos.x, wp.pos.y + 1.0 - cameraPos.y, wp.pos.z - cameraPos.z)
        
        val innerRadius = 0.2f
        val outerRadius = 0.25f
        val time = Math.floorMod(worldTime, 40L) + partialTicks
        val fixedTime = -time
        val wavePhase = Mth.frac(fixedTime * 0.2f - Mth.floor(fixedTime * 0.1f).toFloat())
        val animationStep = -1f + wavePhase
        
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        
        // Inner layer
        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.25f - 45f))
        val renderYOffsetInner = beamHeight.toFloat() * (0.5f / innerRadius) + animationStep
        val innerColor = floatArrayOf(wp.color[0], wp.color[1], wp.color[2], finalAlpha)
        renderBeamLayer(poseStack, buffer, innerColor, 0f, innerRadius, innerRadius, 0f, -innerRadius, 0f, 0f, -innerRadius, renderYOffsetInner, animationStep, beamHeight)
        poseStack.popPose()
        
        // Outer layer
        val renderYOffsetOuter = beamHeight.toFloat() + animationStep
        val outerAlpha = 0.125f * finalAlpha
        val outerColor = floatArrayOf(wp.color[0], wp.color[1], wp.color[2], outerAlpha)
        renderBeamLayer(poseStack, buffer, outerColor, -outerRadius, -outerRadius, outerRadius, -outerRadius, -outerRadius, outerRadius, outerRadius, outerRadius, renderYOffsetOuter, animationStep, beamHeight)
        
        poseStack.popPose()
        poseStack.popPose()
    }
    
    private fun renderIconLayer(
        poseStack: PoseStack, buffer: VertexConsumer, camera: net.minecraft.client.Camera,
        wp: WaypointData, alpha: Float, showText: Boolean
    ) {
        val cameraPos = camera.position()
        val textWorldPos = Vec3(wp.pos.x + 0.5, wp.pos.y + 1.5, wp.pos.z + 0.5)
        
        val renderCoords = if (wp.distance > 32.0) {
            val diff = textWorldPos.subtract(cameraPos)
            cameraPos.add(diff.scale(32.0 / wp.distance))
        } else textWorldPos
        
        poseStack.pushPose()
        poseStack.translate(renderCoords.x - cameraPos.x, renderCoords.y - cameraPos.y, renderCoords.z - cameraPos.z)
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()))
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()))
        poseStack.scale(-0.1f, -0.1f, 0.1f)
        
        val iconSize = 20f
        val iconY = if (showText && wp.displayText.isNotEmpty()) -iconSize - 2f else -iconSize / 2f
        val iconX = -iconSize / 2f
        
        drawTextureQuad(buffer, poseStack.last().pose(), iconX, iconY, iconSize, alpha)
        poseStack.popPose()
    }
    
    private fun renderTextLayerBatched(
        poseStack: PoseStack, bufferSource: MultiBufferSource, camera: net.minecraft.client.Camera,
        font: net.minecraft.client.gui.Font, wp: WaypointData, alpha: Float, displayMode: net.minecraft.client.gui.Font.DisplayMode
    ) {
        if (wp.displayText.isEmpty()) return
        
        val cameraPos = camera.position()
        val textWorldPos = Vec3(wp.pos.x + 0.5, wp.pos.y + 1.5, wp.pos.z + 0.5)
        
        val renderCoords = if (wp.distance > 32.0) {
            val diff = textWorldPos.subtract(cameraPos)
            cameraPos.add(diff.scale(32.0 / wp.distance))
        } else textWorldPos
        
        poseStack.pushPose()
        poseStack.translate(renderCoords.x - cameraPos.x, renderCoords.y - cameraPos.y, renderCoords.z - cameraPos.z)
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()))
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()))
        poseStack.scale(-0.1f, -0.1f, 0.1f)
        
        val textWidth = font.width(wp.displayText)
        val xOffset = -textWidth / 2f
        
        val currentAlpha = if (displayMode == net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH) alpha * 0.5f else alpha
        val alphaInt = (currentAlpha * 255).toInt().coerceIn(0, 255)
        val baseColorInt = ((wp.color[0] * 255).toInt() shl 16) or ((wp.color[1] * 255).toInt() shl 8) or (wp.color[2] * 255).toInt()
        val textColor = baseColorInt or (alphaInt shl 24)
        
        font.drawInBatch(wp.displayText, xOffset, 0f, textColor, false, poseStack.last().pose(), bufferSource, displayMode, 0, 15728880)
        poseStack.popPose()
    }
    
    private fun renderLineFromCamera(
        poseStack: PoseStack, buffer: VertexConsumer, camera: net.minecraft.client.Camera, target: Vec3, color: FloatArray, alpha: Float
    ) {
        val cameraPos = camera.position()
        val startPos = cameraPos.add(Vec3.directionFromRotation(camera.xRot(), camera.yRot()))
        val endPos = target.add(0.5, 0.5, 0.5)
        
        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        val matrix = poseStack.last()
        
        buffer.addVertex(matrix, Vector3f(startPos.x.toFloat(), startPos.y.toFloat(), startPos.z.toFloat()))
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(matrix, Vector3f(0f, 1f, 0f))
        buffer.addVertex(matrix, Vector3f(endPos.x.toFloat(), endPos.y.toFloat(), endPos.z.toFloat()))
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(matrix, Vector3f(0f, 1f, 0f))
        poseStack.popPose()
    }
    
    private fun drawBoxFacesQuads(
        buffer: VertexConsumer, matrix: Matrix4f, minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float, r: Float, g: Float, b: Float, a: Float
    ) {
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a); buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a)
    }
    
    private fun drawTextureQuad(buffer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, size: Float, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        buffer.addVertex(matrix, x, y, 0f).setColor(255, 255, 255, a).setUv(0f, 0f).setLight(15728880)
        buffer.addVertex(matrix, x, y + size, 0f).setColor(255, 255, 255, a).setUv(0f, 1f).setLight(15728880)
        buffer.addVertex(matrix, x + size, y + size, 0f).setColor(255, 255, 255, a).setUv(1f, 1f).setLight(15728880)
        buffer.addVertex(matrix, x + size, y, 0f).setColor(255, 255, 255, a).setUv(1f, 0f).setLight(15728880)
    }
    
    /**
     * Render a single layer of the beacon beam
     */
    private fun renderBeamLayer(
        poseStack: PoseStack, buffer: VertexConsumer, color: FloatArray, x1: Float, z1: Float, x2: Float,
        z2: Float, x3: Float, z3: Float, x4: Float, z4: Float, v1: Float, v2: Float, beamHeight: Int
    ) {
        val entry = poseStack.last()
        renderBeamFace(entry, buffer, color, x1, z1, x2, z2, v1, v2, beamHeight)
        renderBeamFace(entry, buffer, color, x4, z4, x3, z3, v1, v2, beamHeight)
        renderBeamFace(entry, buffer, color, x2, z2, x4, z4, v1, v2, beamHeight)
        renderBeamFace(entry, buffer, color, x3, z3, x1, z1, v1, v2, beamHeight)
    }
    
    /**
     * Render a single face of the beacon beam
     */
    private fun renderBeamFace(
        entry: PoseStack.Pose, buffer: VertexConsumer, color: FloatArray, x1: Float, z1: Float,
        x2: Float, z2: Float, v1: Float, v2: Float, beamHeight: Int
    ) {
        renderBeamVertex(entry, buffer, color, beamHeight, x1, z1, 1f, v1)
        renderBeamVertex(entry, buffer, color, 0, x1, z1, 1f, v2)
        renderBeamVertex(entry, buffer, color, 0, x2, z2, 0f, v2)
        renderBeamVertex(entry, buffer, color, beamHeight, x2, z2, 0f, v1)
    }
    
    /**
     * Render a single vertex of the beacon beam
     */
    private fun renderBeamVertex(
        entry: PoseStack.Pose, buffer: VertexConsumer, color: FloatArray, y: Int,
        x: Float, z: Float, u: Float, v: Float
    ) {
        buffer.addVertex(entry, Vector3f(x, y.toFloat(), z))
            .setColor(color[0], color[1], color[2], if (color.size > 3) color[3] else 1f)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(entry, Vector3f(0f, 1f, 0f))
    }
}