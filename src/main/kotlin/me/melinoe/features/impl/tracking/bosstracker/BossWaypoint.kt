package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Represents a waypoint for a boss location with rendering capabilities
 */
class BossWaypoint(
    val boss: BossState.TrackedBoss
) {
    val pos: BlockPos = boss.spawnPosition
    val name: String = boss.name
    
    /**
     * Calculate distance from player to this waypoint
     */
    fun distanceToPlayer(): Double {
        val player = mc.player ?: return Double.MAX_VALUE
        val playerPos = player.position()
        return sqrt(
            (playerPos.x - pos.x).pow(2.0) +
            (playerPos.y - pos.y).pow(2.0) +
            (playerPos.z - pos.z).pow(2.0)
        )
    }
    
    /**
     * Get the formatted text to display at the waypoint
     */
    fun getDisplayText(): String {
        val distance = distanceToPlayer().toInt()
        val playerName = boss.calledPlayerName
        
        return buildString {
            append(name)
            if (playerName != null) {
                append(" [")
                append(playerName)
                append("]")
            }
            
            // Add portal countdown timer if boss is defeated with portal active
            if (boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
                val secondsRemaining = boss.portalTimer / 20
                append(" §6[${secondsRemaining}s]") // Gold color for portal timer
            }
            
            append(" §7[${distance}m]")
        }
    }
    
    /**
     * Get the color for this waypoint based on boss state
     */
    fun getColor(): FloatArray {
        return when {
            boss.state == BossState.State.DEFEATED -> floatArrayOf(0.3f, 0.3f, 0.3f) // Dark gray
            boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE -> floatArrayOf(1.0f, 0.84f, 0.0f) // Gold
            boss.calledPlayerName != null -> floatArrayOf(0.0f, 1.0f, 0.0f) // Green
            else -> floatArrayOf(0.95f, 0.95f, 1.0f) // White glass (soft white with slight blue tint)
        }
    }
    
    /**
     * Check if player is looking at this waypoint
     */
    fun isLookingAt(): Boolean {
        val player = mc.player ?: return false
        val camera = mc.gameRenderer.mainCamera
        
        // Get player look vector
        val lookVec = player.lookAngle
        
        // Get vector from player to waypoint
        val playerPos = player.position()
        val waypointVec = Vec3(
            pos.x.toDouble() - playerPos.x,
            pos.y.toDouble() - playerPos.y,
            pos.z.toDouble() - playerPos.z
        ).normalize()
        
        // Calculate dot product (cosine of angle between vectors)
        val dot = lookVec.dot(waypointVec)
        
        // If dot product > 0.98 (about 11 degrees), player is looking at waypoint
        return dot > 0.98
    }
    
    /**
     * Get the teleport command for this waypoint
     */
    fun getTeleportCommand(): String? {
        val playerName = boss.calledPlayerName ?: return null
        return "/tp $playerName"
    }
}
