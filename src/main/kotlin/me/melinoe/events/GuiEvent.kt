package me.melinoe.events

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/**
 * GUI rendering events.
 */
abstract class GuiEvent(val screen: Screen) : CancellableEvent() {

    /**
     * Called when rendering with NanoVG in GUI screens.
     */
    class NVGRender(screen: Screen) : GuiEvent(screen)

    /**
     * Called when rendering the game overlay (HUD).
     */
    class Render(screen: Screen, val guiGraphics: GuiGraphics, val tickCounter: Any) : GuiEvent(screen)
    
    /**
     * Called when a GUI screen is opened/initialized.
     */
    class Open(screen: Screen) : GuiEvent(screen)
    
    /**
     * Called when a screen is closed (container screens like /bosses menu)
     */
    class Close(screen: Screen) : GuiEvent(screen)

    /**
     * Called when a slot is clicked in a container screen.
     */
    class SlotClick(screen: Screen, val slotId: Int, val button: Int) : GuiEvent(screen)

    /**
     * Called when a container slot is updated by the server.
     */
    class SlotUpdate(screen: Screen, val packet: ClientboundContainerSetSlotPacket, val menu: AbstractContainerMenu) : GuiEvent(screen)

    /**
     * Called when a mouse button is clicked.
     */
    class MouseClick(screen: Screen, val click: MouseButtonEvent, val doubled: Boolean) : GuiEvent(screen)

    /**
     * Called when a mouse button is released.
     */
    class MouseRelease(screen: Screen, val click: MouseButtonEvent) : GuiEvent(screen)

    /**
     * Called when a key is pressed.
     */
    class KeyPress(screen: Screen, val input: KeyEvent) : GuiEvent(screen)

    /**
     * Called during main GUI rendering.
     */
    class Draw(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int) : GuiEvent(screen)

    /**
     * Called during background rendering.
     */
    class DrawBackground(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int) : GuiEvent(screen)

    /**
     * Called when rendering individual slots.
     */
    class DrawSlot(screen: Screen, val guiGraphics: GuiGraphics, val slot: Slot) : GuiEvent(screen)

    /**
     * Called when rendering tooltips.
     */
    class DrawTooltip(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int) : GuiEvent(screen)
}
