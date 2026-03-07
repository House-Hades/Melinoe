package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.Melinoe
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.events.DungeonChangeEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.utils.Color
import me.melinoe.utils.Message
import me.melinoe.utils.PersonalBestManager
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.render.textDim
import net.minecraft.network.chat.Component

/**
 * Dungeon Timer Module - displays dungeon completion time with personal best tracking.
 * Tracks dungeon runs, boss splits, and shows completion messages with PB comparisons.
 * 
 * Refactored to separate concerns:
 * - TimerState: Timer state management
 * - GradientTextBuilder: Gradient text generation
 * - MessageFormatter: Message formatting
 * - PityCounterConfig: Pity counter configuration
 */
object TimerModule : Module(
    name = "Dungeon Timer",
    category = Category.VISUAL,
    description = "Displays dungeon completion timer with personal best tracking"
) {
    
    // Settings
    private val nameColor by ColorSetting("Label Color", Color(0xFF7CFFB2.toInt()), desc = "Color for labels (Dungeon:, Current Time:, etc.)")
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for values (dungeon name, times, etc.)")
    
    // State management
    private val timerState = TimerState()
    
    // Cache for expensive operations
    private var cachedPersonalBestString = "None"
    private var lastPersonalBestUpdate = 0L
    
    // HUD element for displaying the timer
    private val timerHud by HUDSetting(
        name = "Timer Display",
        x = 10,
        y = 150,
        scale = 1f,
        toggleable = false,
        description = "Position of the dungeon timer display",
        module = this
    ) { example ->
        val textRenderer = mc.font
        val lineHeight = textRenderer.lineHeight
        
        if (example) {
            // Example display
            this.renderExampleHUD(textRenderer, lineHeight)
        } else if (timerState.getCurrentDungeon() != null && (timerState.isActive() || timerState.isCompleted())) {
            // Actual display - show timer while active OR for a few seconds after completion
            this.renderActualHUD(textRenderer, lineHeight)
        } else {
            0 to 0
        }
    }
    
    init {
        // Event-driven dungeon detection
        on<DungeonEntryEvent> {
            Melinoe.logger.info("DungeonTimerModule: Received DungeonEntryEvent for ${dungeon.areaName}, enabled=$enabled")
            if (!enabled) return@on
            handleDungeonEntry(dungeon)
        }
        
        on<DungeonExitEvent> {
            Melinoe.logger.info("DungeonTimerModule: Received DungeonExitEvent for ${dungeon.areaName}, enabled=$enabled")
            if (!enabled) return@on
            handleDungeonExit()
        }
        
        on<DungeonChangeEvent> {
            Melinoe.logger.info("DungeonTimerModule: Received DungeonChangeEvent from ${previousDungeon.areaName} to ${newDungeon.areaName}, enabled=$enabled")
            if (!enabled) return@on
            // Dungeon chain - treat as new dungeon entry
            handleDungeonEntry(newDungeon)
        }
        
        on<TickEvent.End> {
            if (!enabled) return@on
            
            // Update personal best cache periodically
            if (timerState.isActive()) {
                updatePersonalBestCache()
            }
        }
    }

    /**
     * Called when player enters a dungeon.
     */
    private fun handleDungeonEntry(dungeon: DungeonData) {
        Melinoe.logger.info("DungeonTimerModule: Player entered dungeon: ${dungeon.areaName}")
        
        if (timerState.isActive()) {
            Melinoe.logger.warn("DungeonTimerModule: Attempted to start new timer while one was active!")
            return
        }
        
        timerState.startTimer(dungeon)
        cachedPersonalBestString = getCurrentPersonalBestString()
        lastPersonalBestUpdate = 0

        Melinoe.logger.info("DungeonTimerModule: Timer started for ${dungeon.areaName}")
    }
    
    /**
     * Called when player exits a dungeon.
     */
    private fun handleDungeonExit() {
        if (timerState.isActive() || timerState.isCompleted()) {
            Melinoe.logger.info("DungeonTimerModule: Player exited dungeon, stopping timer")
            timerState.reset()
        }
    }
    
    /**
     * Executes timer logic (PB, splits, ending) and returns formatted MiniMessage components
     * to the BossDefeatHandler. Keeps Chat functionality completely modular.
     */
    fun processBossDefeatAndGetLines(boss: BossData): List<Component> {
        Melinoe.logger.info("[TIMER] onBossDefeated called:")
        Melinoe.logger.info("[TIMER]   - boss: ${boss.label} (${boss.name})")
        Melinoe.logger.info("[TIMER]   - timerActive: ${timerState.isActive()}")
        Melinoe.logger.info("[TIMER]   - currentDungeon: ${timerState.getCurrentDungeon()?.areaName}")
        Melinoe.logger.info("[TIMER]   - currentFinalBoss: ${timerState.getFinalBoss()?.label}")
        
        val lines = mutableListOf<Component>()
        if (!timerState.isActive()) {
            Melinoe.logger.info("[TIMER] Ignoring boss defeat - timer not active")
            return lines
        }
        
        val dungeon = timerState.getCurrentDungeon() ?: return lines
        val currentTime = timerState.getCurrentTime()
        
        // Check if this is the final boss for the current dungeon
        if (boss == timerState.getFinalBoss()) {
            timerState.stopTimer(currentTime)
            
            val oldPB = PersonalBestManager.getDungeonPersonalBest(dungeon)
            val isNewPB = oldPB == -1f || currentTime < oldPB
            
            if (isNewPB) {
                PersonalBestManager.updateDungeonPersonalBest(dungeon, currentTime)
                Melinoe.logger.info("[TIMER] New personal best! ${timerState.getFormattedTime()}")
            }
            
            if (dungeon.isMultiStageDungeon()) {
                val splitTime = calculateSplitTime(currentTime)
                val bossPB = PersonalBestManager.getBossPersonalBest(boss)
                val wasBossNewPB = PersonalBestManager.updateBossPersonalBest(boss, splitTime)
                timerState.addBossDefeat(TimerState.BossDefeat(boss, splitTime, wasBossNewPB, bossPB))
                
                // For multi-stage final boss, return all splits
                for (defeat in timerState.getBossDefeats()) {
                    lines.add(MessageFormatter.formatSplitSummaryMessage(
                        dungeon, defeat.boss, defeat.splitTime, defeat.oldPB, defeat.wasNewPB
                    ))
                }
            } else {
                // Regular dungeon final boss
                timerState.addBossDefeat(TimerState.BossDefeat(boss, currentTime, isNewPB, oldPB))
                lines.add(MessageFormatter.formatCompletionMessage(dungeon, currentTime, oldPB, isNewPB))
            }
        } else if (dungeon.isMultiStageDungeon()) {
            // Intermediate multi-stage boss
            val splitTime = calculateSplitTime(currentTime)
            val oldPB = PersonalBestManager.getBossPersonalBest(boss)
            val wasNewPB = PersonalBestManager.updateBossPersonalBest(boss, splitTime)
            timerState.addBossDefeat(TimerState.BossDefeat(boss, splitTime, wasNewPB, oldPB))
            
            lines.add(MessageFormatter.formatSplitMessage(dungeon, boss, splitTime, oldPB, wasNewPB))
        }
        
        return lines
    }
    
    /**
     * Calculates the split time for a boss defeat.
     */
    private fun calculateSplitTime(currentTime: Float): Float {
        val lastBossTime = timerState.getLastBossTime()
        return if (lastBossTime == 0f) {
            // First boss in the dungeon - split time is the total time from start
            currentTime
        } else {
            // Subsequent bosses - split time is time since last boss
            currentTime - lastBossTime
        }
    }
    
    private fun net.minecraft.client.gui.GuiGraphics.renderExampleHUD(textRenderer: net.minecraft.client.gui.Font, lineHeight: Int): Pair<Int, Int> {
        val line1Label = "Dungeon: "
        val line1Value = "Example Dungeon"
        val line2Label = "Current Time: "
        val line2Value = "1m23.4s"
        val line3Label = "Personal Best: "
        val line3Value = "1m15.2s"
        
        var xOffset = 0
        xOffset += textDim(line1Label, xOffset, 0, nameColor, true).first
        xOffset += textDim(line1Value, xOffset, 0, valueColor, true).first
        
        xOffset = 0
        xOffset += textDim(line2Label, xOffset, lineHeight, nameColor, true).first
        xOffset += textDim(line2Value, xOffset, lineHeight, valueColor, true).first
        
        xOffset = 0
        xOffset += textDim(line3Label, xOffset, lineHeight * 2, nameColor, true).first
        xOffset += textDim(line3Value, xOffset, lineHeight * 2, valueColor, true).first
        
        val width = maxOf(
            textRenderer.width(line1Label + line1Value),
            textRenderer.width(line2Label + line2Value),
            textRenderer.width(line3Label + line3Value)
        )
        return width to (lineHeight * 3)
    }
    
    /**
     * Renders the actual HUD display.
     */
    private fun net.minecraft.client.gui.GuiGraphics.renderActualHUD(textRenderer: net.minecraft.client.gui.Font, lineHeight: Int): Pair<Int, Int> {
        val dungeon = timerState.getCurrentDungeon() ?: return 0 to 0
        
        val line1Label = "Dungeon: "
        val line1Value = dungeon.areaName
        
        val line2Label = "Current Time: "
        val line2Value = timerState.getFormattedTime()
        
        val line3Label = "Personal Best: "
        val line3Value = cachedPersonalBestString
        
        var xOffset = 0
        xOffset += textDim(line1Label, xOffset, 0, nameColor, true).first
        xOffset += textDim(line1Value, xOffset, 0, valueColor, true).first
        
        xOffset = 0
        xOffset += textDim(line2Label, xOffset, lineHeight, nameColor, true).first
        xOffset += textDim(line2Value, xOffset, lineHeight, valueColor, true).first
        
        xOffset = 0
        xOffset += textDim(line3Label, xOffset, lineHeight * 2, nameColor, true).first
        xOffset += textDim(line3Value, xOffset, lineHeight * 2, valueColor, true).first
        
        val width = maxOf(
            textRenderer.width(line1Label + line1Value),
            textRenderer.width(line2Label + line2Value),
            textRenderer.width(line3Label + line3Value)
        )
        return width to (lineHeight * 3)
    }
    
    /**
     * Get current personal best string.
     */
    private fun getCurrentPersonalBestString(): String {
        val dungeon = timerState.getCurrentDungeon() ?: return "None"
        val pb = PersonalBestManager.getDungeonPersonalBest(dungeon)
        return if (pb == -1f) "None" else PersonalBestManager.formatTimeWithDecimals(pb)
    }
    
    /**
     * Update personal best cache periodically.
     */
    private fun updatePersonalBestCache() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPersonalBestUpdate >= Constants.PB_UPDATE_INTERVAL_MS) {
            cachedPersonalBestString = getCurrentPersonalBestString()
            lastPersonalBestUpdate = currentTime
        }
    }
    
    override fun onEnable() {
        super.onEnable()
        Melinoe.logger.info("Dungeon Timer enabled")
    }
    
    override fun onDisable() {
        super.onDisable()
        if (timerState.isActive() || timerState.isCompleted()) {
            timerState.reset()
        }
        Melinoe.logger.info("Dungeon Timer disabled")
    }
}
