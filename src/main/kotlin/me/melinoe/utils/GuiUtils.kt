package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.mixin.accessors.AbstractContainerScreenAccessor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

/**
 * Utility functions for working with GUI screens.
 * Uses accessors to get internal screen state.
 */
object GuiUtils {
    
    /**
     * Get the currently hovered slot in a container screen, or null if none.
     */
    fun getHoveredSlot(): Slot? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            return (screen as AbstractContainerScreenAccessor).hoveredSlot
        }
        return null
    }
    
    /**
     * Get the X position (left edge) of the container GUI.
     */
    fun getContainerX(): Int? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            return (screen as AbstractContainerScreenAccessor).x
        }
        return null
    }
    
    /**
     * Get the Y position (top edge) of the container GUI.
     */
    fun getContainerY(): Int? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            return (screen as AbstractContainerScreenAccessor).y
        }
        return null
    }
    
    /**
     * Get the width of the container GUI.
     */
    fun getContainerWidth(): Int? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            return (screen as AbstractContainerScreenAccessor).width
        }
        return null
    }
    
    /**
     * Get the height of the container GUI.
     */
    fun getContainerHeight(): Int? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            return (screen as AbstractContainerScreenAccessor).height
        }
        return null
    }
    
    /**
     * Get the bounds of the container GUI as (x, y, width, height).
     * Returns null if not in a container screen.
     */
    fun getContainerBounds(): ContainerBounds? {
        val screen = Melinoe.mc.screen
        if (screen is AbstractContainerScreen<*>) {
            val accessor = screen as AbstractContainerScreenAccessor
            return ContainerBounds(
                x = accessor.x,
                y = accessor.y,
                width = accessor.width,
                height = accessor.height
            )
        }
        return null
    }
    
    /**
     * Check if a screen position (x, y) is inside the container GUI bounds.
     */
    fun isInsideContainer(screenX: Int, screenY: Int): Boolean {
        val bounds = getContainerBounds() ?: return false
        return screenX >= bounds.x && screenX < bounds.x + bounds.width &&
               screenY >= bounds.y && screenY < bounds.y + bounds.height
    }
    
    data class ContainerBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
}
