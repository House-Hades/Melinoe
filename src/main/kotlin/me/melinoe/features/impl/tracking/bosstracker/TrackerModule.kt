package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
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
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF8A0000.toInt()), desc = "Color for the widget border and title")
    
    private val showWaypoints by BooleanSetting("Show Waypoints", true, desc = "Show waypoints at boss locations")
    
    private val waypointBeams by BooleanSetting("Waypoint Beams", true, desc = "Show beams at boss waypoints")
    
    private val maxTextScale by NumberSetting<Double>("Max Text Scale", 1.0, 0.1, 3.0, 0.1, desc = "Maximum scale for waypoint text when far away")
    
    // Filtering settings
    private val showAvailable by BooleanSetting("Show Available", true, desc = "Show waypoints for available bosses (white)")
    
    private val showFighting by BooleanSetting("Show Fighting", true, desc = "Show waypoints for bosses being fought (green)")
    
    private val showPortal by BooleanSetting("Show Portal", true, desc = "Show waypoints for defeated bosses with portal (gold)")
    
    // Distance settings
    private val maxRenderDistance by NumberSetting<Double>("Max Distance", 1000.0, 100.0, 5000.0, 50.0, desc = "Hide waypoints farther than this (blocks)")
    
    private val fadeDistance by NumberSetting<Double>("Fade Distance", 50.0, 10.0, 200.0, 10.0, desc = "Distance over which waypoints fade in/out (blocks)")
    
    private val quickTeleportKey by KeybindSetting("Quick Teleport", GLFW.GLFW_KEY_Y, desc = "Teleport to player at boss when looking at waypoint")
        .onPress { handleQuickTeleport() }

    // Previous world and dimension for detecting changes
    private var previousWorld: String = ""
    private var previousDimension: String = ""
    
    // Timers
    private var distanceUpdateTimer = 0
    
    init {
        // Register tick event for distance updates and portal timers
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (!me.melinoe.utils.ServerUtils.isOnTelos()) return@on
            
            val player = mc.player ?: return@on
            
            // Update distances every 20 ticks (1 second)
            distanceUpdateTimer = (distanceUpdateTimer + 1) % Constants.DISTANCE_UPDATE_INTERVAL
            
            // Update portal timers
            BossState.updatePortalTimers()
            
            // Update distance markers once per second
            if (distanceUpdateTimer == 0) {
                BossState.updateDistanceMarkers()
            }
        }
        
        // Register chat event for boss messages
        on<ChatPacketEvent> {
            if (!enabled) return@on
            if (!me.melinoe.utils.ServerUtils.isOnTelos()) return@on
            
            val shouldHide = ChatParser.handleChatMessage(value)
            if (shouldHide) {
                this.hideMessage()
            }
        }
        
        // Register GUI close event for /bosses menu scanning
        on<GuiEvent.Close> {
            if (!enabled) return@on
            if (!me.melinoe.utils.ServerUtils.isOnTelos()) return@on
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
            if (!me.melinoe.utils.ServerUtils.isOnTelos()) return@on
            if (!shouldShowTracker()) return@on
            
            // Update renderer settings
            RendererWaypoints.showWaypoints = showWaypoints
            RendererWaypoints.waypointBeams = waypointBeams
            RendererWaypoints.maxTextScale = maxTextScale
            RendererWaypoints.showAvailable = showAvailable
            RendererWaypoints.showFighting = showFighting
            RendererWaypoints.showPortal = showPortal
            RendererWaypoints.maxRenderDistance = maxRenderDistance
            RendererWaypoints.fadeDistance = fadeDistance
            
            RendererWaypoints.render(context)
        }
    }

    /**
     * Handle quick teleport keybind
     */
    private fun handleQuickTeleport() {
        if (!enabled) return
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) return
        
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
                Message.success("Teleporting to §f$targetPlayerName §aat §f${lookingAtWaypoint.name}")
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
            return
        }
        
        if (previousWorld.isEmpty()) {
            Melinoe.logger.info("[BossTracker] First world load: $currentWorld (dimension: $currentDimension)")
            previousWorld = currentWorld
            previousDimension = currentDimension
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
        } else {
            Melinoe.logger.info("[BossTracker] World/Dimension change but no clear needed")
        }
        
        previousWorld = currentWorld
        previousDimension = currentDimension
    }
}
