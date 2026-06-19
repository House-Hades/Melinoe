package me.melinoe.clickgui.settings.impl

import me.melinoe.Melinoe
import me.melinoe.utils.Colors
import me.melinoe.utils.ui.isAreaHovered
import me.melinoe.utils.render.hollowFill
import net.minecraft.client.gui.GuiGraphicsExtractor

open class HudElement(
    var x: Int,
    var y: Int,
    var scale: Float,
    var enabled: Boolean = true,
    val render: GuiGraphicsExtractor.(Boolean) -> Pair<Int, Int> = { _ -> 0 to 0 }
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /**
     * Whether this element is anchored to the right side of the screen.
     * When true, [x] stores the distance from the right edge so that the element
     * grows leftward when scaled up, staying anchored to the right.
     */
    var anchorRight: Boolean = false

    /**
     * Whether this element is anchored to the bottom of the screen.
     * When true, [y] stores the distance from the bottom edge so that the element
     * grows upward when scaled up, staying anchored to the bottom.
     */
    var anchorBottom: Boolean = false

    /**
     * Returns the actual screen X position, resolving [anchorRight] if set.
     */
    val screenX: Int
        get() = if (anchorRight) {
            Melinoe.mc.window.screenWidth - x - (width * scale).toInt()
        } else {
            x
        }

    /**
     * Returns the actual screen Y position, resolving [anchorBottom] if set.
     */
    val screenY: Int
        get() = if (anchorBottom) {
            Melinoe.mc.window.screenHeight - y - (height * scale).toInt()
        } else {
            y
        }

    /**
     * Updates [x] from an absolute screen X position, respecting [anchorRight].
     */
    fun setScreenX(absoluteX: Int) {
        x = if (anchorRight) {
            Melinoe.mc.window.screenWidth - absoluteX - (width * scale).toInt()
        } else {
            absoluteX
        }
    }

    /**
     * Updates [y] from an absolute screen Y position, respecting [anchorBottom].
     */
    fun setScreenY(absoluteY: Int) {
        y = if (anchorBottom) {
            Melinoe.mc.window.screenHeight - absoluteY - (height * scale).toInt()
        } else {
            absoluteY
        }
    }

    fun draw(context: GuiGraphicsExtractor, example: Boolean) {
        val drawX = screenX
        val drawY = screenY
        context.pose().pushMatrix()
        context.pose().translate(drawX.toFloat(), drawY.toFloat())

        context.pose().scale(scale, scale)
        val (width, height) = context.render(example).let { (w, h) -> w to h }

        context.pose().popMatrix()
        if (example) context.hollowFill(drawX - 1, drawY - 1, (width * scale).toInt(), (height * scale).toInt(), if (isHovered()) 2 else 1, Colors.WHITE)

        this.width = width
        this.height = height
    }

    fun isHovered(): Boolean = isAreaHovered(screenX.toFloat(), screenY.toFloat(), width * scale, height * scale)
}
