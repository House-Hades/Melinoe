package me.melinoe.features.impl.misc

import me.melinoe.Melinoe.mc
import me.melinoe.features.impl.tracking.bosstracker.BossState
import me.melinoe.features.impl.tracking.bosstracker.RendererWaypoints
import me.melinoe.features.impl.tracking.bosstracker.TrackerModule
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.render.WaypointData
import me.melinoe.utils.render.WaypointRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

/**
 * Credit to Wynntils for practically everything related to the Map.
 *
 * Markers pinned from the map screen (right-click)
 */
object MapPins {

    /** Pins fade out as the player closes in, and unpin once reached */
    private const val FADE_RANGE = 10.0
    private const val UNPIN_RANGE = 3.0

    data class Pin(
        val label: String,
        val pos: BlockPos,
        val icon: Identifier?,
        val color: FloatArray,
        val region: String,
        val expiresAt: Long
    )

    // Keyed by label + position so each miniboss spawn location can be pinned separately
    private val pins = LinkedHashMap<String, Pin>()

    /** Toggles a pin for the marker */
    fun toggle(label: String, pos: BlockPos, iconPath: String?, colorRgba: Int, region: String): Boolean {
        purge()
        val key = "${label.uppercase()}@${pos.x},${pos.z}"
        if (pins.remove(key) != null) return false

        val icon = iconPath?.let { runCatching { Identifier.parse(it) }.getOrNull() }
        val color = floatArrayOf(
            ((colorRgba shr 16) and 0xFF) / 255f,
            ((colorRgba shr 8) and 0xFF) / 255f,
            (colorRgba and 0xFF) / 255f
        )
        pins[key] = Pin(label, pos, icon, color, region, System.currentTimeMillis() + MapModule.pinDuration.toLong() * 1000L)
        return true
    }

    /** Fade for an approaching player: full beyond 10 blocks, gone at 3 */
    fun proximityAlpha(distance: Double): Float =
        ((distance - UNPIN_RANGE) / (FADE_RANGE - UNPIN_RANGE)).toFloat().coerceIn(0f, 1f)

    /** Removes pins the player has reached */
    private fun unpinReached() {
        val playerPos = mc.player?.position() ?: return
        pins.values.removeIf { playerPos.distanceTo(Vec3.atCenterOf(it.pos)) <= UNPIN_RANGE }
    }

    /** Pinned waypoints pulse their color toward red instead of fading their alpha */
    fun flashColor(base: FloatArray): FloatArray {
        val t = 0.5f + 0.5f * kotlin.math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * 2 * Math.PI).toFloat()
        return floatArrayOf(
            base[0] + (1f - base[0]) * t,
            base[1] + (0.15f - base[1]) * t,
            base[2] + (0.15f - base[2]) * t
        )
    }

    /** Whether an active pin targets this boss; the tracker renderer flashes its waypoint then */
    fun isPinned(bossName: String): Boolean {
        purge()
        return pins.values.any { it.label.equals(bossName, ignoreCase = true) }
    }

    /** Whether this exact marker location is pinned */
    fun isPinnedAt(label: String, pos: BlockPos): Boolean {
        purge()
        return pins.containsKey("${label.uppercase()}@${pos.x},${pos.z}")
    }

    /** Region for the player's current area */
    fun currentRegion(): String = when (LocalAPI.getCurrentCharacterArea()) {
        "Shadowlands" -> "shadowlands"
        "Radiant Isles" -> "radiant_isles"
        else -> "main"
    }

    /** Pins the tracker already shows as a flashing waypoint */
    private fun coveredByTracker(pin: Pin): Boolean =
        TrackerModule.enabled && RendererWaypoints.showWaypoints &&
                BossState.getAllBosses().any { it.name.equals(pin.label, ignoreCase = true) }

    /** Renders pins that no tracker waypoint covers */
    fun renderStandalone(context: LevelRenderContext) {
        purge()
        unpinReached()
        if (pins.isEmpty()) return

        val camera = mc.gameRenderer.mainCamera ?: return
        val cameraPos = camera.position()
        val region = currentRegion()

        val playerPos = mc.player?.position() ?: cameraPos
        val waypoints = pins.values
            .filter { it.region == region && !coveredByTracker(it) }
            .mapNotNull { pin ->
                val pos = Vec3(pin.pos.x.toDouble(), pin.pos.y.toDouble(), pin.pos.z.toDouble())
                val playerDistance = playerPos.distanceTo(pos)
                val proximity = proximityAlpha(playerDistance)
                if (proximity <= 0.01f) return@mapNotNull null
                
                val text = "${pin.label} §7[${playerDistance.toInt()}m]"
                WaypointData(
                    pos = pos,
                    distance = cameraPos.distanceTo(pos),
                    text = text,
                    displayText = text, // Pinned waypoints are never truncated
                    color = flashColor(pin.color),
                    alpha = 0.5f * proximity,
                    textAlpha = proximity,
                    icon = pin.icon,
                    isFlashing = false // Pins flash by color
                )
            }
        if (waypoints.isEmpty()) return

        WaypointRenderer.renderAllBatched(
            context = context,
            waypoints = waypoints,
            showBeam = true,
            showLine = false,
            showText = true,
            textScale = 0.5f
        )
    }

    private fun purge() {
        val now = System.currentTimeMillis()
        pins.values.removeIf { it.expiresAt < now }
    }
}
