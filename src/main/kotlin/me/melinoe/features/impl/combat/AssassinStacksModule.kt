package me.melinoe.features.impl.combat

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.events.core.onReceive
import me.melinoe.utils.Color
import me.melinoe.utils.render.textDim
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.core.particles.ParticleTypes
import kotlin.math.sqrt

/**
 * Assassin Stacks Module - displays assassin stack counters using particle detection.
 * Detects electric_spark particles in a circle around the player.
 */
object AssassinStacksModule : Module(
    name = "Assassin Stacks",
    category = Category.COMBAT,
    description = "Displays assassin stack counters using particle detection."
) {

    // Settings
    private val nameColor by ColorSetting("Name Color", Color(0xFF7CFFB2.toInt()), desc = "Color for the name text")
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for the value text")
    private val madScientist by BooleanSetting("Mad Scientist", false, desc = "Show Mad Scientist stacks")
    
    private val assassinStacksHud by HUDSetting(
        name = "Stacks Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = true,
        description = "Position of the assassin stacks display",
        module = this
    ) render@{ example ->
        // Return early if disabled and not in example mode
        if (!enabled && !example) return@render 0 to 0
        
        val stacks = if (example) 5 else getStacks()
        if (stacks == 0 && !example) return@render 0 to 0
        
        // Draw stack count
        val nameText = "Stacks: "
        val valueText = "$stacks"
        
        var width = 0
        val (nameWidth, _) = textDim(nameText, 0, 0, nameColor, true)
        width += nameWidth
        val (valueWidth, height) = textDim(valueText, width, 0, valueColor, true)
        width += valueWidth
        
        width to height
    }

    // Stack tracking
    private var currentStacks = 0
    private var lastParticleTime = 0L
    private val particleTimeout = 2000L // Reset stacks if no particles for 2 seconds
    private val detectionRadius = 1.5 // Only detect particles within 1.5 blocks of player (assassin stacks)
    
    // Track both electric_spark and dust particles at each angle
    private val electricSparkPositions = mutableSetOf<RelativeParticlePosition>()
    private val dustPositions = mutableSetOf<RelativeParticlePosition>()
    private val angleTolerance = 12.0 // Degrees - tighter tolerance for more accuracy
    private val positionLifetime = 1000L // Longer lifetime for stable tracking while moving
    
    data class RelativeParticlePosition(
        val angle: Double, // Angle around the player (0-360)
        val distance: Double, // Distance from player
        val timestamp: Long
    ) {
        fun isSimilarTo(other: RelativeParticlePosition): Boolean {
            // Check if angles are similar (accounting for wrap-around at 0/360)
            val angleDiff = kotlin.math.abs(angle - other.angle)
            val normalizedAngleDiff = if (angleDiff > 180) 360 - angleDiff else angleDiff
            
            return normalizedAngleDiff < angleTolerance && 
                   kotlin.math.abs(distance - other.distance) < 0.5
        }
    }

    init {
        // Listen for particle packets
        onReceive<ClientboundLevelParticlesPacket> {
            if (!enabled) return@onReceive
            if (!me.melinoe.utils.ServerUtils.isOnTelos()) return@onReceive
            
            // Check if it's an electric_spark or dust particle
            val isElectricSpark = particle.type == ParticleTypes.ELECTRIC_SPARK
            val isDust = particle.type == ParticleTypes.DUST
            
            if (!isElectricSpark && !isDust) return@onReceive
            
            val player = mc.player ?: return@onReceive
            
            // Compensate for player velocity to account for server tick delay
            // Particles are spawned server-side, so they appear at the player's position
            // from ~50-100ms ago (1-2 ticks). We need to adjust for this.
            val velocity = player.deltaMovement
            val tickCompensation = 1.5 // Compensate for ~1.5 ticks of movement
            val compensatedX = player.x - (velocity.x * tickCompensation)
            val compensatedY = player.y - (velocity.y * tickCompensation)
            val compensatedZ = player.z - (velocity.z * tickCompensation)
            
            // Calculate relative position from compensated player position
            val dx = x - compensatedX
            val dy = y - compensatedY
            val dz = z - compensatedZ
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            
            // Check if particle is within detection radius (very close to player)
            if (distance <= detectionRadius) {
                val currentTime = System.currentTimeMillis()
                
                // Calculate angle around player (in degrees, 0-360)
                val angle = kotlin.math.atan2(dz, dx) * 180.0 / kotlin.math.PI
                val normalizedAngle = if (angle < 0) angle + 360 else angle
                
                val newPosition = RelativeParticlePosition(normalizedAngle, distance, currentTime)
                
                // Clean up old positions aggressively
                electricSparkPositions.removeIf { currentTime - it.timestamp > positionLifetime }
                dustPositions.removeIf { currentTime - it.timestamp > positionLifetime }
                
                // Reset stacks if too much time has passed since last particle
                if (currentTime - lastParticleTime > particleTimeout) {
                    currentStacks = 0
                    electricSparkPositions.clear()
                    dustPositions.clear()
                }
                
                // Add to the appropriate set
                if (isElectricSpark) {
                    val isNewPosition = electricSparkPositions.none { it.isSimilarTo(newPosition) }
                    if (isNewPosition) {
                        electricSparkPositions.add(newPosition)
                        lastParticleTime = currentTime
                    }
                } else if (isDust) {
                    val isNewPosition = dustPositions.none { it.isSimilarTo(newPosition) }
                    if (isNewPosition) {
                        dustPositions.add(newPosition)
                        lastParticleTime = currentTime
                    }
                }
                
                // Count stacks: only count angles where BOTH electric_spark AND dust exist
                var stackCount = 0
                val matchedAngles = mutableListOf<Double>()
                
                for (sparkPos in electricSparkPositions) {
                    if (dustPositions.any { it.isSimilarTo(sparkPos) }) {
                        matchedAngles.add(sparkPos.angle)
                        stackCount++
                    }
                }
                
                // Verify consistent spacing if we have multiple stacks
                // Assassin stacks spawn clockwise with ~22.5° spacing (360° / 16 max stacks)
                if (stackCount >= 2) {
                    // Sort angles to check spacing
                    val sortedAngles = matchedAngles.sorted()
                    
                    // Expected spacing is always ~22.5° (for max 16 stacks)
                    val expectedSpacing = 22.5
                    val spacingTolerance = 8.0 // Allow some variance
                    
                    // Check if consecutive angles have consistent spacing
                    var hasConsistentSpacing = true
                    for (i in 0 until sortedAngles.size - 1) {
                        val spacing = sortedAngles[i + 1] - sortedAngles[i]
                        
                        // Check if spacing is close to expected ~22.5°
                        if (kotlin.math.abs(spacing - expectedSpacing) > spacingTolerance) {
                            hasConsistentSpacing = false
                            break
                        }
                    }
                    
                    // Update stack count if spacing is consistent
                    if (hasConsistentSpacing) {
                        currentStacks = stackCount.coerceAtMost(16)
                    }
                    // If spacing is inconsistent, keep the previous count (don't reset to 0)
                    // This makes the display "sticky" when moving fast
                } else if (stackCount == 1) {
                    // For 1 stack, we can't verify spacing, so accept it
                    currentStacks = stackCount
                }
                // If stackCount is 0, currentStacks will naturally decay via the timeout check
            }
        }
    }

    override fun onEnable() {
        super.onEnable()
        currentStacks = 0
        lastParticleTime = 0L
        electricSparkPositions.clear()
        dustPositions.clear()
    }

    override fun onDisable() {
        super.onDisable()
        currentStacks = 0
        electricSparkPositions.clear()
        dustPositions.clear()
    }
    
    /**
     * Get the current stack count
     */
    fun getStacks(): Int {
        // Reset if timeout exceeded
        if (System.currentTimeMillis() - lastParticleTime > particleTimeout) {
            currentStacks = 0
        }
        return currentStacks
    }
}
