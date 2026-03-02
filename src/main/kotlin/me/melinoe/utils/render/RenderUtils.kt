package me.melinoe.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.melinoe.Melinoe
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.utils.Color
import me.melinoe.utils.component1
import me.melinoe.utils.component2
import me.melinoe.utils.component3
import me.melinoe.utils.unaryMinus
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

private const val DEPTH = 0
private const val NO_DEPTH = 1

internal data class LineData(val from: Vec3, val to: Vec3, val color1: Int, val color2: Int, val thickness: Float)
internal data class BoxData(val aabb: AABB, val r: Float, val g: Float, val b: Float, val a: Float, val thickness: Float)
internal data class TriangleStripPoint(val pos: Vec3, val color: Int)
internal data class TriangleStripData(val points: List<TriangleStripPoint>)
internal data class TextData(
    val text: String, 
    val pos: Vec3, 
    val scale: Float, 
    val depth: Boolean, 
    val cameraRotation: org.joml.Quaternionf, 
    val font: net.minecraft.client.gui.Font, 
    val textWidth: Float,
    val color: Int
)

class RenderConsumer {
    internal val lines: List<ObjectArrayList<LineData>> = listOf(ObjectArrayList(), ObjectArrayList())
    internal val wireBoxes: List<ObjectArrayList<BoxData>> = listOf(ObjectArrayList(), ObjectArrayList())
    internal val filledBoxes: List<ObjectArrayList<BoxData>> = listOf(ObjectArrayList(), ObjectArrayList())
    internal val triangleStrips: List<ObjectArrayList<TriangleStripData>> = listOf(ObjectArrayList(), ObjectArrayList())
    internal val texts: ObjectArrayList<TextData> = ObjectArrayList()

    fun clear() {
        lines.forEach { it.clear() }
        wireBoxes.forEach { it.clear() }
        filledBoxes.forEach { it.clear() }
        triangleStrips.forEach { it.clear() }
        texts.clear()
    }
}

object RenderBatchManager {
    val renderConsumer = RenderConsumer()

    init {
        on<RenderEvent.Last> {
            val matrix = context.matrices() ?: return@on
            val bufferSource = context.consumers() as? MultiBufferSource.BufferSource ?: return@on
            val camera = context.gameRenderer().mainCamera?.position ?: return@on

            matrix.pushPose()
            matrix.translate(-camera.x, -camera.y, -camera.z)

            matrix.renderBatchedLines(renderConsumer.lines, bufferSource)
            matrix.renderBatchedWireBoxes(renderConsumer.wireBoxes, bufferSource)
            matrix.renderBatchedFilledBoxes(renderConsumer.filledBoxes, bufferSource)
            matrix.renderBatchedTriangleStrips(renderConsumer.triangleStrips, bufferSource)
            matrix.renderBatchedTexts(renderConsumer.texts, bufferSource, camera)

            matrix.popPose()

            renderConsumer.clear()
        }
    }
}

private fun PoseStack.renderBatchedLines(
    lines: List<List<LineData>>,
    bufferSource: MultiBufferSource.BufferSource
) {
    val lineRenderLayers = listOf(CustomRenderLayer.LINE_LIST, CustomRenderLayer.LINE_LIST_ESP)
    val last = this.last()
    
    for (depthState in 0..1) {
        if (lines[depthState].isEmpty()) continue
        
        // Group lines by thickness for efficient rendering
        val linesByThickness = lines[depthState].groupBy { it.thickness }
        
        for ((thickness, thickLines) in linesByThickness) {
            // Set line width for this batch
            com.mojang.blaze3d.systems.RenderSystem.lineWidth(thickness)
            
            val buffer = bufferSource.getBuffer(lineRenderLayers[depthState])

            for (line in thickLines) {
                val (fromX, fromY, fromZ) = line.from
                val (toX, toY, toZ) = line.to
                val dirX = toX - fromX
                val dirY = toY - fromY
                val dirZ = toZ - fromZ

                PrimitiveRenderer.renderVector(
                    last, buffer,
                    Vector3f(fromX.toFloat(), fromY.toFloat(), fromZ.toFloat()),
                    Vec3(dirX, dirY, dirZ),
                    line.color1, line.color2
                )
            }

            bufferSource.endBatch(lineRenderLayers[depthState])
        }
        
        // Reset line width to default
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(1.0f)
    }
}

private fun PoseStack.renderBatchedWireBoxes(
    boxes: List<List<BoxData>>,
    bufferSource: MultiBufferSource.BufferSource
) {
    val lineRenderLayers = listOf(CustomRenderLayer.LINE_LIST, CustomRenderLayer.LINE_LIST_ESP)
    val last = this.last()
    
    for (depthState in 0..1) {
        if (boxes[depthState].isEmpty()) continue
        
        val buffer = bufferSource.getBuffer(lineRenderLayers[depthState])
        
        for (box in boxes[depthState]) {
            PrimitiveRenderer.renderLineBox(
                last, buffer, box.aabb,
                box.r, box.g, box.b, box.a
            )
        }
        
        bufferSource.endBatch(lineRenderLayers[depthState])
    }
}

private fun PoseStack.renderBatchedFilledBoxes(
    boxes: List<List<BoxData>>,
    bufferSource: MultiBufferSource.BufferSource
) {
    val filledBoxRenderLayers = listOf(CustomRenderLayer.QUADS, CustomRenderLayer.QUADS)
    val last = this.last()
    
    for (depthState in 0..1) {
        if (boxes[depthState].isEmpty()) continue
        
        val buffer = bufferSource.getBuffer(filledBoxRenderLayers[depthState])
        
        for (box in boxes[depthState]) {
            PrimitiveRenderer.addChainedFilledBoxVertices(
                last, buffer,
                box.aabb.minX.toFloat(), box.aabb.minY.toFloat(), box.aabb.minZ.toFloat(),
                box.aabb.maxX.toFloat(), box.aabb.maxY.toFloat(), box.aabb.maxZ.toFloat(),
                box.r, box.g, box.b, box.a
            )
        }
        
        bufferSource.endBatch(filledBoxRenderLayers[depthState])
    }
}

private fun PoseStack.renderBatchedTriangleStrips(
    triangleStrips: List<List<TriangleStripData>>,
    bufferSource: MultiBufferSource.BufferSource
) {
    val triangleRenderLayers = listOf(CustomRenderLayer.TRIANGLE_STRIP, CustomRenderLayer.TRIANGLE_STRIP_ESP)
    val last = this.last()
    
    for (depthState in 0..1) {
        if (triangleStrips[depthState].isEmpty()) continue

        for (strip in triangleStrips[depthState]) {
            // Get a fresh buffer for each strip to prevent connections between strips
            val buffer = bufferSource.getBuffer(triangleRenderLayers[depthState])
            
            for (point in strip.points) {
                buffer.addVertex(last, point.pos.x.toFloat(), point.pos.y.toFloat(), point.pos.z.toFloat())
                    .setColor(point.color)
            }
            
            // End batch after each strip to separate them
            bufferSource.endBatch(triangleRenderLayers[depthState])
        }
    }
}

private fun PoseStack.renderBatchedTexts(
    texts: List<TextData>,
    bufferSource: MultiBufferSource.BufferSource,
    camera: Vec3
) {
    val cameraPos = -camera

    for (textData in texts) {
        pushPose()
        val pose = last().pose()
        val scaleFactor = textData.scale * 0.025f

        pose.translate(textData.pos.x.toFloat(), textData.pos.y.toFloat(), textData.pos.z.toFloat())
            .translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
            .rotate(textData.cameraRotation)
            .scale(scaleFactor, -scaleFactor, scaleFactor)

        textData.font.drawInBatch(
            textData.text, 
            -textData.textWidth / 2f, 
            0f, 
            textData.color, 
            true, 
            pose, 
            bufferSource,
            if (textData.depth) net.minecraft.client.gui.Font.DisplayMode.NORMAL else net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0, 
            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
        )

        popPose()
    }
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color: Color, depth: Boolean, thickness: Float = 3f) {
    drawLine(points, color, color, depth, thickness)
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color1: Color, color2: Color, depth: Boolean, thickness: Float = 3f) {
    if (points.size < 2) return

    val rgba1 = color1.rgba
    val rgba2 = color2.rgba
    val batch = consumer.lines[if (depth) DEPTH else NO_DEPTH]

    val iterator = points.iterator()
    var current = iterator.next()

    while (iterator.hasNext()) {
        val next = iterator.next()
        batch.add(LineData(current, next, rgba1, rgba2, thickness))
        current = next
    }
}

fun RenderEvent.Extract.drawWireFrameBox(aabb: AABB, color: Color, thickness: Float = 3f, depth: Boolean = false) {
    consumer.wireBoxes[if (depth) DEPTH else NO_DEPTH].add(
        BoxData(aabb, color.redFloat, color.greenFloat, color.blueFloat, color.alphaFloat, thickness)
    )
}

fun RenderEvent.Extract.drawFilledBox(aabb: AABB, color: Color, depth: Boolean = false) {
    consumer.filledBoxes[if (depth) DEPTH else NO_DEPTH].add(
        BoxData(aabb, color.redFloat, color.greenFloat, color.blueFloat, color.alphaFloat, 3f)
    )
}

fun RenderEvent.Extract.drawCircle(
    center: Vec3,
    radius: Float,
    color: Color,
    segments: Int = 64,
    thickness: Float = 3f,
    depth: Boolean = false
) {
    // Draw circle as triangle strip for proper 3D ribbon effect
    drawCircleTriangleStrip(center, radius, color, segments, depth)
}

fun RenderEvent.Extract.drawCircleTriangleStrip(
    center: Vec3,
    radius: Float,
    color: Color,
    segments: Int = 64,
    depth: Boolean = false
) {
    val angleStep = 2.0 * Math.PI / segments
    
    // Increase base opacity by 20%
    val baseAlpha = ((color.rgba shr 24) and 0xFF)
    val increasedAlpha = kotlin.math.min(255, (baseAlpha * 1.2).toInt())
    val fadedColor = (color.rgba and 0x00FFFFFF) or (increasedAlpha shl 24)
    
    // Add to triangle strip batch
    val batch = consumer.triangleStrips[if (depth) DEPTH else NO_DEPTH]
    val points = mutableListOf<TriangleStripPoint>()
    
    for (i in 0..segments) {
        val angle = i * angleStep
        val x = center.x + radius * kotlin.math.cos(angle)
        val z = center.z + radius * kotlin.math.sin(angle)
        
        // Add bottom and top vertices for ribbon effect with consistent color
        points.add(TriangleStripPoint(Vec3(x, center.y, z), fadedColor))
        points.add(TriangleStripPoint(Vec3(x, center.y + 0.2, z), fadedColor))
    }
    
    batch.add(TriangleStripData(points))
}

fun RenderEvent.Extract.drawThickLine(
    from: Vec3,
    to: Vec3,
    colorStart: Color,
    colorEnd: Color,
    thickness: Float = 0.125f,
    depth: Boolean = false
) {
    // Calculate direction vector using component destructuring
    val (fromX, fromY, fromZ) = from
    val (toX, toY, toZ) = to
    val dx = toX - fromX
    val dy = toY - fromY
    val dz = toZ - fromZ
    
    // Calculate perpendicular vector for width (cross product with up vector)
    val length = kotlin.math.sqrt(dx * dx + dz * dz)
    if (length < 0.001) return
    
    // Perpendicular in XZ plane (horizontal width)
    val perpX = -dz / length * thickness
    val perpZ = dx / length * thickness
    
    val batch = consumer.triangleStrips[if (depth) DEPTH else NO_DEPTH]
    
    // Increase opacity by 20% for both start and end colors
    val startAlpha = ((colorStart.rgba shr 24) and 0xFF)
    val endAlpha = ((colorEnd.rgba shr 24) and 0xFF)
    val increasedStartAlpha = kotlin.math.min(255, (startAlpha * 1.2).toInt())
    val increasedEndAlpha = kotlin.math.min(255, (endAlpha * 1.2).toInt())
    
    val startRgba = (colorStart.rgba and 0x00FFFFFF) or (increasedStartAlpha shl 24)
    val endRgba = (colorEnd.rgba and 0x00FFFFFF) or (increasedEndAlpha shl 24)
    
    // Add height offset to render above carpets (0.1 blocks)
    val heightOffset = 0.1
    
    // Create a horizontal flat ribbon on the ground with width
    // Triangle strip: left-start, right-start, left-end, right-end
    val points = mutableListOf<TriangleStripPoint>()
    
    points.add(TriangleStripPoint(Vec3(from.x - perpX, from.y + heightOffset, from.z - perpZ), startRgba))
    points.add(TriangleStripPoint(Vec3(from.x + perpX, from.y + heightOffset, from.z + perpZ), startRgba))
    points.add(TriangleStripPoint(Vec3(to.x - perpX, to.y + heightOffset, to.z - perpZ), endRgba))
    points.add(TriangleStripPoint(Vec3(to.x + perpX, to.y + heightOffset, to.z + perpZ), endRgba))
    
    batch.add(TriangleStripData(points))
}

fun RenderEvent.Extract.drawSquare(
    center: Vec3,
    size: Float,
    color: Color,
    yaw: Float,
    depth: Boolean = false
) {
    val halfSize = size / 2f
    
    // Increase base opacity by 20%
    val baseAlpha = ((color.rgba shr 24) and 0xFF)
    val increasedAlpha = kotlin.math.min(255, (baseAlpha * 1.2).toInt())
    val fadedColor = (color.rgba and 0x00FFFFFF) or (increasedAlpha shl 24)
    
    val batch = consumer.triangleStrips[if (depth) DEPTH else NO_DEPTH]
    val points = mutableListOf<TriangleStripPoint>()
    
    // Rotate the square to align with player's yaw
    val yawRad = Math.toRadians(yaw.toDouble())
    val cos = kotlin.math.cos(yawRad)
    val sin = kotlin.math.sin(yawRad)
    
    // Calculate rotated corners
    fun rotatePoint(x: Double, z: Double): Pair<Double, Double> {
        val rotX = x * cos - z * sin
        val rotZ = x * sin + z * cos
        return Pair(rotX, rotZ)
    }
    
    val (x1, z1) = rotatePoint(-halfSize.toDouble(), -halfSize.toDouble())
    val (x2, z2) = rotatePoint(halfSize.toDouble(), -halfSize.toDouble())
    val (x3, z3) = rotatePoint(-halfSize.toDouble(), halfSize.toDouble())
    val (x4, z4) = rotatePoint(halfSize.toDouble(), halfSize.toDouble())
    
    // Add height offset to render above carpets (0.1 blocks)
    val heightOffset = 0.1
    
    // Create a single flat square on the ground using triangle strip
    points.add(TriangleStripPoint(Vec3(center.x + x1, center.y + heightOffset, center.z + z1), fadedColor))
    points.add(TriangleStripPoint(Vec3(center.x + x2, center.y + heightOffset, center.z + z2), fadedColor))
    points.add(TriangleStripPoint(Vec3(center.x + x3, center.y + heightOffset, center.z + z3), fadedColor))
    points.add(TriangleStripPoint(Vec3(center.x + x4, center.y + heightOffset, center.z + z4), fadedColor))
    
    batch.add(TriangleStripData(points))
}

fun RenderEvent.Extract.drawArc(
    center: Vec3,
    radius: Float,
    color: Color,
    segments: Int = 6,
    thickness: Float = 3f,
    depth: Boolean = false
) {
    val points = mutableListOf<Vec3>()
    
    for (i in 0..segments) {
        val angle = Math.PI * 0.4 + Math.PI * 0.2 * (i.toDouble() / segments) - Math.PI * 0.5
        val x = center.x + radius * kotlin.math.sin(angle)
        val z = center.z + radius * kotlin.math.cos(angle)
        points.add(Vec3(x, center.y, z))
    }

    drawLine(points, color, depth, thickness)
}

object PrimitiveRenderer {
    private val edges = intArrayOf(
        0, 1,  1, 2,  2, 3,  3, 0,
        4, 5,  5, 6,  6, 7,  7, 4,
        0, 4,  1, 5,  2, 6,  3, 7
    )

    fun renderLineBox(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        aabb: AABB,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val x0 = aabb.minX.toFloat()
        val y0 = aabb.minY.toFloat()
        val z0 = aabb.minZ.toFloat()
        val x1 = aabb.maxX.toFloat()
        val y1 = aabb.maxY.toFloat()
        val z1 = aabb.maxZ.toFloat()

        val corners = floatArrayOf(
            x0, y0, z0,
            x1, y0, z0,
            x1, y1, z0,
            x0, y1, z0,
            x0, y0, z1,
            x1, y0, z1,
            x1, y1, z1,
            x0, y1, z1
        )

        for (i in edges.indices step 2) {
            val i0 = edges[i] * 3
            val i1 = edges[i + 1] * 3

            val x0 = corners[i0]
            val y0 = corners[i0 + 1]
            val z0 = corners[i0 + 2]
            val x1 = corners[i1]
            val y1 = corners[i1 + 1]
            val z1 = corners[i1 + 2]

            val dx = x1 - x0
            val dy = y1 - y0
            val dz = z1 - z0

            buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz)
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz)
        }
    }

    fun addChainedFilledBoxVertices(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val matrix = pose.pose()

        fun vertex(x: Float, y: Float, z: Float) {
            buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a)
        }

        // Bottom face (y = minY)
        vertex(minX, minY, minZ)
        vertex(maxX, minY, minZ)
        vertex(maxX, minY, maxZ)
        vertex(minX, minY, maxZ)

        // Top face (y = maxY)
        vertex(minX, maxY, minZ)
        vertex(minX, maxY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, maxY, minZ)

        // North face (z = minZ)
        vertex(minX, minY, minZ)
        vertex(minX, maxY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, minY, minZ)

        // South face (z = maxZ)
        vertex(minX, minY, maxZ)
        vertex(maxX, minY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(minX, maxY, maxZ)

        // West face (x = minX)
        vertex(minX, minY, minZ)
        vertex(minX, minY, maxZ)
        vertex(minX, maxY, maxZ)
        vertex(minX, maxY, minZ)

        // East face (x = maxX)
        vertex(maxX, minY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, minY, maxZ)
    }

    fun renderVector(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vector3f,
        direction: Vec3,
        startColor: Int,
        endColor: Int
    ) {
        val endX = start.x() + direction.x.toFloat()
        val endY = start.y() + direction.y.toFloat()
        val endZ = start.z() + direction.z.toFloat()

        val nx = direction.x.toFloat()
        val ny = direction.y.toFloat()
        val nz = direction.z.toFloat()

        buffer.addVertex(pose, start.x(), start.y(), start.z())
            .setColor(startColor)
            .setNormal(pose, nx, ny, nz)

        buffer.addVertex(pose, endX, endY, endZ)
            .setColor(endColor)
            .setNormal(pose, nx, ny, nz)
    }
    
    /**
     * Render a thick line as a quad (rectangle) for better visibility.
     * This bypasses OpenGL line width limitations.
     */
    fun renderThickLine(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vec3,
        end: Vec3,
        startColor: Int,
        endColor: Int,
        thickness: Float
    ) {
        // Calculate direction and perpendicular vectors
        val dx = (end.x - start.x).toFloat()
        val dy = (end.y - start.y).toFloat()
        val dz = (end.z - start.z).toFloat()
        
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.001f) return
        
        // Normalize direction
        val dirX = dx / length
        val dirY = dy / length
        val dirZ = dz / length
        
        // Calculate perpendicular vector (for thickness)
        // Use cross product with up vector (0, 1, 0)
        var perpX = -dirZ
        var perpY = 0f
        var perpZ = dirX
        
        val perpLength = kotlin.math.sqrt(perpX * perpX + perpZ * perpZ)
        if (perpLength > 0.001f) {
            perpX /= perpLength
            perpZ /= perpLength
        } else {
            // If line is vertical, use different perpendicular
            perpX = 1f
            perpZ = 0f
        }
        
        // Scale perpendicular by thickness
        val halfThickness = thickness * 0.05f // Scale factor for visual thickness
        perpX *= halfThickness
        perpZ *= halfThickness
        
        // Create quad vertices
        val x1 = start.x.toFloat()
        val y1 = start.y.toFloat()
        val z1 = start.z.toFloat()
        val x2 = end.x.toFloat()
        val y2 = end.y.toFloat()
        val z2 = end.z.toFloat()
        
        // Four corners of the quad
        buffer.addVertex(pose, x1 - perpX, y1, z1 - perpZ)
            .setColor(startColor)
            .setNormal(pose, dirX, dirY, dirZ)
            
        buffer.addVertex(pose, x1 + perpX, y1, z1 + perpZ)
            .setColor(startColor)
            .setNormal(pose, dirX, dirY, dirZ)
    }
}

/**
 * Draw 3D text in world space that always faces the camera.
 * 
 * @param text The text to display
 * @param pos The world position to render the text at
 * @param scale The scale of the text (1.0 = normal size)
 * @param color The color of the text (ARGB format)
 * @param depth Whether the text should respect depth (true = can be occluded by blocks)
 */
fun RenderEvent.Extract.drawText3D(
    text: String, 
    pos: Vec3, 
    scale: Float = 1.0f, 
    color: Int = 0xFFFFFFFF.toInt(), 
    depth: Boolean = false
) {
    val cameraRotation = Melinoe.mc.gameRenderer.mainCamera.rotation()
    val font = Melinoe.mc.font
    val textWidth = font.width(text).toFloat()

    consumer.texts.add(TextData(text, pos, scale, depth, cameraRotation, font, textWidth, color))
}

/**
 * Draw 3D text in world space with a Color object.
 */
fun RenderEvent.Extract.drawText3D(
    text: String, 
    pos: Vec3, 
    scale: Float = 1.0f, 
    color: Color, 
    depth: Boolean = false
) {
    drawText3D(text, pos, scale, color.rgba, depth)
}
