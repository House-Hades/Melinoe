package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.GuiEvent
import me.melinoe.events.RenderEvent
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.ChatManager.hideMessage
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.equalsOneOf
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW

/**
 * Boss Tracker Module - displays boss kill tracking information
 */
object TrackerModule : Module(
    name = "Boss Tracker",
    category = Category.TRACKING,
    description = "Tracks realm bosses and displays their status"
) {
    
    // Settings
    private val autoTrack by BooleanSetting("Auto Track", true, desc = "Automatically run /bosses when entering a realm")
    private val showHud by BooleanSetting("Show HUD", true, desc = "Show the boss list HUD")
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF2E8F78.toInt()), desc = "Color for the widget border and title")
    private val showWaypoints by BooleanSetting("Show Waypoints", true, desc = "Show waypoints at boss locations")
    private val waypointBeams by BooleanSetting("Waypoint Beams", true, desc = "Show beams at boss waypoints").withDependency { showWaypoints }

    // Filtering settings
    private val waypointStatusDropdown by DropdownSetting("Waypoint Status", false, desc = "Filter waypoints by boss status").withDependency { showWaypoints }
    private val showAvailable by BooleanSetting("Show Available", true, desc = "Show waypoints for available bosses (white)").withDependency { waypointStatusDropdown }
    private val showFighting by BooleanSetting("Show Fighting", true, desc = "Show waypoints for bosses being fought (green)").withDependency { waypointStatusDropdown }
    private val showPortal by BooleanSetting("Show Portal", true, desc = "Show waypoints for defeated bosses with portal (gold)").withDependency { waypointStatusDropdown }
    
    private val quickTeleportKey by KeybindSetting("Quick Teleport", GLFW.GLFW_KEY_Y, desc = "Teleport to the player at the boss when looking at the waypoint")
        .onPress { handleQuickTeleport() }.withDependency { showWaypoints }
    
    // Previous world and dimension for detecting changes
    private var previousWorld: String = ""
    private var previousDimension: String = ""
    
    private enum class AutoTrackState {
        IDLE, WAITING_TO_SEND, WAITING_FOR_GUI, WAITING_TO_CLOSE
    }
    
    private var trackState = AutoTrackState.IDLE
    private var stateActionTime = 0L
    private var guiOpenTime = 0L
    
    // Timers
    private var distanceUpdateTimer = 0
    
    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            
            val player = mc.player ?: return@on
            val now = System.currentTimeMillis()
            
            when (trackState) {
                AutoTrackState.WAITING_TO_SEND -> {
                    if (now >= stateActionTime) {
                        if (mc.screen != null) {
                            // Player has a menu open, wait
                            stateActionTime = now + 500L
                        } else {
                            player.connection?.sendCommand("bosses")
                            trackState = AutoTrackState.WAITING_FOR_GUI
                            stateActionTime = now + 10000L
                        }
                    }
                }
                AutoTrackState.WAITING_FOR_GUI -> {
                    if (mc.screen != null) {
                        if (mc.screen is AbstractContainerScreen<*>) {
                            trackState = AutoTrackState.WAITING_TO_CLOSE
                            guiOpenTime = now
                            stateActionTime = now + 1500L // Prevent being stuck open for more than 1.5s
                        } else {
                            trackState = AutoTrackState.IDLE
                        }
                    } else if (now >= stateActionTime) {
                        trackState = AutoTrackState.IDLE
                    }
                }
                AutoTrackState.WAITING_TO_CLOSE -> {
                    val screen = mc.screen
                    if (screen !is AbstractContainerScreen<*>) {
                        trackState = AutoTrackState.IDLE
                        return@on
                    }
                    
                    val timeOpen = now - guiOpenTime
                    var hasItems = false
                    
                    try {
                        val menu = screen.menu
                        val topSize = menu.slots.size - 36
                        if (topSize > 0) {
                            hasItems = (0 until topSize).any { menu.getSlot(it).hasItem() }
                        }
                    } catch (e: Exception) {
                        // Ignore potential sync errors from Minecraft during screen transitions
                    }
                    
                    // Closure settings which dynamically works with ping
                    val shouldClose = (hasItems && timeOpen >= 50L) || timeOpen >= 500L
                    
                    if (shouldClose || now >= stateActionTime) {
                        // Force a manual scan right before closing to guarantee data isn't lost
                        BossState.scanBossesMenu(screen)
                        
                        // Close properly by sending the server packet AND clearing the client screen
                        player.closeContainer()
                        mc.setScreen(null)
                        
                        trackState = AutoTrackState.IDLE
                    }
                }
                AutoTrackState.IDLE -> {}
            }
            
            // Update distances periodically
            distanceUpdateTimer = (distanceUpdateTimer + 1) % Constants.DISTANCE_UPDATE_INTERVAL
            
            BossState.updatePortalTimers()
            
            if (distanceUpdateTimer == 0) {
                BossState.updateDistanceMarkers()
            }
        }
        
        // Register chat event for boss messages
        on<ChatPacketEvent> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            
            val shouldHide = ChatParser.handleChatMessage(value) && showHud
            if (shouldHide) {
                this.hideMessage()
            }
        }
        
        // Register GUI close event for /bosses menu scanning (manual scan)
        on<GuiEvent.Close> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            if (screen !is AbstractContainerScreen<*>) return@on
            
            BossState.scanBossesMenu(screen)
        }
        
        // Register world load event to clear bosses when changing realms
        on<WorldLoadEvent> {
            if (!enabled) return@on
            handleWorldChange()
        }
        
        // Register world render event for waypoint rendering
        on<RenderEvent.Last> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            if (!shouldShowTracker()) return@on
            
            // Update renderer settings
            RendererWaypoints.showWaypoints = showWaypoints
            RendererWaypoints.waypointBeams = waypointBeams
            RendererWaypoints.showAvailable = showAvailable
            RendererWaypoints.showFighting = showFighting
            RendererWaypoints.showPortal = showPortal
            
            // Update HUD settings
            RendererHUD.showHud = showHud
            RendererHUD.widgetColor = widgetColor

            RendererWaypoints.render(context)
        }
    }
    
    /**
     * Start the sequence to safely run the bosses command automatically
     */
    private fun startAutoTrack() {
        if (!autoTrack) return
        trackState = AutoTrackState.WAITING_TO_SEND
        stateActionTime = System.currentTimeMillis() + 1000L // Give a 1 second buffer after realm entry for lag/safety
    }
    
    /**
     * Handle quick teleport keybind
     */
    private fun handleQuickTeleport() {
        if (!enabled) return
        if (!ServerUtils.isOnTelos()) return
        
        val player = mc.player ?: return
        val playerName = player.gameProfile.name
        
        // Find the waypoint the player is looking at
        val lookingAtWaypoint = BossState.getAllBosses()
            .filter { it.calledPlayerName != null }
            .map { BossWaypoint(it) }
            .firstOrNull { it.isLookingAt() }
        
        if (lookingAtWaypoint != null) {
            val targetPlayerName = lookingAtWaypoint.boss.calledPlayerName
            
            if (targetPlayerName.equals(playerName, ignoreCase = true)) {
                Message.error("You cannot teleport to yourself!")
                return
            }
            
            val command = lookingAtWaypoint.getTeleportCommand()
            if (command != null) {
                player.connection?.sendCommand(command.removePrefix("/"))
                Message.success("<gray>Teleporting to <yellow>$targetPlayerName <gray>at <yellow>${lookingAtWaypoint.name}")
            }
        } else {
            Message.error("Look at a boss waypoint with a player to teleport")
        }
    }
    
    /**
     * HUD rendering
     */
    private val bossTrackerHud by RendererHUD.createHUDSetting(this).also {
        RendererHUD.widgetColor = widgetColor
        RendererHUD.showHud = showHud
    }

    /**
     * Check if we should show the tracker (only in realms)
     */
    private fun shouldShowTracker(): Boolean {
        val level = mc.level ?: return false
        val dimensionPath = level.dimension().location().path
        return dimensionPath == Constants.DIMENSION_REALM
    }
    
    /**
     * Handle world change - clear bosses when leaving realm or switching realms
     */
    private fun handleWorldChange() {
        val currentWorld = LocalAPI.getCurrentCharacterWorld()
        val level = mc.level
        val currentDimension = level?.dimension()?.location()?.path ?: ""
        
        if (currentWorld.isEmpty() || currentDimension.isEmpty()) {
            Melinoe.logger.info("[BossTracker] World change: empty world/dimension, skipping")
            
            previousWorld = ""
            previousDimension = ""
            trackState = AutoTrackState.IDLE // Reset process if disconnected
            return
        }
        
        if (previousWorld.isEmpty()) {
            Melinoe.logger.info("[BossTracker] First world load: $currentWorld (dimension: $currentDimension)")
            previousWorld = currentWorld
            previousDimension = currentDimension
            
            if (currentDimension == Constants.DIMENSION_REALM) {
                startAutoTrack()
            }
            return
        }
        
        if (currentWorld == previousWorld && currentDimension == previousDimension) {
            return
        }
        
        Melinoe.logger.info("[BossTracker] World/Dimension change detected: '$previousWorld/$previousDimension' -> '$currentWorld/$currentDimension'")
        
        val wasDungeon = previousDimension == Constants.DIMENSION_DUNGEON
        val isDungeonNow = currentDimension == Constants.DIMENSION_DUNGEON
        
        if (wasDungeon != isDungeonNow) {
            Melinoe.logger.info("[BossTracker] Dungeon transition detected, preserving ${BossState.getAllBosses().size} bosses")
            previousWorld = currentWorld
            previousDimension = currentDimension
            return
        }
        
        val wasInHub = previousDimension.equalsOneOf(Constants.DIMENSION_HUB, Constants.DIMENSION_DAILY)
        val isInHub = currentDimension.equalsOneOf(Constants.DIMENSION_HUB, Constants.DIMENSION_DAILY)
        
        if (!wasInHub && isInHub) {
            Melinoe.logger.info("[BossTracker] Left realm for hub/missions, clearing ${BossState.getAllBosses().size} bosses")
            BossState.clearAll()
        } else if (previousDimension == Constants.DIMENSION_REALM && currentDimension == Constants.DIMENSION_REALM && currentWorld != previousWorld) {
            Melinoe.logger.info("[BossTracker] Switched between realms, clearing ${BossState.getAllBosses().size} bosses")
            BossState.clearAll()
            startAutoTrack()
        } else if (currentDimension == Constants.DIMENSION_REALM && (wasInHub || previousDimension != Constants.DIMENSION_REALM)) {
            Melinoe.logger.info("[BossTracker] Entered realm from hub")
            startAutoTrack()
        } else {
            Melinoe.logger.info("[BossTracker] World/Dimension change but no clear needed")
        }
        
        previousWorld = currentWorld
        previousDimension = currentDimension
    }
}