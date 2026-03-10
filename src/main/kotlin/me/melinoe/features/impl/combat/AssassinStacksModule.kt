package me.melinoe.features.impl.combat

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.events.core.onReceive
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.ServerUtils
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
    
    private val assassinStacksHud by HUDSetting(
        name = "Stacks Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = true,
        default = true,
        description = "Position of the assassin stacks display",
        module = this
    ) render@{ example ->
        if (!enabled && !example) return@render 0 to 0
        
        val stacks = if (example) 5 else getStacks()
        if (stacks == 0) return@render 0 to 0
        
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
    private var lastDebugTime = 0L // To prevent chat spam
    
    private val particleTimeout = 2000L // Reset stacks if no particles for 2 seconds
    private val detectionRadius = 1.5 // Only detect particles within 1.5 blocks of player (assassin stacks)
    
    private val electricSparkPositions = Array(4) { mutableSetOf<RelativeParticlePosition>() }
    private val dustPositions = Array(4) { mutableSetOf<RelativeParticlePosition>() }
    private val layerStacks = IntArray(4)
    
    private val angleTolerance = 12.0 // Degrees - tighter tolerance for more accuracy
    private val positionLifetime = 1000L // Longer lifetime for stable tracking while moving
    
    private val layerYSpacing = 0.6
    
    data class RelativeParticlePosition(
        val angle: Double, // Angle around the player (0-360)
        val distance: Double, // Distance from player
        val timestamp: Long
    ) {
        fun isSimilarTo(other: RelativeParticlePosition): Boolean {
            // Check if angles are similar (accounting for wrap-around at 0/360)
            val angleDiff = kotlin.math.abs(angle - other.angle)
            val normalizedAngleDiff = if (angleDiff > 180) 360 - angleDiff else angleDiff
            return normalizedAngleDiff < angleTolerance && kotlin.math.abs(distance - other.distance) < 0.5
        }
    }
    
    init {
        // Listen for particle packets
        onReceive<ClientboundLevelParticlesPacket> {
            if (!enabled) return@onReceive
            if (!ServerUtils.isOnTelos()) return@onReceive
            
            val char = LocalAPI.getCurrentCharacterClass()
            if (!char.contains("Assassin")) return@onReceive
            
            // Check if it's an electric_spark or dust particle
            val isElectricSpark = particle.type == ParticleTypes.ELECTRIC_SPARK
            val isDust = particle.type == ParticleTypes.DUST
            
            if (!isElectricSpark && !isDust) return@onReceive
            
            val player = mc.player ?: return@onReceive
            val currentTime = System.currentTimeMillis()
            
            // Compensate for player velocity to account for server tick delay
            // Particles are spawned server-side, so they appear at the player's position
            // from ~50-100ms ago (1-2 ticks). We need to adjust for this.
            val velocity = player.deltaMovement
            val tickCompensation = 1.5 // Compensate for ~1.5 ticks of movement
            val compensatedX = player.x - (velocity.x * tickCompensation)
            val compensatedZ = player.z - (velocity.z * tickCompensation)
            
            val compensatedY = player.y - (velocity.y * 0.2)
            
            if (currentTime - lastParticleTime > particleTimeout) {
                layerStacks.fill(0)
                electricSparkPositions.forEach { it.clear() }
                dustPositions.forEach { it.clear() }
            }
            
            var bestLayer = -1
            var minDistance = Double.MAX_VALUE
            var bestDx = 0.0
            var bestDz = 0.0
            var bestDy = 0.0 // For debug
            
            // Find the closest specific layer
            for (i in 0..3) {
                // Calculate relative position from compensated player position
                val dx = x - compensatedX
                
                val expectedLayerY = compensatedY + (layerYSpacing * i)
                val dy = y - expectedLayerY
                
                val dz = z - compensatedZ
                
                val distance = sqrt(dx * dx + dy * dy + dz * dz)
                
                if (distance < minDistance) {
                    minDistance = distance
                    bestLayer = i
                    bestDx = dx
                    bestDz = dz
                    bestDy = dy
                }
            }
            
            // Assign particle
            if (bestLayer != -1 && minDistance <= detectionRadius) {
                val angle = kotlin.math.atan2(bestDz, bestDx) * 180.0 / kotlin.math.PI
                val normalizedAngle = if (angle < 0) angle + 360.0 else angle
                
                val newPosition = RelativeParticlePosition(normalizedAngle, minDistance, currentTime)
                var added = false
                
                if (isElectricSpark) {
                    if (electricSparkPositions[bestLayer].none { it.isSimilarTo(newPosition) }) {
                        electricSparkPositions[bestLayer].add(newPosition)
                        added = true
                    }
                } else if (isDust) {
                    if (dustPositions[bestLayer].none { it.isSimilarTo(newPosition) }) {
                        dustPositions[bestLayer].add(newPosition)
                        added = true
                    }
                }
                
                if (added) {
                    lastParticleTime = currentTime
                }
            }
            
            // Cleanup & recount
            for (i in 0..3) {
                electricSparkPositions[i].removeIf { currentTime - it.timestamp > positionLifetime }
                dustPositions[i].removeIf { currentTime - it.timestamp > positionLifetime }
                
                var stackCount = 0
                val matchedAngles = mutableListOf<Double>()
                
                for (sparkPos in electricSparkPositions[i]) {
                    if (dustPositions[i].any { it.isSimilarTo(sparkPos) }) {
                        matchedAngles.add(sparkPos.angle)
                        stackCount++
                    }
                }
                
                if (stackCount >= 2) {
                    val sortedAngles = matchedAngles.sorted()
                    val expectedSpacing = 22.5
                    val spacingTolerance = 8.0
                    var hasConsistentSpacing = true
                    
                    for (j in 0 until sortedAngles.size - 1) {
                        val spacing = sortedAngles[j + 1] - sortedAngles[j]
                        if (kotlin.math.abs(spacing - expectedSpacing) > spacingTolerance) {
                            hasConsistentSpacing = false
                            break
                        }
                    }
                    
                    if (hasConsistentSpacing) {
                        layerStacks[i] = stackCount
                    }
                } else {
                    layerStacks[i] = stackCount
                }
            }
            
            currentStacks = layerStacks.sum()
        }
    }
    
    override fun onEnable() {
        super.onEnable()
        resetState()
    }
    
    override fun onDisable() {
        super.onDisable()
        resetState()
    }
    
    private fun resetState() {
        currentStacks = 0
        lastParticleTime = 0L
        layerStacks.fill(0)
        electricSparkPositions.forEach { it.clear() }
        dustPositions.forEach { it.clear() }
    }
    
    fun getStacks(): Int {
        if (System.currentTimeMillis() - lastParticleTime > particleTimeout) {
            resetState()
        }
        return currentStacks
    }
}