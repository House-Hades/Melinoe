package me.melinoe.clickgui.settings.impl

import me.melinoe.clickgui.ClickGUI.gray38
import me.melinoe.clickgui.settings.RenderableSetting
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.Color.Companion.darker
import me.melinoe.utils.Colors
import me.melinoe.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class ActionSetting(
    name: String,
    desc: String,
    private val label: (() -> String)? = null,
    private val enabled: (() -> Boolean)? = null,
    override val default: () -> Unit = {}
) : RenderableSetting<() -> Unit>(name, desc) {

    override var value: () -> Unit = default

    var action: () -> Unit by this::value

    private val isEnabled: Boolean get() = enabled?.invoke() ?: true
    private val displayText: String get() = label?.invoke() ?: name

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.rect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, gray38.rgba, 6f)
        NVGRenderer.hollowRect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, 2f, ClickGUIModule.clickGUIColor.rgba, 6f)

        val text = displayText
        val textWidth = NVGRenderer.textWidth(text, 16f, NVGRenderer.defaultFont)
        val textColor = when {
            !isEnabled -> DISABLED_TEXT
            isHovered -> Colors.WHITE.darker().rgba
            else -> Colors.WHITE.rgba
        }
        NVGRenderer.text(text, x + width / 2f - textWidth / 2, y + height / 2f - 8f, 16f, textColor, NVGRenderer.defaultFont)
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0 || !isHovered || !isEnabled) return false
        action()
        return true
    }

    private companion object {
        private val DISABLED_TEXT = 0xFF888888.toInt()
    }
}