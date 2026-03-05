package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.utils.Message
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.LodestoneTracker
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Boss tracking state management
 */
object BossState {
    private val trackedBosses = mutableMapOf<String, TrackedBoss>()
    private val trackedBossesByState = mutableMapOf<State, MutableSet<TrackedBoss>>()
    
    // Raphael progress tracking (X/10 world bosses defeated)
    var raphaelProgress = 0
    var raphaelMaxProgress = 10
    
    init {
        // Initialize state maps
        for (state in State.values()) {
            trackedBossesByState[state] = mutableSetOf()
        }
        initShadowlandsBosses()
    }

    /**
     * Initializes specific logic for shadowlands bosses which are present at all times
     */
    private fun initShadowlandsBosses() {
        updateBoss("Reaper", State.SHADOWLANDS_IDLE)
        updateBoss("Warden", State.SHADOWLANDS_IDLE)
        updateBoss("Herald", State.SHADOWLANDS_IDLE)
    }
    
    /**
     * Get all tracked bosses
     */
    fun getAllBosses(): Collection<TrackedBoss> = trackedBosses.values
    
    /**
     * Get bosses by state
     */
    fun getBossesByState(state: State): List<TrackedBoss> {
        return trackedBossesByState[state]?.toList() ?: emptyList()
    }
    
    /**
     * Get a specific boss by name
     */
    fun getBoss(name: String): TrackedBoss? = trackedBosses[name]
    
    /**
     * Update or create a tracked boss by name
     */
    fun updateBoss(bossName: String, state: State): TrackedBoss? {
        // Check if boss already exists
        val existing = trackedBosses[bossName]
        if (existing != null) {
            setState(existing, state)
            return existing
        }
        
        // Create new boss if data exists
        val bossData = BossData.fromString(bossName) ?: return null
        val newBoss = TrackedBoss(bossName, bossData.spawnPosition, state, bossData)
        addBoss(newBoss)
        return newBoss
    }
    
    /**
     * Add a boss to tracking
     */
    private fun addBoss(boss: TrackedBoss) {
        trackedBosses[boss.name] = boss
        trackedBossesByState[boss.state]?.add(boss)
    }
    
    /**
     * Remove a boss from tracking
     */
    fun removeBoss(bossName: String) {
        val boss = trackedBosses.remove(bossName) ?: return
        trackedBossesByState[boss.state]?.remove(boss)
    }
    
    /**
     * Set boss state and update state maps
     */
    fun setState(boss: TrackedBoss, newState: State) {
        trackedBossesByState[boss.state]?.remove(boss)
        boss.state = newState
        trackedBossesByState[newState]?.add(boss)

        // Clear called player if it's returning to idle
        if (newState == State.SHADOWLANDS_IDLE) {
            boss.calledPlayerName = null
        }
    }
    
    /**
     * Clear all tracked bosses
     */
    fun clearAll() {
        trackedBosses.clear()
        for (state in State.values()) {
            trackedBossesByState[state]?.clear()
        }
        initShadowlandsBosses()
    }
    
    /**
     * Update distance markers for all bosses
     */
    fun updateDistanceMarkers() {
        val player = mc.player ?: return
        val playerPos = Vec3.atCenterOf(player.blockPosition())
        
        for (boss in trackedBosses.values) {
            if (boss.state != State.DEFEATED) {
                val bossPos = Vec3.atCenterOf(boss.spawnPosition)
                boss.distanceMarkerValue = Math.floor(playerPos.distanceTo(bossPos) * 0.008)
            }
        }
    }
    
    /**
     * Update portal timers for all bosses with active portals
     */
    fun updatePortalTimers() {
        for (boss in trackedBosses.values) {
            if (boss.state == State.DEFEATED_PORTAL_ACTIVE) {
                if (boss.updatePortalTimerFromServerTicks() <= 0) {
                    setState(boss, State.DEFEATED)
                    
                    // Reset Raphael progress when his portal expires
                    if (boss.name == "Raphael") {
                        raphaelProgress = 0
                        Melinoe.logger.info("[BossTracker] Raphael portal expired, resetting progress to 0/10")
                    }
                }
            }
        }
    }
    
    /**
     * Scan the /bosses menu when it closes to update boss states
     */
    fun scanBossesMenu(screen: AbstractContainerScreen<*>) {
        try {
            val menu = screen.menu
            val updatedBosses = mutableMapOf<String, State>()
            
            // Scan all slots in the container
            for (slot in menu.slots) {
                val stack = slot.item
                if (stack.isEmpty) continue
                
                val stackName = stack.hoverName.string
                
                // Get lore from item
                val loreComponent = stack.get(DataComponents.LORE) ?: continue
                val loreLines = loreComponent.lines()
                val loreString = loreLines.joinToString(" ") { it.string }
                
                // Special case: Check if this is Raphael
                if (loreString.contains("Raphael") || stackName.contains("Raphael")) {
                    val progressPattern = java.util.regex.Pattern.compile("\\((\\d+)/(\\d+)\\)")
                    val progressMatcher = progressPattern.matcher(loreString)
                    if (progressMatcher.find()) {
                        raphaelProgress = progressMatcher.group(1).toIntOrNull() ?: 0
                        raphaelMaxProgress = progressMatcher.group(2).toIntOrNull() ?: 10
                        Melinoe.logger.info("[BossTracker] Scanned Raphael progress from menu: $raphaelProgress/$raphaelMaxProgress")
                    }
                    continue
                }
                
                // Try to match regular boss pattern
                val bossNameMatcher = Constants.BOSS_ITEM_NAME_PATTERN.matcher(stackName)
                if (!bossNameMatcher.find()) continue
                
                val bossName = bossNameMatcher.group(1)
                
                // Determine boss state from lore
                when {
                    loreString.contains("This boss is alive") -> {
                        updatedBosses[bossName] = State.ALIVE
                    }
                    loreString.contains("This boss has been defeated") -> {
                        updatedBosses[bossName] = State.DEFEATED
                    }
                    loreString.contains("This boss has not spawned") -> {
                        removeBoss(bossName)
                    }
                }
            }
            
            // Update all found bosses
            if (updatedBosses.isNotEmpty()) {
                updatedBosses.forEach { (name, state) ->
                    updateBoss(name, state)
                }
                
                Message.actionBar("<green>Boss Tracker Updated")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Error scanning bosses menu: ${e.message}", e)
        }
    }
    
    /**
     * Boss state enum
     */
    enum class State {
        ALIVE,
        DEFEATED_PORTAL_ACTIVE,
        DEFEATED,
        SHADOWLANDS_IDLE
    }
    
    /**
     * Tracked boss data class
     */
    data class TrackedBoss(
        val name: String,
        val spawnPosition: BlockPos,
        var state: State,
        val data: BossData
    ) {
        var distanceMarkerValue: Double = 0.0
        var calledPlayerName: String? = null
        var portalTimer: Int = Constants.PORTAL_TIMER_NORMAL
        private var portalTimerStartTick: Long = 0
        
        // Cached ItemStacks for rendering
        private var _bossIcon: ItemStack? = null
        private var _compass: ItemStack? = null
        
        /**
         * Get or create the boss icon ItemStack
         */
        fun getBossIcon(): ItemStack {
            if (_bossIcon == null) {
                _bossIcon = data.createItemStack()
            }
            return _bossIcon!!
        }
        
        /**
         * Get or create the compass ItemStack with lodestone tracker
         */
        fun getCompass(): ItemStack {
            if (_compass == null) {
                _compass = createCompassItem()
            }
            return _compass!!
        }
        
        /**
         * Create a compass item pointing to the boss location
         */
        private fun createCompassItem(): ItemStack {
            val compass = ItemStack(Items.COMPASS)
            
            val dimensionKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("telos", "realm")
            )
            
            val globalPos = GlobalPos(dimensionKey, spawnPosition)
            val lodestoneTracker = LodestoneTracker(Optional.of(globalPos), false)
            
            compass.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker)
            compass.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
            
            return compass
        }
        
        fun resetPortalTimer(ticks: Int = Constants.PORTAL_TIMER_NORMAL) {
            portalTimer = ticks
            portalTimerStartTick = mc.level?.gameTime ?: 0
        }
        
        /**
         * Update portal timer based on server ticks (not affected by pauses)
         */
        fun updatePortalTimerFromServerTicks(): Int {
            if (portalTimerStartTick == 0L) {
                portalTimerStartTick = mc.level?.gameTime ?: 0
            }
            
            val currentTick = mc.level?.gameTime ?: 0
            val elapsedTicks = currentTick - portalTimerStartTick
            
            val initialTimer = if (name == "Raphael") Constants.PORTAL_TIMER_RAPHAEL else Constants.PORTAL_TIMER_NORMAL
            portalTimer = (initialTimer - elapsedTicks).toInt().coerceAtLeast(0)
            return portalTimer
        }
    }
}