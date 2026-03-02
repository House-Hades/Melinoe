package me.melinoe.features.impl.visual.healthbar

/**
 * Constants used by the Health Bar system.
 */
object Constants {
    // Health bar colors
    const val BORDER_COLOR = 0xFF000000.toInt()
    const val BACKGROUND_COLOR = 0xFF202020.toInt()
    const val NORMAL_HEALTH_COLOR = 0xFF40CC40.toInt()
    const val MEDIUM_HEALTH_COLOR = 0xFFFFCC40.toInt()
    const val LOW_HEALTH_COLOR = 0xFFCC3030.toInt()
    
    // Animation constants
    const val HEALTH_LERP_SPEED = 0.08f
    const val DAMAGE_FLASH_DURATION = 200L
    
    // Warning constants
    const val WARNING_COOLDOWN = 5000L
    const val SOUND_REPEAT_DELAY = 200L
    
    // Grace period constants
    const val WORLD_JOIN_GRACE_PERIOD = 5000L
    const val MINECRAFT_DEFAULT_MAX_HEALTH = 20f
    
    // Rendering constants
    const val BORDER_WIDTH_3D = 0.03f
    const val BORDER_WIDTH_HUD = 1f
}
