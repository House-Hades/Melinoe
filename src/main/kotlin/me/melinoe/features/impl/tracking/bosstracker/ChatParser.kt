package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.utils.Message

/**
 * Parses chat messages for boss-related events
 */
object ChatParser {
    
    /**
     * Handle incoming chat message
     */
    fun handleChatMessage(messageString: String): Boolean {
        // Skip messages that don't match boss patterns
        if (!messageString.matches(Regex("^(\\w|\\[|\\().+"))) {
            return false
        }
        
        val level = mc.level
        val currentDimension = level?.dimension()?.location()?.path ?: ""
        val inDungeon = currentDimension == Constants.DIMENSION_DUNGEON
        
        // Check for boss call messages: "[BossName] message"
        val potentialBossMatcher = Constants.POTENTIAL_BOSS_PATTERN.matcher(messageString)
        if (potentialBossMatcher.find()) {
            val bossName = potentialBossMatcher.group(1)
            val bossData = BossData.fromString(bossName)
            
            if (bossData != null) {
                // Unique override for Shadowlands minibosses
                val isShadowlandsBoss = bossName in listOf("Reaper", "Warden", "Herald")
                if (isShadowlandsBoss) {
                    val defeatPattern = Constants.SHADOWLANDS_DEFEATS[bossName]
                    if (defeatPattern != null && defeatPattern.matcher(messageString).find()) {
                        Melinoe.logger.info("[BossTracker] Shadowlands boss defeated: $bossName")
                        BossState.updateBoss(bossName, BossState.State.SHADOWLANDS_IDLE)
                        return false
                    }

                    val spawnPattern = Constants.SHADOWLANDS_SPAWNS[bossName]
                    if (spawnPattern != null && spawnPattern.matcher(messageString).find()) {
                        Melinoe.logger.info("[BossTracker] Shadowlands boss spawned: $bossName")
                        BossState.updateBoss(bossName, BossState.State.ALIVE)
                        return false
                    }

                    // Allow players' calls to still update the called player for shadowlands bosses
                    val playerCallMatcher = bossData.playerCallPattern.matcher(messageString)
                    if (playerCallMatcher.find()) {
                        val boss = BossState.getBoss(bossName) ?: BossState.updateBoss(bossName, BossState.State.ALIVE)
                        boss?.calledPlayerName = playerCallMatcher.group(1)
                        Melinoe.logger.info("[BossTracker] Player call detected: ${boss?.calledPlayerName}")
                    }

                    return false
                }

                // Normal handling for the normal realm bosses
                Melinoe.logger.info("[BossTracker] Boss call detected: $bossName (dimension: $currentDimension, inDungeon=$inDungeon)")
                val boss = BossState.updateBoss(bossName, BossState.State.ALIVE)
                
                // Check if this is a player call message
                val playerCallMatcher = bossData.playerCallPattern.matcher(messageString)
                if (playerCallMatcher.find()) {
                    boss?.calledPlayerName = playerCallMatcher.group(1)
                    Melinoe.logger.info("[BossTracker] Player call detected: ${boss?.calledPlayerName}")
                }
                return false
            }
        }
        
        // Check for boss defeated messages
        val defeatedMatcher = Constants.BOSS_DEFEATED_PATTERN.matcher(messageString)
        if (defeatedMatcher.find()) {
            return handleBossDefeated(defeatedMatcher, currentDimension, inDungeon)
        }
        
        // Check for boss spawned messages
        val spawnedMatcher = Constants.BOSS_SPAWNED_PATTERN.matcher(messageString)
        if (spawnedMatcher.find()) {
            handleBossSpawned(spawnedMatcher, currentDimension, inDungeon)
            return false
        }
        
        return false
    }
    
    /**
     * Handle boss defeated message
     */
    private fun handleBossDefeated(
        matcher: java.util.regex.Matcher,
        currentDimension: String,
        inDungeon: Boolean
    ): Boolean {
        val bossName = matcher.group(1)
        val currentCount = matcher.group(2)?.toIntOrNull()
        val maxCount = matcher.group(3)?.toIntOrNull()
        
        Melinoe.logger.info("[BossTracker] Boss defeated: $bossName (dimension: $currentDimension, inDungeon=$inDungeon, count: $currentCount/$maxCount)")
        BossState.updateBoss(bossName, BossState.State.DEFEATED_PORTAL_ACTIVE)?.resetPortalTimer()
        
        // Update Raphael progress if we have count information
        if (currentCount != null && maxCount != null) {
            BossState.raphaelProgress = currentCount
            BossState.raphaelMaxProgress = maxCount
            Melinoe.logger.info("[BossTracker] Raphael progress updated: ${BossState.raphaelProgress}/${BossState.raphaelMaxProgress}")
        }
        
        // Check if Raphael's portal should spawn (10/10 world bosses defeated)
        if (currentCount != null && maxCount != null && currentCount == maxCount && maxCount == 10) {
            Melinoe.logger.info("[BossTracker] 10/10 world bosses defeated! Spawning Raphael portal")
            val raphael = BossState.updateBoss("Raphael", BossState.State.DEFEATED_PORTAL_ACTIVE)
            raphael?.resetPortalTimer(Constants.PORTAL_TIMER_RAPHAEL)
            
            Message.warning("Raphael's portal has spawned!")
        }
        
        // Return true to hide the "has been defeated" message
        return true
    }
    
    /**
     * Handle boss spawned message
     */
    private fun handleBossSpawned(
        matcher: java.util.regex.Matcher,
        currentDimension: String,
        inDungeon: Boolean
    ) {
        val bossName = matcher.group(1)
        Melinoe.logger.info("[BossTracker] Boss spawned: $bossName (dimension: $currentDimension, inDungeon=$inDungeon)")
        BossState.getBoss(bossName)?.calledPlayerName = null
        BossState.updateBoss(bossName, BossState.State.ALIVE)
    }
}