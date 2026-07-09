package me.melinoe.features.impl.misc

import com.mojang.blaze3d.platform.InputConstants
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.Message
import me.melinoe.utils.ServerUtils
import org.lwjgl.glfw.GLFW

/**
 * Credit to Wynntils for practically everything related to the Map.
 *
 * Telos Map Module - view the world of Telos Realms in a map with boss & miniboss markers.
 */
object MapModule : Module(
    name = "Telos Map",
    category = Category.MISC,
    description = "Map of the Telos Realms.",
    toggled = true
) {
    // The module's main keybind keeps the default toggle behavior; opening the map is its own bind
    val openMapKey: InputConstants.Key by KeybindSetting("Open Map", GLFW.GLFW_KEY_M, desc = "Opens the map.")
        .onPress { openMap() }

    val showBosses by BooleanSetting("Boss Markers", true, desc = "Show boss spawn locations on the map.")
    val showMinibosses by BooleanSetting("Miniboss Markers", true, desc = "Show possible miniboss spawn locations on the map.")
    val markerScale by NumberSetting("Marker Scale", 1.0, 0.5, 2.0, 0.1, desc = "Size of the markers drawn on the map.")
    val pinDuration by NumberSetting("Pin Duration", 120, 30, 180, 5, desc = "How long a pinned waypoint lasts.", unit = "s")
    val defaultZoom by NumberSetting("Default Zoom", 1.0, 0.25, 4.0, 0.25, desc = "Zoom when opening the map.")
    val rememberView by BooleanSetting("Remember View", false, desc = "Keep the pan/zoom between opens instead of centering on you.")
    val playerArrowColor by ColorSetting("Player Color", Color(124, 255, 178), desc = "Color of your position arrow.")

    init {
        // Pins from the map get their own render pass
        on<RenderEvent.Last> {
            if (!enabled) return@on
            if (!ServerUtils.isOnTelos()) return@on
            MapPins.renderStandalone(context)
        }
    }

    private fun openMap() {
        if (!enabled || !ServerUtils.isOnTelos()) return
        if (LocalAPI.isInDungeon()) {
            Message.error("The map is not available in dungeons.")
            return
        }
        if (LocalAPI.isInNexus()) {
            Message.error("The map is not available in the Nexus.")
            return
        }
        mc.execute {
            MapScreen.prepareOpen()
            mc.setScreen(MapScreen)
        }
    }
}
