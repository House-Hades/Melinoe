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
    private val nameColor by ColorSetting("Label Color", Color(27, 197, 97), desc = "Color for labels (Dungeon:, Current Time:, etc.)")
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
     * Called when a boss is defeated (called from BossDefeatHandler).
     */
    fun onBossDefeated(boss: BossData) {
        Melinoe.logger.info("[TIMER] onBossDefeated called:")
        Melinoe.logger.info("[TIMER]   - boss: ${boss.label} (${boss.name})")
        Melinoe.logger.info("[TIMER]   - timerActive: ${timerState.isActive()}")
        Melinoe.logger.info("[TIMER]   - currentDungeon: ${timerState.getCurrentDungeon()?.areaName}")
        Melinoe.logger.info("[TIMER]   - currentFinalBoss: ${timerState.getFinalBoss()?.label}")
        
        if (!timerState.isActive()) {
            Melinoe.logger.info("[TIMER] Ignoring boss defeat - timer not active")
            return
        }
        
        val dungeon = timerState.getCurrentDungeon() ?: return
        val currentTime = timerState.getCurrentTime()
        
        // Check if this is the final boss for the current dungeon
        if (boss == timerState.getFinalBoss()) {
            Melinoe.logger.info("[TIMER] ✓ FINAL BOSS DEFEATED! Stopping timer at ${timerState.getFormattedTime()}")
            handleFinalBossDefeat(boss, dungeon, currentTime)
        } else if (dungeon.isMultiStageDungeon()) {
            Melinoe.logger.info("[TIMER] ✓ Split boss defeated (${boss.label}) at ${timerState.getFormattedTime()} - timer continues")
            handleSplitBossDefeat(boss, dungeon, currentTime)
        } else {
            Melinoe.logger.info("[TIMER] ✗ Non-final boss defeated (${boss.label}) - timer continues")
        }
    }
    
    /**
     * Handles the defeat of the final boss in a dungeon.
     */
    private fun handleFinalBossDefeat(boss: BossData, dungeon: DungeonData, time: Float) {
        // Stop the timer
        timerState.stopTimer(time)
        
        // Check for personal best
        val oldPB = PersonalBestManager.getDungeonPersonalBest(dungeon)
        val isNewPB = oldPB == -1f || time < oldPB
        
        if (isNewPB) {
            PersonalBestManager.updateDungeonPersonalBest(dungeon, time)
            Melinoe.logger.info("[TIMER] New personal best! ${timerState.getFormattedTime()}")
        } else {
            val diff = time - oldPB
            val diffStr = if (diff > 0) "+${PersonalBestManager.formatTimeWithDecimals(diff)}" else PersonalBestManager.formatTimeWithDecimals(diff)
            Melinoe.logger.info("[TIMER] Dungeon completed in ${timerState.getFormattedTime()} (PB: ${PersonalBestManager.formatTimeWithDecimals(oldPB)}, $diffStr)")
        }
        
        // For split dungeons, add final boss to defeats list
        if (dungeon.isMultiStageDungeon()) {
            val splitTime = calculateSplitTime(time)
            val bossPB = PersonalBestManager.getBossPersonalBest(boss)
            val wasBossNewPB = PersonalBestManager.updateBossPersonalBest(boss, splitTime)
            timerState.addBossDefeat(TimerState.BossDefeat(boss, splitTime, wasBossNewPB, bossPB))
        } else {
            // Regular dungeon - just record the boss defeat
            timerState.addBossDefeat(TimerState.BossDefeat(boss, time, isNewPB, oldPB))
        }
        
        // Send completion message to chat
        val player = mc.player
        if (player != null) {
            if (dungeon.isMultiStageDungeon()) {
                showSplitSummary(dungeon)
            } else {
                showCompletionMessage(dungeon, boss, time, oldPB, isNewPB)
            }
        }
    }
    
    /**
     * Handles the defeat of a non-final boss in a multi-stage dungeon.
     */
    private fun handleSplitBossDefeat(boss: BossData, dungeon: DungeonData, currentTime: Float) {
        val splitTime = calculateSplitTime(currentTime)
        
        val oldPB = PersonalBestManager.getBossPersonalBest(boss)
        val wasNewPB = PersonalBestManager.updateBossPersonalBest(boss, splitTime)
        timerState.addBossDefeat(TimerState.BossDefeat(boss, splitTime, wasNewPB, oldPB))
        
        // Show immediate split message
        val player = mc.player
        if (player != null) {
            showSplitMessage(dungeon, boss, splitTime, oldPB, wasNewPB)
        }
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
    
    /**
     * Shows the completion message for a regular dungeon.
     */
    private fun showCompletionMessage(
        dungeon: DungeonData,
        boss: BossData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ) {
        // Show dungeon name centered
        val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
        Message.raw(centerComponent(headerComponent))
        
        // Show pity counter if applicable
        val pityLine = PityCounterConfig.buildPityLine(dungeon, boss)
        if (pityLine.isNotEmpty()) {
            Message.centeredRaw(pityLine)
        }
        
        // Show completion message centered
        val messageComponent = MessageFormatter.formatCompletionMessage(dungeon, time, oldPB, isNewPB)
        Message.raw(centerComponent(messageComponent))
    }
    
    /**
     * Shows the split message for a boss in a multi-stage dungeon.
     */
    private fun showSplitMessage(
        dungeon: DungeonData,
        boss: BossData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ) {
        // Show dungeon name centered
        val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
        Message.raw(centerComponent(headerComponent))
        
        // Show pity counter if applicable
        val pityLine = PityCounterConfig.buildPityLine(dungeon, boss)
        if (pityLine.isNotEmpty()) {
            Message.centeredRaw(pityLine)
        }
        
        // Show boss split message centered
        val messageComponent = MessageFormatter.formatSplitMessage(dungeon, boss, time, oldPB, isNewPB)
        Message.raw(centerComponent(messageComponent))
    }
    
    /**
     * Shows the split summary for multi-stage dungeons.
     */
    private fun showSplitSummary(dungeon: DungeonData) {
        // Show dungeon name at the top, centered
        val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
        Message.raw(centerComponent(headerComponent))
        
        // Show pity counter only for the final boss (last defeat in the list)
        val defeats = timerState.getBossDefeats()
        if (defeats.isNotEmpty()) {
            val finalBoss = defeats.last().boss
            val pityLine = PityCounterConfig.buildPityLine(dungeon, finalBoss)
            if (pityLine.isNotEmpty()) {
                Message.centeredRaw(pityLine)
            }
        }
        
        // Show all boss defeats centered
        for (defeat in defeats) {
            val messageComponent = MessageFormatter.formatSplitSummaryMessage(
                dungeon, defeat.boss, defeat.splitTime, defeat.oldPB, defeat.wasNewPB
            )
            Message.raw(centerComponent(messageComponent))
        }
    }
    
    /**
     * Renders the example HUD display.
     */
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

    /**
     * Public methods for simulations (used by dev commands).
     */
    fun buildSimulatedCompletionMessage(dungeon: DungeonData, time: Float, oldPB: Float, isNewPB: Boolean): String {
        val component = MessageFormatter.formatCompletionMessage(dungeon, time, oldPB, isNewPB)
        return component.string
    }
    
    fun showSimulatedHeader(dungeon: DungeonData) {
        val headerComponent = GradientTextBuilder.buildGradientText(dungeon.areaName, dungeon.dungeonType)
        Message.raw(centerComponent(headerComponent))
    }
    
    fun showSimulatedCompletionMessage(dungeon: DungeonData, time: Float, oldPB: Float, isNewPB: Boolean) {
        val messageComponent = MessageFormatter.formatCompletionMessage(dungeon, time, oldPB, isNewPB)
        Message.raw(centerComponent(messageComponent))
    }
    
    fun showSimulatedBossSplitMessage(dungeon: DungeonData, boss: BossData, time: Float, oldPB: Float, isNewPB: Boolean) {
        val messageComponent = MessageFormatter.formatSplitMessage(dungeon, boss, time, oldPB, isNewPB)
        Message.raw(centerComponent(messageComponent))
    }
    
    fun showSimulatedBossSplitSummaryMessage(defeat: TimerState.BossDefeat, dungeon: DungeonData) {
        val messageComponent = MessageFormatter.formatSplitSummaryMessage(dungeon, defeat.boss, defeat.splitTime, defeat.oldPB, defeat.wasNewPB)
        Message.raw(centerComponent(messageComponent))
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
