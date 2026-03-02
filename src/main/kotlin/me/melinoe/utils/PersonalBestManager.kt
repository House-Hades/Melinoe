package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.persistence.PersonalBestRecord
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey
import kotlin.math.abs

/**
 * Manages personal best times for dungeons and boss splits.
 * Handles storage, retrieval, and comparison of personal best times.
 * Now uses type-safe TrackingKey API for compile-time safety.
 */
object PersonalBestManager {

    /**
     * Gets the current personal best time for a dungeon.
     * @param dungeon The dungeon to get the PB for
     * @return The personal best time in seconds, or -1 if no PB exists
     */
    fun getDungeonPersonalBest(dungeon: DungeonData): Float {
        val record = TypeSafeDataAccess.get(TrackingKey.PersonalBest(dungeon.areaName))
        return record?.time ?: -1f
    }
    
    /**
     * Gets the current personal best record with metadata for a dungeon.
     * @param dungeon The dungeon to get the PB record for
     * @return The personal best record, or null if no PB exists
     */
    fun getDungeonPersonalBestRecord(dungeon: DungeonData): PersonalBestRecord? {
        return TypeSafeDataAccess.get(TrackingKey.PersonalBest(dungeon.areaName))
    }

    /**
     * Gets the current personal best time for a boss split.
     * @param boss The boss to get the PB for
     * @return The personal best time in seconds, or -1 if no PB exists
     */
    fun getBossPersonalBest(boss: BossData): Float {
        val record = TypeSafeDataAccess.get(TrackingKey.PersonalBest(boss.label))
        return record?.time ?: -1f
    }
    
    /**
     * Gets the current personal best record with metadata for a boss split.
     * @param boss The boss to get the PB record for
     * @return The personal best record, or null if no PB exists
     */
    fun getBossPersonalBestRecord(boss: BossData): PersonalBestRecord? {
        return TypeSafeDataAccess.get(TrackingKey.PersonalBest(boss.label))
    }

    /**
     * Updates the personal best time for a dungeon if the new time is better.
     * @param dungeon The dungeon to update
     * @param newTime The new completion time in seconds
     * @return true if a new personal best was set, false otherwise
     */
    fun updateDungeonPersonalBest(dungeon: DungeonData, newTime: Float): Boolean {
        return TypeSafeDataAccess.updatePersonalBestIfBetter(
            TrackingKey.PersonalBest(dungeon.areaName),
            newTime
        )
    }
    
    /**
     * Updates the personal best time for a dungeon with boss splits.
     * @param dungeon The dungeon to update
     * @param newTime The new completion time in seconds
     * @param splits Map of boss names to their split times
     * @return true if a new personal best was set, false otherwise
     */
    fun updateDungeonPersonalBestWithSplits(dungeon: DungeonData, newTime: Float, splits: Map<String, Float>): Boolean {
        return TypeSafeDataAccess.updatePersonalBestIfBetter(
            TrackingKey.PersonalBest(dungeon.areaName),
            newTime,
            splits
        )
    }

    /**
     * Updates the personal best time for a boss split if the new time is better.
     * @param boss The boss to update
     * @param newTime The new split time in seconds
     * @return true if a new personal best was set, false otherwise
     */
    fun updateBossPersonalBest(boss: BossData, newTime: Float): Boolean {
        return TypeSafeDataAccess.updatePersonalBestIfBetter(
            TrackingKey.PersonalBest(boss.label),
            newTime
        )
    }

    /**
     * Formats a time in seconds to MMmSSs format.
     * @param seconds The time in seconds
     * @return Formatted time string
     */
    fun formatTime(seconds: Float): String {
        val minutes = (seconds / 60).toInt()
        val secs = seconds % 60

        return if (minutes == 0) {
            String.format("%.0fs", secs)
        } else {
            String.format("%dm%02.0fs", minutes, secs)
        }
    }

    /**
     * Formats a time in seconds with decimals for times under a minute.
     * @param seconds The time in seconds
     * @return Formatted time string with decimals if under a minute
     */
    fun formatTimeWithDecimals(seconds: Float): String {
        val minutes = (seconds / 60).toInt()
        val secs = seconds % 60

        return if (minutes == 0) {
            String.format("%.1fs", secs).replace(',', '.')
        } else {
            String.format("%dm%04.1fs", minutes, secs).replace(',', '.')
        }
    }

    /**
     * Formats a time difference with decimals for times under a minute.
     * @param difference The time difference in seconds
     * @return Formatted difference string with decimals if under a minute
     */
    fun formatTimeDifferenceWithDecimals(difference: Float): String {
        if (difference == 0f) return "±0.0s"

        val sign = if (difference > 0) "+" else "-"
        val absDiff = abs(difference)

        // If less than a minute, show only seconds with decimals
        return if (absDiff < 60) {
            sign + String.format("%.1fs", absDiff).replace(',', '.')
        } else {
            // Otherwise show minutes and seconds (no decimals for over a minute)
            sign + formatTime(absDiff)
        }
    }

    /**
     * Calculates the time difference between two times.
     * @param currentTime The current time
     * @param personalBest The personal best time
     * @return The difference in seconds (positive means current is slower)
     */
    fun calculateTimeDifference(currentTime: Float, personalBest: Float): Float {
        return currentTime - personalBest
    }

    /**
     * Formats a time difference for display.
     * @param difference The time difference in seconds
     * @return Formatted difference string with + or - prefix
     */
    fun formatTimeDifference(difference: Float): String {
        if (difference == 0f) return "±0s"

        val sign = if (difference > 0) "+" else "-"
        val absDiff = abs(difference)

        // If less than a minute, show only seconds
        return if (absDiff < 60) {
            sign + String.format("%.0fs", absDiff)
        } else {
            // Otherwise show minutes and seconds
            sign + formatTime(absDiff)
        }
    }

    /**
     * Checks if personal best display is enabled in config.
     * @return true if PB display is enabled
     */
    fun isPersonalBestDisplayEnabled(): Boolean {
        return try {
            // TODO: Implement config check when config system is ported
            true // Default to enabled
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to get showPersonalBest setting", e)
            true // Default to enabled
        }
    }

    /**
     * Checks if personal best improvement display is enabled in config.
     * @return true if PB improvement display is enabled
     */
    fun isPersonalBestImprovementEnabled(): Boolean {
        return try {
            // TODO: Implement config check when config system is ported
            true // Default to enabled
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to get showPersonalBestImprovement setting", e)
            true // Default to enabled
        }
    }
}
