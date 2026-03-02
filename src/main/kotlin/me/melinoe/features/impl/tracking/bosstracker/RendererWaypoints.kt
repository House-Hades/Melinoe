package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe.mc
import me.melinoe.utils.render.WaypointRenderer
import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3

/**
 * Renders 3D waypoints in the world for tracked bosses
 */
object RendererWaypoints {
    
    var showWaypoints = true
    var waypointBeams = true
    var maxTextScale = 1.0
    var showAvailable = true
    var showFighting = true
    var showPortal = true
    var maxRenderDistance = 1000.0
    var fadeDistance = 50.0
    
    /**
     * Render waypoints for all tracked bosses
     */
    fun render(context: net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext) {
        if (!showWaypoints) return
        
        val player = mc.player ?: return
        val camera = mc.gameRenderer.mainCamera
        val cameraPos = camera.position
        
        for (boss in BossState.getAllBosses()) {
            // Skip defeated bosses without portals
            if (boss.state == BossState.State.DEFEATED) continue
            
            // Filter by boss state
            val shouldShow = when {
                boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE && !showPortal -> false
                boss.state == BossState.State.ALIVE && boss.calledPlayerName != null && !showFighting -> false
                boss.state == BossState.State.ALIVE && boss.calledPlayerName == null && !showAvailable -> false
                else -> true
            }
            
            if (!shouldShow) continue
            
            val waypoint = BossWaypoint(boss)
            val pos = Vec3(waypoint.pos.x.toDouble(), waypoint.pos.y.toDouble(), waypoint.pos.z.toDouble())
            
            // Calculate distance for culling
            val distance = cameraPos.distanceTo(pos)
            
            // Distance-based culling
            if (distance > maxRenderDistance) continue
            
            // Frustum culling
            if (!isInFrustum(pos, camera)) continue
            
            // Calculate fade alpha
            val fadeAlpha = calculateFadeAlpha(distance)
            if (fadeAlpha <= 0.01f) continue
            
            val color = waypoint.getColor()
            val text = waypoint.getDisplayText()
            
            // Render waypoint
            WaypointRenderer.renderWaypoint(
                context = context,
                pos = pos,
                text = text,
                color = color,
                alpha = 0.5f * fadeAlpha,
                showBeam = waypointBeams,
                showLine = false,
                showText = true,
                textShadow = true,
                textScale = 0.5f,
                maxTextScale = maxTextScale,
                lineWidth = 2.0f,
                textAlpha = fadeAlpha
            )
        }
    }
    
    /**
     * Check if a position is within the camera's view frustum
     */
    private fun isInFrustum(pos: Vec3, camera: Camera): Boolean {
        val cameraPos = camera.position
        val lookVec = camera.lookVector
        
        val toWaypoint = Vec3(
            pos.x - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z - cameraPos.z
        ).normalize()
        
        val dot = lookVec.x * toWaypoint.x + lookVec.y * toWaypoint.y + lookVec.z * toWaypoint.z
        
        return dot > Constants.FRUSTUM_DOT_THRESHOLD
    }
    
    /**
     * Calculate fade alpha based on distance
     */
    private fun calculateFadeAlpha(distance: Double): Float {
        // Completely hide within 20 blocks
        if (distance < Constants.MIN_FADE_DISTANCE) {
            return 0.0f
        }
        
        // Fade from 20 to 40 blocks
        if (distance < Constants.MIN_FADE_DISTANCE + Constants.FADE_RANGE) {
            val fadeProgress = (distance - Constants.MIN_FADE_DISTANCE) / Constants.FADE_RANGE
            return fadeProgress.coerceIn(0.0, 1.0).toFloat()
        }
        
        // Fade out when approaching max distance
        if (distance > maxRenderDistance - fadeDistance) {
            val fadeProgress = (maxRenderDistance - distance) / fadeDistance
            return fadeProgress.coerceIn(0.0, 1.0).toFloat()
        }
        
        return 1.0f
    }
}
