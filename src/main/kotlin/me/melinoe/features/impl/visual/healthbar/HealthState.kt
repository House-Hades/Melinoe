package me.melinoe.features.impl.visual.healthbar

/**
 * Manages the state of health animations and tracking.
 */
class HealthState {
    var displayedHealth = 0f
        private set
    
    var lastHealth = 0f
        private set
    
    var lastHealthPercentage = 100f
    
    var damageFlashTime = 0L
        private set
    
    // Grace period tracking
    var worldJoinTime = 0L
    var previousRealm: String = ""
    var previousDimension: String = ""
    var isInGracePeriod = false
    var lastMaxHealth = 0f
    
    /**
     * Initialize displayed health on first render
     */
    fun initialize(currentHealth: Float) {
        if (displayedHealth == 0f && lastHealth == 0f) {
            displayedHealth = currentHealth
            lastHealth = currentHealth
        }
    }
    
    /**
     * Update health with optional smooth animation
     */
    fun updateHealth(currentHealth: Float, smoothHealth: Boolean) {
        if (smoothHealth) {
            when {
                currentHealth < displayedHealth -> displayedHealth = currentHealth
                currentHealth > displayedHealth -> {
                    val healthDiff = currentHealth - displayedHealth
                    displayedHealth += healthDiff * Constants.HEALTH_LERP_SPEED
                    if (kotlin.math.abs(healthDiff) < 0.1f) {
                        displayedHealth = currentHealth
                    }
                }
            }
        } else {
            displayedHealth = currentHealth
        }
    }
    
    /**
     * Check for damage and trigger flash effect
     */
    fun checkDamage(currentHealth: Float, damageFlashEnabled: Boolean) {
        if (damageFlashEnabled && currentHealth < lastHealth) {
            damageFlashTime = System.currentTimeMillis()
        }
        lastHealth = currentHealth
    }
    
    /**
     * Get health bar color with optional damage flash
     */
    fun getHealthBarColor(
        healthPercentage: Float,
        lowThreshold: Double,
        mediumThreshold: Double,
        damageFlashEnabled: Boolean
    ): Int {
        val healthPercent = healthPercentage * 100f
        
        var color = when {
            healthPercent <= lowThreshold -> Constants.LOW_HEALTH_COLOR
            healthPercent < mediumThreshold -> {
                val range = mediumThreshold - lowThreshold
                val position = (healthPercent - lowThreshold) / range
                interpolateColor(Constants.LOW_HEALTH_COLOR, Constants.MEDIUM_HEALTH_COLOR, position.toFloat())
            }
            healthPercent < 100f -> {
                val range = 100f - mediumThreshold
                val position = (healthPercent - mediumThreshold) / range
                interpolateColor(Constants.MEDIUM_HEALTH_COLOR, Constants.NORMAL_HEALTH_COLOR, position.toFloat())
            }
            else -> Constants.NORMAL_HEALTH_COLOR
        }
        
        // Apply damage flash
        if (damageFlashEnabled) {
            val timeSinceFlash = System.currentTimeMillis() - damageFlashTime
            if (timeSinceFlash < Constants.DAMAGE_FLASH_DURATION) {
                val flashIntensity = 1f - (timeSinceFlash.toFloat() / Constants.DAMAGE_FLASH_DURATION)
                color = interpolateColor(color, 0xFFFF0000.toInt(), flashIntensity * 0.5f)
            }
        }
        
        return color
    }
    
    /**
     * Reset all state
     */
    fun reset() {
        displayedHealth = 0f
        lastHealth = 0f
        lastHealthPercentage = 100f
        damageFlashTime = 0L
    }
    
    companion object {
        /**
         * Interpolate between two ARGB colors
         */
        fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
            val clampedRatio = ratio.coerceIn(0f, 1f)
            val invRatio = 1f - clampedRatio
            
            val a = ((color1 shr 24 and 0xFF).toFloat() * invRatio + (color2 shr 24 and 0xFF).toFloat() * clampedRatio).toInt()
            val r = ((color1 shr 16 and 0xFF).toFloat() * invRatio + (color2 shr 16 and 0xFF).toFloat() * clampedRatio).toInt()
            val g = ((color1 shr 8 and 0xFF).toFloat() * invRatio + (color2 shr 8 and 0xFF).toFloat() * clampedRatio).toInt()
            val b = ((color1 and 0xFF).toFloat() * invRatio + (color2 and 0xFF).toFloat() * clampedRatio).toInt()
            
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
