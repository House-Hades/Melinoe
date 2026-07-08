package me.melinoe.utils.handlers

import me.melinoe.Melinoe
import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.DungeonChangeEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.core.EventBus
import me.melinoe.events.core.on
import me.melinoe.features.impl.tracking.PityCounterModule
import me.melinoe.features.impl.visual.dungeontimer.PityCounterConfig
import me.melinoe.utils.ChatManager.hideMessage
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.BagTracker
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.getCenteredText
import me.melinoe.utils.noControlCodes
import me.melinoe.utils.toNative
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

/**
 * Handles boss defeat / dungeon leaderboard messages from the server.
 *
 * Telos now sends the entire leaderboard as a SINGLE multi-line system message, and
 * provides its own dungeon timer line. Example (one message, newlines embedded):
 *
 *     ☠ Abyss of Demons ☠                     <- dungeon title
 *     ☠ Defeated Malfas in 24s                <- Telos' built-in timer line
 *                                             <- blank spacer
 *     𖁰 Damage   ◆ Contribs   𖃱 Loot Boost      <- column headers
 *     𕑱 willowx (you) ... 100.0% ... 100% ... +20%   <- player rows
 *
 * This handler leaves Telos' leaderboard formatting untouched and only:
 *  - increments pity counters / total runs for the defeated boss, and
 *  - injects our pity line directly under the dungeon title (above the timer line).
 */
object BossDefeatHandler {

    /**
     * Matches the "Defeated <boss>" line, capturing everything after it.
     * Dungeons include a timer ("Defeated Malfas in 24s"); world bosses do not
     * ("Defeated Lotil")
     */
    private val DEFEAT_LINE = Regex("Defeated\\s+(.+)")

    /** Trailing " in <time>" suffix on dungeon timer lines (absent for world bosses) */
    private val TIME_SUFFIX = Regex("\\s+in\\s+\\d.*$")

    /** Arrow the server appends to the local player's row in the kill leaderboard */
    private const val SELF_ARROW = "⬅" // ⬅

    /** Any percentage value on a leaderboard row */
    private val PERCENT = Regex("(\\d+(?:\\.\\d+)?)%")

    private var currentDungeon: DungeonData? = null
    private var lastDefeatedBoss: BossData? = null

    init {
        EventBus.subscribe(this)

        on<ChatPacketEvent> {
            if (!ServerUtils.isOnTelos()) return@on
            this.handleMessage()
        }

        on<DungeonEntryEvent> {
            currentDungeon = dungeon
        }

        on<DungeonExitEvent> {
            currentDungeon = null
        }

        on<DungeonChangeEvent> {
            currentDungeon = newDungeon
        }
    }

    private fun ChatPacketEvent.handleMessage() {
        val strippedValue = value.noControlCodes

        // Hide "Bonus dungeon chest has spawned!" message
        if (strippedValue.contains("Bonus dungeon chest has spawned")) {
            hideMessage()
            return
        }

        // "Bonus chest has been unlocked!" counts as an additional pity increment
        if (strippedValue.contains("Bonus chest has been unlocked")) {
            hideMessage()
            handleBonusChestUnlocked()
            return
        }

        // Dungeon/boss leaderboard: a single multi-line message containing the timer line
        if (value.contains('\n') && DEFEAT_LINE.containsMatchIn(strippedValue)) {
            handleLeaderboard()
        }
    }

    /**
     * Process the single-message leaderboard: count the defeat and inject our pity line.
     */
    private fun ChatPacketEvent.handleLeaderboard() {
        val strippedLines = value.split("\n").map { it.noControlCodes }

        // Locate the "Defeated <boss> in <time>" line and extract the boss label
        var defeatIdx = -1
        var bossName: String? = null
        for ((i, line) in strippedLines.withIndex()) {
            val match = DEFEAT_LINE.find(line) ?: continue
            defeatIdx = i
            bossName = match.groupValues[1].replace(TIME_SUFFIX, "").trim()
            break
        }
        if (defeatIdx < 0 || bossName == null) return

        val bossData = BossData.findByKey(bossName)

        // Capture the contribution/damage loot boost
        BagTracker.setContributionLootBoost(parseLootBoost(strippedLines) ?: 0)

        // Count the defeat (pity counters + total runs), regardless of HUD/module state
        if (bossData != null) {
            lastDefeatedBoss = bossData
            BagTracker.onBossDefeat(bossName)
        }

        // Only touch the chat message if there is actually a pity line to inject
        if (bossData == null || !PityCounterModule.enabled) return
        val pityLine = PityCounterConfig.buildPityLine(currentDungeon, bossData)
        if (pityLine.isEmpty()) return

        // Rebuild the message with the pity line inserted directly above the timer line
        val rebuilt = injectLine(component, defeatIdx, getCenteredText(pityLine).toNative())
        hideMessage()
        Melinoe.mc.execute { Melinoe.mc.gui.chat.addClientSystemMessage(rebuilt) }
    }

    /**
     * Extracts the local player's loot boost from the kill leaderboard.
     */
    private fun parseLootBoost(strippedLines: List<String>): Int? {
        val name = Melinoe.mc.player?.gameProfile?.name
        val row = strippedLines.firstOrNull { it.contains(SELF_ARROW) }
            ?: name?.let { name -> strippedLines.firstOrNull { it.contains(name) } }
            ?: return null

        val percents = PERCENT.findAll(row).map { it.groupValues[1] }.toList()
        if (percents.isEmpty()) return null

        return percents.last().toDoubleOrNull()?.toInt()
    }

    /**
     * Rebuilds [component] (a multi-line message), inserting [line] at line index [index].
     * Preserves the original per-segment styling (colors, hover/click events, etc.).
     */
    private fun injectLine(component: Component, index: Int, line: Component): Component {
        data class Run(val style: Style, val text: String)

        val lines = mutableListOf<MutableList<Run>>()
        var current = mutableListOf<Run>()
        component.visit({ style: Style, text: String ->
            var start = 0
            while (true) {
                val nl = text.indexOf('\n', start)
                if (nl < 0) {
                    if (start < text.length) current.add(Run(style, text.substring(start)))
                    break
                }
                if (nl > start) current.add(Run(style, text.substring(start, nl)))
                lines.add(current)
                current = mutableListOf()
                start = nl + 1
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        lines.add(current)

        val lineComponents = lines.mapTo(mutableListOf<Component>()) { runs ->
            val c = Component.empty()
            for (run in runs) c.append(Component.literal(run.text).withStyle(run.style))
            c
        }

        lineComponents.add(index.coerceIn(0, lineComponents.size), line)

        val result = Component.empty()
        for ((i, lineComponent) in lineComponents.withIndex()) {
            if (i > 0) result.append(Component.literal("\n"))
            result.append(lineComponent)
        }
        return result
    }

    /**
     * Handle bonus chest unlock (when "Bonus chest has been unlocked!" message appears).
     * Increments pity counters for all items from the most recently defeated boss.
     */
    private fun handleBonusChestUnlocked() {
        val boss = lastDefeatedBoss
        if (boss == null) {
            Melinoe.logger.warn("[BossDefeatHandler] Bonus chest unlocked but no boss was recently defeated")
            return
        }

        Melinoe.logger.info("[BossDefeatHandler] Bonus chest unlocked for ${boss.label}, incrementing pity")
        BagTracker.onBossDefeat(boss.label)
    }
}