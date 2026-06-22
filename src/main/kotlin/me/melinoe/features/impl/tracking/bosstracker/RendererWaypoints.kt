package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe.mc
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.render.WaypointCache
import me.melinoe.utils.render.WaypointData
import me.melinoe.utils.render.WaypointRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3

/**
 * Renders 3D waypoints in the world for tracked bosses
 */
object RendererWaypoints {
    
    var showWaypoints = true
    var waypointBeams = true
    var showAvailable = true
    var showFighting = true
    var showPortal = true
    
    /**
     * Render waypoints for all tracked bosses
     */
    fun render(context: LevelRenderContext) {
        if (!ServerUtils.isOnTelos()) return
        if (!showWaypoints) return
        
        val player = mc.player ?: return
        val camera = mc.gameRenderer.mainCamera ?: return
        val cameraPos = camera.position()
        val cameraDirection = Vec3.directionFromRotation(camera.xRot(), camera.yRot())
        
        // Fetch current area safely to determine area-based waypoint filtering
        val currentArea = try {
            LocalAPI.getCurrentCharacterArea()
        } catch (e: Exception) {
            ""
        }
        val inShadowlands = currentArea == "Shadowlands"
        val inRadiantIsles = currentArea == "Radiant Isles"

        val waypointsToRender = mutableListOf<WaypointData>()

        for (boss in BossState.getAllBosses()) {
            // The Cog Sentinel and its Cog Stabilizers are the Radiant Isles waypoints
            val isRadiantIslesWaypoint = boss.data == BossData.COG_SENTINEL || boss.data == BossData.COG_STABILIZER

            if (inRadiantIsles) {
                // In the Radiant Isles, only show the Cog Sentinel/Stabilizers and hide every other waypoint
                if (!isRadiantIslesWaypoint) continue
            } else {
                // These are area-locked, so they never show outside the Radiant Isles
                if (isRadiantIslesWaypoint) continue

                val isShadowlandsBoss = boss.name in listOf("Reaper", "Warden", "Herald")

                if (inShadowlands) {
                    // If in shadowlands, hide all other bosses (except shadowlands minibosses, raphael, & defender
                    if (!isShadowlandsBoss && boss.name != "Raphael" && boss.name != "Defender") continue
                } else {
                    // If not in shadowlands, hide shadowlands bosses
                    if (isShadowlandsBoss) continue
                }
            }

            // Skip defeated bosses without portals (unless they are Shadowlands bosses returning to idle)
            if (boss.state == BossState.State.DEFEATED) continue
            
            // Filter by boss state
            val shouldShow = when {
                boss.state == BossState.State.SHADOWLANDS_IDLE -> true
                boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE && !showPortal -> false
                boss.state == BossState.State.ALIVE && boss.calledPlayerName != null && !showFighting -> false
                boss.state == BossState.State.ALIVE && boss.calledPlayerName == null && !showAvailable -> false
                else -> true
            }
            
            if (!shouldShow) continue
            
            val waypoint = BossWaypoint(boss)
            val pos = Vec3(waypoint.pos.x.toDouble(), waypoint.pos.y.toDouble(), waypoint.pos.z.toDouble())
            
            // Calculate distance for fade
            val distance = cameraPos.distanceTo(pos)
            
            // Frustum culling
            if (!isInFrustum(pos, camera)) continue
            
            // Calculate fade alpha
            val fadeAlpha = calculateFadeAlpha(distance)
            if (fadeAlpha <= 0.01f) continue

            // Overlay dark gray text styling for Shadowlands idle bosses
            val color = if (boss.state == BossState.State.SHADOWLANDS_IDLE) {
                floatArrayOf(0.33f, 0.33f, 0.33f)
            } else {
                waypoint.getColor()
            }
            
            // Text Calculation
            val textWorldPos = Vec3(pos.x + 0.5, pos.y + 1.5, pos.z + 0.5)
            val toWaypoint = textWorldPos.subtract(cameraPos).normalize()
            val isLookingAt = cameraDirection.dot(toWaypoint) > 0.95
            val isNearby = distance < 10.0
            
            val text = waypoint.getDisplayText()
            val displayText = if (text.isEmpty()) {
                ""
            } else if (isLookingAt || isNearby) {
                text
            } else {
                WaypointCache.getTruncatedText(text)
            }
            
            waypointsToRender.add(WaypointData(
                pos = pos,
                distance = distance,
                text = text,
                displayText = displayText,
                color = color,
                alpha = 0.5f * fadeAlpha,
                textAlpha = fadeAlpha,
                icon = boss.data.modelIdentifier,
                isFlashing = boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE
            ))
        }
        
        // Draw everything all at once to prevent constant render engine layer flushing
        WaypointRenderer.renderAllBatched(
            context = context,
            waypoints = waypointsToRender,
            showBeam = waypointBeams,
            showLine = false,
            showText = true,
            textScale = 0.5f,
            lineWidth = 2.0f
        )
    }
    
    /**
     * Check if a position is within the camera's view frustum
     */
    private fun isInFrustum(pos: Vec3, camera: Camera): Boolean {
        val cameraPos = camera.position()
        val lookVec = camera.forwardVector()
        
        val toWaypoint = Vec3(
            pos.x - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z - cameraPos.z
        ).normalize()
        
        val dot = lookVec.x() * toWaypoint.x + lookVec.y() * toWaypoint.y + lookVec.z() * toWaypoint.z
        
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
        
        // Fade from 20 to (20 + FADE_DISTANCE) blocks
        if (distance < Constants.MIN_FADE_DISTANCE + Constants.FADE_DISTANCE) {
            val fadeProgress = (distance - Constants.MIN_FADE_DISTANCE) / Constants.FADE_DISTANCE
            return fadeProgress.coerceIn(0.0, 1.0).toFloat()
        }
        
        return 1.0f
    }
}