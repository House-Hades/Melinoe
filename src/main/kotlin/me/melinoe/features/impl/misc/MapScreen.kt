package me.melinoe.features.impl.misc

import com.mojang.blaze3d.platform.InputConstants
import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.features.impl.tracking.bosstracker.BossState
import me.melinoe.features.impl.tracking.bosstracker.TrackerModule
import me.melinoe.network.MapTileDownloader
import me.melinoe.utils.Colors
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.TelosItemUtils
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.MapData
import me.melinoe.utils.data.MapTileData
import me.melinoe.utils.data.MinibossData
import me.melinoe.utils.ui.rendering.Image
import me.melinoe.utils.ui.rendering.NVGPIPRenderer
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.pow
import kotlin.math.roundToInt
import me.melinoe.utils.ui.mouseX as melinoeMouseX
import me.melinoe.utils.ui.mouseY as melinoeMouseY

/**
 * Credit to Wynntils for practically everything related to the Map.
 *
 * Map of Telos, The view is clamped to the active region's bounds.
 * The map always fills the whole panel with no void visible
 */
object MapScreen : Screen(Component.literal("Telos Map")) {

    private const val MIN_ZOOM = 0.1
    private const val MAX_ZOOM = 10.0

    private const val PANEL_INSET = 0.1f
    private const val MARKER_IDLE_ALPHA = 0.85f
    private const val MINIBOSS_IDLE_ALPHA = 0.7f

    // Base colors
    private val background = me.melinoe.utils.Color(16, 16, 20).rgba
    private val placeholderFill = me.melinoe.utils.Color(32, 32, 38).rgba
    private val tooltipFill = me.melinoe.utils.Color(26, 26, 26).rgba
    private val alertColor = me.melinoe.utils.Color(255, 92, 92).rgba

    // Boss state colors
    private val stateAlive = me.melinoe.utils.Color(96, 255, 128).rgba
    private val stateFighting = me.melinoe.utils.Color(255, 216, 74).rgba
    private val statePortal = me.melinoe.utils.Color(255, 92, 92).rgba
    private val stateGone = me.melinoe.utils.Color(158, 158, 158).rgba
    private val stateNeutral = me.melinoe.utils.Color(140, 190, 255).rgba
    
    private val shadowlandsBossNames = setOf("REAPER", "WARDEN", "HERALD", "DEFENDER")

    /** Which map is shown: "main", "shadowlands" or "radiant_isles" */
    private var activeRegion = "main"

    // World coordinates at the viewport center
    private var centerX = 0.0
    private var centerZ = 0.0
    
    private var zoom = 1.0

    private var viewInitialized = false
    private var dragging = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    
    private val tileImages = HashMap<String, Image>()
    private val failedImages = HashSet<String>()

    private data class Marker(
        val worldX: Double,
        val worldZ: Double,
        val label: String,
        val color: Int,
        val stateText: String,
        val texture: String?,
        val calledPlayer: String?,
        val alwaysVisible: Boolean = false,
        val miniboss: Boolean = false,
        val worldY: Int = 0
    )
    
    private var hoveredMarker: Marker? = null

    private val scW get() = mc.window.screenWidth.toFloat()
    private val scH get() = mc.window.screenHeight.toFloat()

    private val panelX get() = scW * PANEL_INSET
    private val panelY get() = scH * PANEL_INSET
    private val panelW get() = scW * (1f - 2f * PANEL_INSET)
    private val panelH get() = scH * (1f - 2f * PANEL_INSET)

    private fun worldToScreenX(wx: Double): Float = (panelX + panelW / 2.0 + (wx - centerX) * zoom).toFloat()
    private fun worldToScreenZ(wz: Double): Float = (panelY + panelH / 2.0 + (wz - centerZ) * zoom).toFloat()
    private fun screenToWorldX(sx: Float): Double = centerX + (sx - panelX - panelW / 2.0) / zoom
    private fun screenToWorldZ(sy: Float): Double = centerZ + (sy - panelY - panelH / 2.0) / zoom

    private fun isInPanel(x: Float, y: Float): Boolean =
        x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH

    /** Called right before the screen opens */
    fun prepareOpen() {
        MapTileDownloader.ensureTiles(retryFailed = true)
        val previousRegion = activeRegion
        activeRegion = when (LocalAPI.getCurrentCharacterArea()) {
            "Shadowlands" -> "shadowlands"
            "Radiant Isles" -> "radiant_isles"
            else -> "main"
        }
        if (MapModule.rememberView && viewInitialized && activeRegion == previousRegion) {
            clampView()
            return
        }
        zoom = MapModule.defaultZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        centerOnPlayer()
        viewInitialized = true
    }

    private fun centerOnPlayer() {
        val player = mc.player
        val bounds = MapData.bounds(activeRegion)
        if (player != null && LocalAPI.isInRealm()) {
            centerX = player.x
            centerZ = player.z
        } else {
            centerX = bounds?.let { (it[0] + it[2]) / 2.0 } ?: 0.0
            centerZ = bounds?.let { (it[1] + it[3]) / 2.0 } ?: 0.0
        }
        clampView()
    }

    /**
     * Smallest zoom that still covers the whole panel with map
     */
    private fun minZoom(): Double {
        val b = MapData.bounds(activeRegion) ?: return MIN_ZOOM
        val cover = maxOf(panelW / (b[2] - b[0] + 1.0), panelH / (b[3] - b[1] + 1.0))
        return cover.coerceAtMost(MAX_ZOOM)
    }

    /** Clamps zoom and view center so the visible rect never leaves the active region's bounds */
    private fun clampView() {
        zoom = zoom.coerceIn(minZoom(), MAX_ZOOM)
        val b = MapData.bounds(activeRegion) ?: return
        centerX = clampAxis(centerX, b[0].toDouble(), b[2] + 1.0, panelW / 2.0 / zoom)
        centerZ = clampAxis(centerZ, b[1].toDouble(), b[3] + 1.0, panelH / 2.0 / zoom)
    }

    private fun clampAxis(center: Double, min: Double, max: Double, halfView: Double): Double =
        if (max - min <= halfView * 2) (min + max) / 2 else center.coerceIn(min + halfView, max - halfView)

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            val mx = melinoeMouseX
            val my = melinoeMouseY
            val uiScale = ClickGUIModule.getStandardGuiScale()

            if (dragging) {
                centerX -= (mx - lastMouseX) / zoom
                centerZ -= (my - lastMouseY) / zoom
            }
            lastMouseX = mx
            lastMouseY = my
            clampView()

            val px = panelX
            val py = panelY
            val pw = panelW
            val ph = panelH

            NVGRenderer.dropShadow(px, py, pw, ph, 16f, 4f, 0f)
            NVGRenderer.rect(px, py, pw, ph, background)

            NVGRenderer.pushScissor(px, py, pw, ph)
            drawTiles()
            drawMarkers(mx, my, uiScale)
            drawPlayerArrow(uiScale)
            NVGRenderer.popScissor()

            NVGRenderer.hollowRect(px, py, pw, ph, 1.5f * uiScale, ClickGUIModule.clickGUIColor.rgba, 0f)

            drawOverlayText(mx, my, uiScale)
            hoveredMarker?.let { drawTooltip(it, mx, my, uiScale) }
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }

    private fun drawTiles() {
        val worldMinX = screenToWorldX(panelX)
        val worldMaxX = screenToWorldX(panelX + panelW)
        val worldMinZ = screenToWorldZ(panelY)
        val worldMaxZ = screenToWorldZ(panelY + panelH)

        for (tile in MapData.tilesFor(activeRegion)) {
            // Tile bounds are inclusive, so the tile spans x1..x2+1 in continuous world coords
            if (tile.x2 + 1 < worldMinX || tile.x1 > worldMaxX || tile.z2 + 1 < worldMinZ || tile.z1 > worldMaxZ) continue

            val sx = worldToScreenX(tile.x1.toDouble())
            val sy = worldToScreenZ(tile.z1.toDouble())
            val tw = (tile.width * zoom).toFloat()
            val th = (tile.height * zoom).toFloat()

            val image = if (MapTileDownloader.status(tile) == MapTileDownloader.Status.READY) tileImage(tile) else null
            if (image != null) {
                NVGRenderer.image(image, sx, sy, tw, th)
            } else {
                NVGRenderer.rect(sx, sy, tw, th, placeholderFill)
                NVGRenderer.hollowRect(sx, sy, tw, th, 1f, background, 0f)
            }
        }
    }

    private fun tileImage(tile: MapTileData): Image? {
        if (tile.md5 in failedImages) return null
        tileImages[tile.md5]?.let { return it }
        return runCatching { NVGRenderer.createImage(tile.localFile.absolutePath, nearest = true) }
            .onSuccess { tileImages[tile.md5] = it }
            .onFailure {
                failedImages.add(tile.md5)
                Melinoe.logger.error("[MapScreen] Failed to load map tile image ${tile.name}", it)
            }
            .getOrNull()
    }

    /** Gathers boss markers: the static registry drives the list, the boss tracker supplies live state */
    private fun collectMarkers(): List<Marker> {
        val markers = ArrayList<Marker>()

        // Boss states come from the tracker
        val trackerOn = TrackerModule.enabled

        // The tracker keys bosses by whatever case the source used
        val trackedByLabel =
            if (trackerOn) BossState.getAllBosses().associateBy { it.data.label.uppercase() } else emptyMap()

        if (MapModule.showBosses) {
            for (boss in BossData.all) {
                val pos = boss.spawnPosition ?: continue
                if (regionOf(boss.name, pos.x, pos.z) != activeRegion) continue

                val tracked = trackedByLabel[boss.label.uppercase()]
                val (color, stateText) = when {
                    !trackerOn -> stateGone to describeBoss(boss.name)
                    tracked == null -> stateGone to "Dead"
                    tracked.state == BossState.State.ALIVE && tracked.calledPlayerName != null ->
                        stateFighting to "Fighting: ${tracked.calledPlayerName}"

                    tracked.state == BossState.State.ALIVE -> stateAlive to "Alive"
                    tracked.state == BossState.State.DEFEATED_PORTAL_ACTIVE -> statePortal to "Portal Spawned"
                    tracked.state == BossState.State.SHADOWLANDS_IDLE -> stateGone to "Idle"
                    else -> stateGone to "Defeated"
                }
                markers.add(
                    Marker(
                        pos.x + 0.5, pos.z + 0.5, boss.label, color, stateText,
                        TelosItemUtils.getIdentifier(boss.name)?.let { texturePath(it) },
                        tracked?.calledPlayerName, worldY = pos.y
                    )
                )
            }
        }

        if (MapModule.showMinibosses) {
            // A miniboss can spawn at any of its listed locations, so each gets a marker
            for (miniboss in MinibossData.all) {
                if (miniboss.region != activeRegion) continue
                val texture = miniboss.texture?.let { "$it.png" }
                    ?: TelosItemUtils.getIdentifier(miniboss.name)?.let { texturePath(it) }
                for (pos in miniboss.positions) {
                    markers.add(
                        Marker(
                            pos.x + 0.5, pos.z + 0.5, miniboss.label, stateGone,
                            miniboss.note ?: "Miniboss", texture, null, miniboss = true, worldY = pos.y
                        )
                    )
                }
            }
        }

        if (MapModule.showBosses && TrackerModule.enabled) {
            for (boss in BossState.getAllBosses()) {
                if (BossData.findByKey(boss.data.label) != null) continue
                val pos = boss.spawnPosition
                if (regionOf(boss.name, pos.x, pos.z) != activeRegion) continue
                markers.add(
                    Marker(
                        pos.x + 0.5, pos.z + 0.5, boss.name, stateNeutral, "",
                        texturePath(boss.data.modelIdentifier), null, alwaysVisible = true, worldY = pos.y
                    )
                )
            }
        }
        return markers
    }

    private fun texturePath(id: Identifier): String = "${id.namespace}:${id.path}.png"

    private fun drawMarkers(mx: Float, my: Float, uiScale: Float) {
        val markers = collectMarkers()
        val baseRadius = 5f * MapModule.markerScale.toFloat() * uiScale
        val baseHeadSize = 26f * MapModule.markerScale.toFloat() * uiScale
        val headGap = 3f * uiScale
        val maxX = panelX + panelW
        val maxY = panelY + panelH
        val cull = baseHeadSize + baseRadius

        fun hasTexture(marker: Marker) = marker.texture != null && NVGRenderer.resourceTexture(marker.texture) != -1

        // Hover before drawing so the hovered marker renders opaque
        hoveredMarker = markers.lastOrNull { marker ->
            val sx = worldToScreenX(marker.worldX)
            val sy = worldToScreenZ(marker.worldZ)
            if (hasTexture(marker)) {
                val half = baseHeadSize / 2f
                mx >= sx - half && mx <= sx + half && my >= sy - half && my <= sy + half
            } else {
                val dx = mx - sx
                val dy = my - sy
                dx * dx + dy * dy <= (baseRadius + 2f) * (baseRadius + 2f)
            }
        }

        for (marker in markers) {
            val sx = worldToScreenX(marker.worldX)
            val sy = worldToScreenZ(marker.worldZ)
            if (sx < panelX - cull || sx > maxX + cull || sy < panelY - cull || sy > maxY + cull) continue

            val hovered = marker == hoveredMarker
            val alpha = when {
                hovered || marker.alwaysVisible -> 1f
                marker.miniboss -> MINIBOSS_IDLE_ALPHA
                else -> MARKER_IDLE_ALPHA
            }
            NVGRenderer.globalAlpha(alpha)
            
            val labelAnchor: Float
            if (hasTexture(marker)) {
                val hx = sx - baseHeadSize / 2f
                val hy = sy - baseHeadSize / 2f
                NVGRenderer.glow(hx, hy, baseHeadSize, baseHeadSize, 8f * uiScale, 1f * uiScale, baseHeadSize / 2f, glowColor(marker.color))
                NVGRenderer.texturedRect(marker.texture!!, hx, hy, baseHeadSize, baseHeadSize)
                labelAnchor = sy + baseHeadSize / 2f
            } else {
                NVGRenderer.circle(sx, sy, baseRadius + 1.5f * uiScale, Colors.BLACK.rgba)
                NVGRenderer.circle(sx, sy, baseRadius, marker.color)
                labelAnchor = sy + baseRadius
            }

            if (zoom >= 1.5 || hovered) {
                val size = 14f * uiScale
                val labelWidth = NVGRenderer.textWidth(marker.label, size, NVGRenderer.defaultFont)
                NVGRenderer.textShadow(marker.label, sx - labelWidth / 2f, labelAnchor + 3f * uiScale, size, Colors.WHITE.rgba, NVGRenderer.defaultFont)
            }

            NVGRenderer.globalAlpha(1f)
        }
    }

    private fun glowColor(color: Int) = (color and 0x00FFFFFF) or (0xB4 shl 24)

    private fun describeBoss(name: String): String = when (name.uppercase()) {
        "RAPHAEL" -> "Sanguine Lord"
        "REAPER", "WARDEN", "HERALD" -> "Shadowlands Miniboss"
        else -> "Realm Boss"
    }

    private fun regionOf(name: String, x: Int, z: Int): String = when {
        name.uppercase().substringBefore(" (") in shadowlandsBossNames -> "shadowlands"
        x >= 50000 || z >= 50000 -> "radiant_isles"
        else -> "main"
    }

    private fun drawPlayerArrow(uiScale: Float) {
        val player = mc.player ?: return
        if (!LocalAPI.isInRealm()) return

        val px = worldToScreenX(player.x)
        val py = worldToScreenZ(player.z)
        val size = 8f * MapModule.markerScale.toFloat() * uiScale
        val thickness = 2f * uiScale
        val color = MapModule.playerArrowColor.rgba

        NVGRenderer.push()
        NVGRenderer.translate(px, py)
        // yRot 0 = south (+Z); the map draws north (-Z) upwards
        NVGRenderer.rotate(Math.toRadians(player.yRot + 180.0).toFloat())
        NVGRenderer.line(0f, -size, size * 0.6f, size, thickness, color)
        NVGRenderer.line(size * 0.6f, size, 0f, size * 0.45f, thickness, color)
        NVGRenderer.line(0f, size * 0.45f, -size * 0.6f, size, thickness, color)
        NVGRenderer.line(-size * 0.6f, size, 0f, -size, thickness, color)
        NVGRenderer.pop()
    }

    private fun drawOverlayText(mx: Float, my: Float, uiScale: Float) {
        val size = 16f * uiScale
        val font = NVGRenderer.defaultFont
        val centerTextX = panelX + panelW / 2f

        val tiles = MapData.tilesFor(activeRegion)
        if (tiles.isEmpty()) {
            val text = "Map data not yet available"
            NVGRenderer.textShadow(text, centerTextX - NVGRenderer.textWidth(text, size, font) / 2f, panelY + panelH / 2f - size / 2f, size, Colors.WHITE.rgba, font)
        } else {
            val ready = tiles.count { MapTileDownloader.status(it) == MapTileDownloader.Status.READY }
            if (ready < tiles.size) {
                val text = "Downloading map… ($ready/${tiles.size})"
                NVGRenderer.textShadow(text, centerTextX - NVGRenderer.textWidth(text, size, font) / 2f, panelY + 10f * uiScale, size, Colors.WHITE.rgba, font)
            }
        }

        if (mc.player == null || !LocalAPI.isInRealm()) {
            val text = "You are not in a Realm"
            NVGRenderer.textShadow(text, centerTextX - NVGRenderer.textWidth(text, size, font) / 2f, panelY + 34f * uiScale, size, alertColor, font)
        }

        if (isInPanel(mx, my)) {
            val coords = "X: ${screenToWorldX(mx).roundToInt()}  Z: ${screenToWorldZ(my).roundToInt()}"
            NVGRenderer.textShadow(coords, centerTextX - NVGRenderer.textWidth(coords, size, font) / 2f, panelY + panelH - size - 10f * uiScale, size, Colors.WHITE.rgba, font)
        }
    }

    private fun drawTooltip(marker: Marker, mx: Float, my: Float, uiScale: Float) {
        val font = NVGRenderer.defaultFont
        val titleSize = 18f * uiScale
        val bodySize = 14f * uiScale
        val hintSize = 13f * uiScale
        val pad = 10f * uiScale
        val gap = 4f * uiScale
        val dimText = me.melinoe.utils.Color(170, 170, 170).rgba

        val pinned = MapPins.isPinnedAt(
            marker.label,
            BlockPos((marker.worldX - 0.5).toInt(), marker.worldY, (marker.worldZ - 0.5).toInt())
        )

        val hasIcon = marker.texture != null && NVGRenderer.resourceTexture(marker.texture) != -1
        val iconSize = if (hasIcon) titleSize + 2f * uiScale else 0f
        val titleTextOffset = if (hasIcon) iconSize + 6f * uiScale else 0f

        val body = buildList {
            if (marker.stateText.isNotEmpty()) add(marker.stateText to marker.color)
        }
        val coordParts = listOf("X" to marker.worldX.toInt(), "Y" to marker.worldY, "Z" to marker.worldZ.toInt())
        val coordsPlain = coordParts.joinToString("  ") { "${it.first}: ${it.second}" }
        val hints = buildList {
            marker.calledPlayer?.let { add("Left-click to teleport to $it") }
            add(if (pinned) "Right-click to unpin" else "Right-click to pin")
        }

        val width = maxOf(
            titleTextOffset + NVGRenderer.textWidth(marker.label, titleSize, font),
            (body.maxOfOrNull { NVGRenderer.textWidth(it.first, bodySize, font) } ?: 0f),
            NVGRenderer.textWidth(coordsPlain, bodySize, font),
            hints.maxOf { NVGRenderer.textWidth(it, hintSize, font) }
        ) + pad * 2f
        val height = pad * 2f +
                maxOf(titleSize, iconSize) + gap +
                body.size * (bodySize + gap) +
                bodySize + gap +
                gap + 1f +
                hints.size * (hintSize + gap) - gap + gap

        val x = (mx + 12f * uiScale).coerceAtMost(scW - width - 4f)
        val y = (my + 12f * uiScale).coerceAtMost(scH - height - 4f)

        NVGRenderer.dropShadow(x, y, width, height, 10f, 2f, 8f)
        NVGRenderer.rect(x, y, width, height, tooltipFill, 8f)
        NVGRenderer.hollowRect(x, y, width, height, 1f, glowColor(marker.color), 8f)

        var lineY = y + pad
        if (hasIcon) NVGRenderer.texturedRect(marker.texture!!, x + pad, lineY, iconSize, iconSize)
        NVGRenderer.text(marker.label, x + pad + titleTextOffset, lineY + (maxOf(titleSize, iconSize) - titleSize) / 2f, titleSize, Colors.WHITE.rgba, font)
        lineY += maxOf(titleSize, iconSize) + gap

        for ((text, color) in body) {
            NVGRenderer.text(text, x + pad, lineY, bodySize, color, font)
            lineY += bodySize + gap
        }

        var coordX = x + pad
        for ((label, value) in coordParts) {
            NVGRenderer.text("$label: ", coordX, lineY, bodySize, dimText, font)
            coordX += NVGRenderer.textWidth("$label: ", bodySize, font)
            NVGRenderer.text("$value", coordX, lineY, bodySize, Colors.WHITE.rgba, font)
            coordX += NVGRenderer.textWidth("$value", bodySize, font) + NVGRenderer.textWidth("  ", bodySize, font)
        }
        lineY += bodySize + gap

        NVGRenderer.rect(x + pad, lineY, width - pad * 2f, 1f, me.melinoe.utils.Color(255, 255, 255, 0.15f).rgba)
        lineY += 1f + gap

        for ((i, hint) in hints.withIndex()) {
            val color = if (i == 0 && marker.calledPlayer != null) glowColor(stateFighting) else dimText
            NVGRenderer.text(hint, x + pad, lineY, hintSize, color, font)
            lineY += hintSize + gap
        }
    }

    private fun teleportTo(marker: Marker) {
        val player = mc.player ?: return
        val target = marker.calledPlayer ?: return
        if (target.equals(player.gameProfile.name, ignoreCase = true)) {
            Message.error("You cannot teleport to yourself!")
            return
        }
        player.connection.sendCommand("tp $target")
        Message.success("<#AAAAAA>Teleporting to <#FFFF00><underlined>$target</underlined> <#AAAAAA>at <#FFFF00><bold>${marker.label}</bold>")
        onClose()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (isInPanel(melinoeMouseX, melinoeMouseY)) {
            when (mouseButtonEvent.button()) {
                0 -> {
                    val target = hoveredMarker
                    if (target?.calledPlayer != null) {
                        teleportTo(target)
                        return true
                    }
                    dragging = true
                    lastMouseX = melinoeMouseX
                    lastMouseY = melinoeMouseY
                    return true
                }

                1 -> {
                    val marker = hoveredMarker
                    if (marker != null) {
                        val pinned = MapPins.toggle(
                            marker.label,
                            BlockPos((marker.worldX - 0.5).toInt(), marker.worldY, (marker.worldZ - 0.5).toInt()),
                            marker.texture, marker.color, activeRegion
                        )
                        if (pinned) {
                            Message.success("<#AAAAAA>Pinned <#FFFF00><bold>${marker.label}</bold> <#AAAAAA>for ${MapModule.pinDuration.toInt()}s")
                        } else {
                            Message.success("<#AAAAAA>Unpinned <#FFFF00><bold>${marker.label}</bold>")
                        }
                    } else {
                        centerOnPlayer()
                    }
                    return true
                }
            }
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0) dragging = false
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val old = zoom
        zoom = (zoom * 1.15.pow(verticalAmount)).coerceIn(minZoom(), MAX_ZOOM)
        if (zoom != old) {
            centerX += (melinoeMouseX - panelX - panelW / 2.0) * (1.0 / old - 1.0 / zoom)
            centerZ += (melinoeMouseY - panelY - panelH / 2.0) * (1.0 / old - 1.0 / zoom)
            clampView()
        }
        return true
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (MapModule.openMapKey != InputConstants.UNKNOWN && InputConstants.getKey(keyEvent) == MapModule.openMapKey) {
            onClose()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        dragging = false
        hoveredMarker = null
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
