package me.melinoe.features.impl.visual.healthbar

import me.melinoe.Melinoe
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.equalsOneOf
import me.melinoe.utils.playSoundAtPlayer
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Manages health warning system including sounds, titles, and grace periods.
 */
class Warnings(private val mc: Minecraft) {
    // Warning state
    var lowHealthWarningTriggered = false
    var mediumHealthWarningTriggered = false
    var lowHealthTitleEndTime = 0L
    var mediumHealthTitleEndTime = 0L
    private var lastLowWarningTime = 0L
    private var lastMediumWarningTime = 0L
    
    // Sound tasks
    private var lowHealthSoundTask: ScheduledFuture<*>? = null
    private var mediumHealthSoundTask: ScheduledFuture<*>? = null
    private val soundExecutor = Executors.newSingleThreadScheduledExecutor()
    
    // Grace period tracking
    var worldJoinTime = 0L
    var previousRealm: String = ""
    var previousDimension: String = ""
    var isInGracePeriod = false
    var lastMaxHealth = 0f
    
    /**
     * Check and trigger health warnings based on health percentage changes
     */
    fun checkHealthWarnings(
        healthPercentage: Float,
        lastHealthPercentage: Float,
        currentMaxHealth: Float,
        lowThreshold: Double,
        mediumThreshold: Double,
        enableLowHealth: Boolean,
        enableMediumHealth: Boolean,
        lowHealthHudEnabled: Boolean,
        mediumHealthHudEnabled: Boolean,
        lowHealthSoundSettings: () -> Triple<String, Float, Float>,
        mediumHealthSoundSettings: () -> Triple<String, Float, Float>,
        lowHealthSoundRepeat: Int,
        mediumHealthSoundRepeat: Int
    ): Float {
        val healthPercent = healthPercentage * 100f
        val currentTime = System.currentTimeMillis()
        
        // Smart grace period check
        val timeBasedGrace = currentTime - worldJoinTime < Constants.WORLD_JOIN_GRACE_PERIOD
        val healthNotLoaded = currentMaxHealth == Constants.MINECRAFT_DEFAULT_MAX_HEALTH && isInGracePeriod
        val healthJustChanged = currentMaxHealth != lastMaxHealth
        
        if (timeBasedGrace || healthNotLoaded || healthJustChanged) {
            lastMaxHealth = currentMaxHealth
            if (currentMaxHealth != Constants.MINECRAFT_DEFAULT_MAX_HEALTH) {
                isInGracePeriod = false
            }
            return healthPercent
        }
        
        lastMaxHealth = currentMaxHealth
        
        // Cancel sounds if titles have expired
        if (currentTime > lowHealthTitleEndTime && currentTime > mediumHealthTitleEndTime) {
            cancelActiveSounds()
        }
        
        // Reset warnings when health goes above medium threshold
        if (healthPercent > mediumThreshold) {
            lowHealthWarningTriggered = false
            mediumHealthWarningTriggered = false
            lowHealthTitleEndTime = 0L
            mediumHealthTitleEndTime = 0L
            cancelActiveSounds()
            return healthPercent
        }
        
        // Priority 1: Low health warning
        if (healthPercent <= lowThreshold && 
            lastHealthPercentage > lowThreshold && 
            !lowHealthWarningTriggered) {
            
            if (currentTime - lastLowWarningTime >= Constants.WARNING_COOLDOWN) {
                lowHealthWarningTriggered = true
                mediumHealthWarningTriggered = true
                lastLowWarningTime = currentTime
                
                val titleDuration = getTitleDuration(lowHealthSoundRepeat)
                lowHealthTitleEndTime = currentTime + titleDuration
                mediumHealthTitleEndTime = 0L
                
                cancelActiveSounds()
                if (enableLowHealth && lowHealthHudEnabled) {
                    val (soundId, volume, pitch) = lowHealthSoundSettings()
                    playWarningSoundRepeating(soundId, volume, pitch, lowHealthSoundRepeat, true)
                }
            }
            return healthPercent
        }
        
        // Priority 2: Medium health warning
        if (healthPercent <= mediumThreshold && 
            lastHealthPercentage > mediumThreshold && 
            !mediumHealthWarningTriggered &&
            !lowHealthWarningTriggered) {
            
            if (currentTime - lastMediumWarningTime >= Constants.WARNING_COOLDOWN) {
                mediumHealthWarningTriggered = true
                lastMediumWarningTime = currentTime
                
                val titleDuration = getTitleDuration(mediumHealthSoundRepeat)
                mediumHealthTitleEndTime = currentTime + titleDuration
                
                cancelActiveSounds()
                if (enableMediumHealth && mediumHealthHudEnabled) {
                    val (soundId, volume, pitch) = mediumHealthSoundSettings()
                    playWarningSoundRepeating(soundId, volume, pitch, mediumHealthSoundRepeat, false)
                }
            }
        }
        
        return healthPercent
    }
    
    /**
     * Handle world change - trigger grace period when switching realms
     */
    fun handleWorldChange() {
        val currentRealm = LocalAPI.getCurrentCharacterWorld()
        val level = mc.level
        val currentDimension = level?.dimension()?.location()?.path ?: ""
        
        if (currentRealm.isEmpty() || currentDimension.isEmpty()) return
        
        if (previousRealm.isEmpty() || previousDimension.isEmpty()) {
            previousRealm = currentRealm
            previousDimension = currentDimension
            triggerGracePeriod()
            return
        }
        
        if (currentRealm == previousRealm && currentDimension == previousDimension) return
        
        val wasDungeon = previousDimension == "dungeon"
        val isDungeonNow = currentDimension == "dungeon"
        val isDungeonTransition = wasDungeon != isDungeonNow
        
        val isGoingToSafeZone = currentDimension.equalsOneOf("hub", "daily")
        val wasInSafeZone = previousDimension.equalsOneOf("hub", "daily")
        
        if (isGoingToSafeZone || 
            (wasInSafeZone && currentDimension == "realm") ||
            (previousDimension == "realm" && currentDimension == "realm" && currentRealm != previousRealm)) {
            triggerGracePeriod()
        }
        
        previousRealm = currentRealm
        previousDimension = currentDimension
    }
    
    /**
     * Trigger grace period to prevent false warnings
     */
    fun triggerGracePeriod() {
        worldJoinTime = System.currentTimeMillis()
        isInGracePeriod = true
        lowHealthWarningTriggered = false
        mediumHealthWarningTriggered = false
        lowHealthTitleEndTime = 0L
        mediumHealthTitleEndTime = 0L
        cancelActiveSounds()
        lastMaxHealth = 0f
    }
    
    /**
     * Cancel all active sound tasks
     */
    fun cancelActiveSounds() {
        lowHealthSoundTask?.cancel(false)
        mediumHealthSoundTask?.cancel(false)
        lowHealthSoundTask = null
        mediumHealthSoundTask = null
    }
    
    /**
     * Play warning sound repeatedly at intervals
     */
    private fun playWarningSoundRepeating(
        soundId: String,
        volume: Float,
        pitch: Float,
        repeatCount: Int,
        isLowHealth: Boolean
    ) {
        var currentRepeat = 0
        
        val task = soundExecutor.scheduleAtFixedRate({
            if (currentRepeat >= repeatCount) {
                if (isLowHealth) {
                    lowHealthSoundTask?.cancel(false)
                    lowHealthSoundTask = null
                } else {
                    mediumHealthSoundTask?.cancel(false)
                    mediumHealthSoundTask = null
                }
                return@scheduleAtFixedRate
            }
            
            currentRepeat++
            
            mc.execute {
                try {
                    val soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                        net.minecraft.resources.ResourceLocation.parse(soundId)
                    )
                    playSoundAtPlayer(soundEvent, volume, pitch)
                } catch (e: Exception) {
                    try {
                        playSoundAtPlayer(
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                            volume,
                            pitch
                        )
                    } catch (ignored: Exception) {}
                }
            }
        }, 0, Constants.SOUND_REPEAT_DELAY, TimeUnit.MILLISECONDS)
        
        if (isLowHealth) {
            lowHealthSoundTask = task
        } else {
            mediumHealthSoundTask = task
        }
    }
    
    /**
     * Calculate title duration based on sound repeat count
     */
    private fun getTitleDuration(repeatCount: Int): Long {
        return (repeatCount - 1) * Constants.SOUND_REPEAT_DELAY + 500L
    }
    
    /**
     * Get flashing color for health warnings
     */
    fun getFlashingColor(baseColor: Int, titleEndTime: Long, currentTime: Long, repeatCount: Int): Int {
        val titleDuration = getTitleDuration(repeatCount)
        val timeSinceStart = (currentTime - (titleEndTime - titleDuration)).coerceAtLeast(0L)
        val isFlashPhase = (timeSinceStart / Constants.SOUND_REPEAT_DELAY) % 2 == 0L
        return if (isFlashPhase) lightenColor(baseColor, 0.3f) else baseColor
    }
    
    companion object {
        /**
         * Lighten a color by blending with white
         */
        fun lightenColor(color: Int, amount: Float): Int {
            val clampedAmount = amount.coerceIn(0f, 1f)
            val a = (color shr 24 and 0xFF)
            val r = (color shr 16 and 0xFF)
            val g = (color shr 8 and 0xFF)
            val b = (color and 0xFF)
            val newR = (r + (255 - r) * clampedAmount).toInt().coerceIn(0, 255)
            val newG = (g + (255 - g) * clampedAmount).toInt().coerceIn(0, 255)
            val newB = (b + (255 - b) * clampedAmount).toInt().coerceIn(0, 255)
            return (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }
    }
    
    /**
     * Reset all warning state
     */
    fun reset() {
        lowHealthWarningTriggered = false
        mediumHealthWarningTriggered = false
        lowHealthTitleEndTime = 0L
        mediumHealthTitleEndTime = 0L
        lastLowWarningTime = 0L
        lastMediumWarningTime = 0L
        isInGracePeriod = false
        lastMaxHealth = 0f
        cancelActiveSounds()
    }
}
