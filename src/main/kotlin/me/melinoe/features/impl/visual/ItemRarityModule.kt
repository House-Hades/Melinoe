package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.events.GuiEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.data.Item
import me.melinoe.utils.data.TelosItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.component.DataComponents
import net.minecraft.data.AtlasIds
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/**
 * Displays item rarity indicators in inventory slots and the hotbar.
 */

object ItemRarityModule : Module(
    name = "Item Rarity",
    description = "Shows colored indicators for items based on their rarity",
    category = Category.VISUAL
) {
    private val opacity by NumberSetting("Opacity", 0.3f, 0f, 1f, 0.05f, "Indicator opacity")
    private val shape by SelectorSetting("Shape", "Circular", listOf("Circular", "Square"), "Shape of the rarity indicator.")
    private const val CIRCULAR = 0
    private const val SQUARE = 1

    private val showInHotbar by BooleanSetting("Show in Hotbar", true, "Show rarity indicators in the hotbar")

    private val rarityDropdown = +DropdownSetting("Rarity Filters", false)
    private val showIrradiated = +BooleanSetting("Irradiated", true, "Show Irradiated rarity").withDependency { rarityDropdown.value }
    private val showGilded = +BooleanSetting("Gilded", true, "Show Gilded rarity").withDependency { rarityDropdown.value }
    private val showRoyal = +BooleanSetting("Royal", true, "Show Royal rarity").withDependency { rarityDropdown.value }
    private val showBloodshot = +BooleanSetting("Bloodshot", true, "Show Bloodshot rarity").withDependency { rarityDropdown.value }
    private val showVoidbound = +BooleanSetting("Voidbound", true, "Show Voidbound rarity").withDependency { rarityDropdown.value }
    private val showUnholy = +BooleanSetting("Unholy", true, "Show Unholy rarity").withDependency { rarityDropdown.value }
    private val showCompanion = +BooleanSetting("Companion", true, "Show Companion rarity").withDependency { rarityDropdown.value }
    private val showShiny = +BooleanSetting("Shiny", true, "Show Shiny rarity").withDependency { rarityDropdown.value }

    init {
        on<GuiEvent.DrawSlot> {
            renderIndicator(guiGraphics, slot.item, slot.x, slot.y)
        }
    }

    @JvmStatic
    fun drawHotbarIndicator(stack: ItemStack, guiGraphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!enabled || !showInHotbar) return
        renderIndicator(guiGraphics, stack, x, y)
    }

    private fun renderIndicator(guiGraphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty) return

        val rarity = getItemRarity(stack) ?: return
        if (!shouldShowRarity(rarity)) return

        drawRarityIndicator(guiGraphics, x, y, rarity)
    }

    private fun drawRarityIndicator(
        guiGraphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        rarity: Item.Rarity
    ) {
        val spriteId = if (shape == SQUARE) SQUARE_SPRITE_ID else CIRCULAR_SPRITE_ID
        val sprite = getRarityIndicatorSprite(spriteId) ?: return

        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, colorWithAlpha(rarity))
    }

    private val CIRCULAR_SPRITE_ID: Identifier = Identifier.fromNamespaceAndPath("melinoe", "item_background_circular")
    private val SQUARE_SPRITE_ID: Identifier = Identifier.fromNamespaceAndPath("melinoe", "item_background_square")

    private fun getRarityIndicatorSprite(id: Identifier): TextureAtlasSprite? {
        return Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.GUI).getSprite(id)
    }

    private fun colorWithAlpha(rarity: Item.Rarity): Int {
        val color = TelosItems.rarityColor(rarity)
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    private fun getItemRarity(stack: ItemStack): Item.Rarity? {
        val tooltipStyle = stack.get(DataComponents.TOOLTIP_STYLE) ?: return null
        if (tooltipStyle.namespace != "telos") return null

        val rarityName = tooltipStyle.path.substringAfterLast('/')
        return Item.Rarity.entries.firstOrNull { it.name.equals(rarityName, ignoreCase = true) }
    }

    private fun shouldShowRarity(rarity: Item.Rarity): Boolean {
        return when (rarity) {
            Item.Rarity.IRRADIATED -> showIrradiated.value
            Item.Rarity.GILDED -> showGilded.value
            Item.Rarity.ROYAL -> showRoyal.value
            Item.Rarity.BLOODSHOT -> showBloodshot.value
            Item.Rarity.VOIDBOUND -> showVoidbound.value
            Item.Rarity.UNHOLY -> showUnholy.value
            Item.Rarity.COMPANION -> showCompanion.value
            Item.Rarity.SHINY -> showShiny.value
        }
    }
}