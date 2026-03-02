package me.melinoe.features.impl.tracking.bosstracker

import java.util.regex.Pattern

/**
 * Constants and patterns for boss tracking
 */
object Constants {
    // Chat patterns
    val BOSS_DEFEATED_PATTERN: Pattern = Pattern.compile("^(\\w+) has been defeated(?:\\s+\\((\\d+)/(\\d+)\\))?")
    val BOSS_SPAWNED_PATTERN: Pattern = Pattern.compile("^(\\w+) has spawned at ([0-9.-]+), ([0-9.-]+), ([0-9.-]+)")
    val POTENTIAL_BOSS_PATTERN: Pattern = Pattern.compile("^\\[(\\w+)]")
    val BOSS_ITEM_NAME_PATTERN: Pattern = Pattern.compile("^» \\[(\\w+)] «")
    
    // Timers (in ticks, 20 ticks = 1 second)
    const val PORTAL_TIMER_NORMAL = 600  // 30 seconds
    const val PORTAL_TIMER_RAPHAEL = 1200  // 60 seconds
    const val DISTANCE_UPDATE_INTERVAL = 20  // 1 second
    
    // Rendering
    const val DISTANCE_MARKER = "♦"
    const val MIN_FADE_DISTANCE = 20.0
    const val FADE_RANGE = 20.0
    const val FRUSTUM_DOT_THRESHOLD = -0.2
    
    // Dimensions
    const val DIMENSION_REALM = "realm"
    const val DIMENSION_HUB = "hub"
    const val DIMENSION_DUNGEON = "dungeon"
    const val DIMENSION_DAILY = "daily"
}
