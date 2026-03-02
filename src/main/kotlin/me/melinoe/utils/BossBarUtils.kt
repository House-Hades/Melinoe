package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.utils.data.DungeonData
import net.minecraft.client.gui.components.LerpingBossEvent
import java.util.*

/**
 * Utility for accessing and monitoring boss bars.
 * Provides information about active boss fights and dungeon status.
 * 
 * Boss bars are updated via BossHealthOverlayMixin which injects into the render method.
 */
object BossBarUtils {
    private var bossBarMap: Map<UUID, LerpingBossEvent> = emptyMap()
    private var lastUpdateTick = 0

    /**
     * Called from BossHealthOverlayMixin to update the boss bar map.
     * This is called every frame when boss bars are being rendered.
     */
    @JvmStatic
    fun updateBossBarMap(newMap: Map<UUID, LerpingBossEvent>) {
        bossBarMap = newMap
    }

    /**
     * Get the current boss bar map.
     */
    fun getBossBarMap(): Map<UUID, LerpingBossEvent> = bossBarMap

    /**
     * Check if there are any active boss bars (player is in combat with a boss).
     * Only counts bosses with health > 0% (exactly 0% means defeated).
     * Defeated bosses have exactly 0% health (progress = 0.0f).
     * This aligns with how BossBarProgressionFeature checks: progress > 0.0f
     * @return true if there is at least one active boss with health > 0%, false otherwise
     */
    fun hasActiveBossBar(): Boolean {
        if (bossBarMap.isEmpty()) {
            return false
        }

        // Check if any boss bars have health > 0% (defeated bosses have exactly 0%)
        // This ensures we only block menus when actually fighting, not when boss is defeated at 0%
        // Matches BossBarProgressionFeature which checks progress > 0.0f for valid health
        for (bossBar in bossBarMap.values) {
            val healthPercent = bossBar.progress

            // Boss must have health > 0% to be considered "active"
            // Defeated bosses have exactly 0.0f, which will not block menus
            if (healthPercent > 0.0f) {
                return true
            }
        }

        return false // All bosses have 0% health - defeated
    }

    /**
     * Check if player is currently in a dungeon.
     * @return true if player is in a dungeon, false otherwise
     */
    fun isInDungeon(): Boolean {
        return try {
            val currentDungeon = DungeonData.findByKey(LocalAPI.getCurrentCharacterArea())
            currentDungeon != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if player is in dungeon AND has active boss bar.
     * @return true if both conditions are met (player should not open menus)
     */
    fun shouldBlockMenus(): Boolean {
        return isInDungeon() && hasActiveBossBar()
    }
}
