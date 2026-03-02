package me.melinoe.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import me.melinoe.clickgui.ClickGUI.gray38
import me.melinoe.clickgui.settings.RenderableSetting
import me.melinoe.clickgui.settings.Saving
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.Color.Companion.brighter
import me.melinoe.utils.Colors
import me.melinoe.utils.ui.animations.LinearAnimation
import me.melinoe.utils.ui.isAreaHovered
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc), Saving {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    private val toggleAnimation = LinearAnimation<Float>(200)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.rect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, if (isHovered) gray38.brighter().rgba else gray38.rgba, 9f)

        if (enabled || toggleAnimation.isAnimating()) {
            val color = ClickGUIModule.clickGUIColor
            NVGRenderer.rect(
                x + width - 40f,
                y + height / 2f - 10f,
                toggleAnimation.get(34f, 9f, enabled),
                20f,
                if (isHovered) color.brighter().rgba else color.rgba,
                9f
            )
        }

        NVGRenderer.hollowRect(
            x + width - 40f,
            y + height / 2f - 10f,
            34f,
            20f,
            2f,
            ClickGUIModule.clickGUIColor.rgba,
            9f
        )
        NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !enabled), y + height / 2f, 6f, Colors.WHITE.rgba)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            toggleAnimation.start()
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 43f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)

    override fun write(gson: Gson): JsonElement = JsonPrimitive(enabled)

    override fun read(element: JsonElement, gson: Gson) {
        enabled = element.asBoolean
    }
}
