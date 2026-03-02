package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData

/**
 * Manages the state of the dungeon timer.
 * Separates state management from UI and event handling logic.
 */
class TimerState {
    
    data class BossDefeat(
        val boss: BossData,
        val splitTime: Float,
        val wasNewPB: Boolean,
        val oldPB: Float
    )
    
    private var timerActive = false
    private var dungeonCompleted = false
    private var currentDungeon: DungeonData? = null
    private var currentFinalBoss: BossData? = null
    private var dungeonStartTime = 0L
    private var finalCompletionTime = 0f
    private val bossDefeats = mutableListOf<BossDefeat>()
    private var lastBossTime = 0f
    
    /**
     * Starts the timer for a new dungeon run.
     */
    fun startTimer(dungeon: DungeonData) {
        timerActive = true
        dungeonCompleted = false
        finalCompletionTime = 0f
        
        currentDungeon = dungeon
        currentFinalBoss = dungeon.finalBoss
        lastBossTime = 0f
        bossDefeats.clear()
        
        dungeonStartTime = System.nanoTime()
    }
    
    /**
     * Stops the timer and marks the dungeon as completed.
     */
    fun stopTimer(completionTime: Float) {
        timerActive = false
        dungeonCompleted = true
        finalCompletionTime = completionTime
    }
    
    /**
     * Gets the current elapsed time in seconds.
     */
    fun getCurrentTime(): Float {
        if (dungeonStartTime == 0L) return 0f
        
        if (dungeonCompleted) {
            return finalCompletionTime
        }
        
        val currentTime = System.nanoTime()
        val elapsedNanos = currentTime - dungeonStartTime
        return elapsedNanos / 1_000_000_000f
    }
    
    /**
     * Gets formatted time string for HUD display.
     */
    fun getFormattedTime(): String {
        val currentTimer = getCurrentTime()
        val minutes = (currentTimer / 60).toInt()
        val seconds = currentTimer % 60
        
        return if (minutes == 0) {
            String.format("%.1fs", seconds).replace(',', '.')
        } else {
            String.format("%dm%.1fs", minutes, seconds).replace(',', '.')
        }
    }
    
    /**
     * Adds a boss defeat to the tracking list.
     */
    fun addBossDefeat(defeat: BossDefeat) {
        bossDefeats.add(defeat)
        lastBossTime = defeat.splitTime
    }
    
    /**
     * Resets all timer state.
     */
    fun reset() {
        timerActive = false
        dungeonCompleted = false
        currentDungeon = null
        currentFinalBoss = null
        dungeonStartTime = 0L
        finalCompletionTime = 0f
        bossDefeats.clear()
        lastBossTime = 0f
    }
    
    // Getters
    fun isActive() = timerActive
    fun isCompleted() = dungeonCompleted
    fun getCurrentDungeon() = currentDungeon
    fun getFinalBoss() = currentFinalBoss
    fun getBossDefeats() = bossDefeats.toList()
    fun getLastBossTime() = lastBossTime
}
