package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.render.drawStyledBox
import me.melinoe.utils.renderBoundingBox
import net.minecraft.client.CameraType
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player

/**
 * Hitboxes Module - renders entity hitboxes with optional fill.
 * Similar to F3+B debug hitboxes but with customizable colors and fill.
 */
object HitboxModule : Module(
    name = "Hitboxes",
    category = Category.VISUAL,
    description = "Renders entity hitboxes with customizable colors and fill."
) {
    
    private val renderStyle by SelectorSetting("Render Style", "Filled Outline", listOf("Outline", "Filled Outline"), desc = "Style of the box.")
    
    private val personalSetting by BooleanSetting("Personal", true, desc = "Render hitbox for yourself")
    private val yourselfColor by ColorSetting("Personal Color", Color(0xFF4C2882.toInt()), true, desc = "Color of your hitbox").withDependency { personalSetting }
    
    private val otherPlayersSetting by BooleanSetting("Other Players", true, desc = "Render hitboxes for other players")
    private val playersColor by ColorSetting("Other Players Color", Color(0xFF283582.toInt()), true, desc = "Color of player hitboxes").withDependency { otherPlayersSetting }
    
    private val mobsSetting by BooleanSetting("Mobs", true, desc = "Render hitboxes for mobs")
    private val mobsColor by ColorSetting("Mobs Color", Color(0xFFB23939.toInt()), true, desc = "Color of mob hitboxes").withDependency { mobsSetting }
    
    private val itemsSetting by BooleanSetting("Items", true, desc = "Render hitboxes for items")
    private val itemsColor by ColorSetting("Items Color", Color(0xFF39B293.toInt()), true, desc = "Color of item hitboxes").withDependency { itemsSetting }
    
    private val hideArmorStandsSetting by BooleanSetting("Hide Armor Stands", true, desc = "Don't render hitboxes for armor stands")
    
    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on // Don't render if module is disabled
            
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on
            
            // Render player's own hitbox (only in third person)
            if (personalSetting && mc.options.cameraType != CameraType.FIRST_PERSON) {
                // Use interpolated render bounding box for smooth movement
                val box = player.renderBoundingBox
                
                drawStyledBox(box, yourselfColor, yourselfColor, renderStyle, true)
            }
            
            // Iterate through all other entities in the level
            for (entity in level.entitiesForRendering()) {
                // Skip the player itself (already rendered above)
                if (entity == player) continue
                
                // Skip armor stands if the setting is enabled
                if (entity is ArmorStand && hideArmorStandsSetting) continue
                
                // Filter entity types based on settings and get color
                val boxColor = when (entity) {
                    is Player -> {
                        if (!otherPlayersSetting) continue
                        playersColor
                    }
                    is ItemEntity -> {
                        if (!itemsSetting) continue
                        itemsColor
                    }
                    else -> {
                        if (!mobsSetting) continue
                        mobsColor
                    }
                }
                
                // Use interpolated render bounding box for smooth movement
                val box = entity.renderBoundingBox
                
                // Skip hitboxes that are too thin (like interaction entities)
                // These render as lines which look like artifacts
                val width = box.maxX - box.minX
                val height = box.maxY - box.minY
                val depth = box.maxZ - box.minZ
                val minSize = 0.01 // Minimum size threshold
                
                if (width < minSize || height < minSize || depth < minSize) continue
                
                drawStyledBox(box, boxColor, boxColor, renderStyle, true)
            }
        }
    }
}