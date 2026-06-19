package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.features.impl.combat.ArmorCooldownsModule
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Armor HUD Module - Displays equipped armor on the hud.
 */
object ArmorHUDModule : Module(
    name = "Armor HUD",
    category = Category.VISUAL,
    description = "Shows your currently equipped armor on the screen."
) {
    private val direction by SelectorSetting("Orientation", "Horizontal", listOf("Horizontal", "Vertical"), "Orientation of the armor being displayed.")
    private const val HORIZONTAL = 0
    
    private val hud by HUDSetting(
        name = "Armor Display",
        x = 10,
        y = 50,
        scale = 1f,
        toggleable = false,
        description = "Position of the armor HUD.",
        module = this
    ) { example ->
        if (!enabled && !example) return@HUDSetting 0 to 0
        
        val isHorizontal = direction == HORIZONTAL
        val player = mc.player
        
        val armorItems = mutableListOf<ItemStack>()
        
        if (example || player == null) {
            // Helper to create an item with a custom model
            fun createCustomItem(model: Identifier): ItemStack {
                val stack = ItemStack(Items.CARROT_ON_A_STICK)
                stack.set(DataComponents.ITEM_MODEL, model)
                return stack
            }

            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/heavy/helmet/ut-mandorla")))
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/magical/chestplate/ut-spiritbloom")))
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/light/leggings/ut-onyx")))
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/heavy/boots/ut-timelost")))
        } else {
            // Each worn slot, followed by any swapped-off pieces of that slot still on cooldown
            for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                armorItems.add(player.getItemBySlot(slot))
                if (ArmorCooldownsModule.enabled) {
                    armorItems.addAll(ArmorCooldownsModule.extraPieces(slot))
                }
            }
        }

        val iconSize = 16
        val barGap = 1
        val barH = ArmorCooldownsModule.BAR_HEIGHT
        val barsOn = example || (ArmorCooldownsModule.enabled && ArmorCooldownsModule.showBars.value)
        // Extra vertical room reserved per icon for the cooldown bar
        val cellExtra = if (barsOn) barGap + barH else 0
        // Sample values for HUD editor
        val examplePreview = listOf(0f, 0.35f, 0.7f, 1f)

        var currentX = 0
        var currentY = 0
        val rowAdvance = if (isHorizontal) iconSize else iconSize + cellExtra

        for ((index, stack) in armorItems.withIndex()) {
            if (!stack.isEmpty) {
                item(stack, currentX, currentY)

                val barY = currentY + iconSize + barGap
                if (example) {
                    ArmorCooldownsModule.renderArmorBar(this, currentX, barY, iconSize, examplePreview[index % examplePreview.size])
                } else if (barsOn && ArmorCooldownsModule.hasAbility(stack)) {
                    ArmorCooldownsModule.renderArmorBar(this, currentX, barY, iconSize, ArmorCooldownsModule.progressFor(stack))
                }
            }

            if (isHorizontal) {
                currentX += iconSize
            } else {
                currentY += rowAdvance
            }
        }

        // Calculate final dimensions of the widget
        val width = if (isHorizontal) armorItems.size * iconSize else iconSize
        val height = if (isHorizontal) iconSize + cellExtra else armorItems.size * rowAdvance

        width to height
    }
}