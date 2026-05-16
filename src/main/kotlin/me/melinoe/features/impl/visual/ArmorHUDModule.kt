package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
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
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/magical/chestplate/ex-spiritbloom")))
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/light/leggings/ut-onyx")))
            armorItems.add(createCustomItem(Identifier.fromNamespaceAndPath("telos", "material/armour/heavy/boots/ut-timelost")))
        } else {
            armorItems.add(player.getItemBySlot(EquipmentSlot.HEAD))
            armorItems.add(player.getItemBySlot(EquipmentSlot.CHEST))
            armorItems.add(player.getItemBySlot(EquipmentSlot.LEGS))
            armorItems.add(player.getItemBySlot(EquipmentSlot.FEET))
        }
        
        var currentX = 0
        var currentY = 0
        val spacing = 16
        
        for (stack in armorItems) {
            if (!stack.isEmpty) {
                item(stack, currentX, currentY)
            }
            
            if (isHorizontal) {
                currentX += spacing
            } else {
                currentY += spacing
            }
        }
        
        // Calculate final dimensions of the widget
        val width = if (isHorizontal) armorItems.size * spacing else spacing
        val height = if (isHorizontal) spacing else armorItems.size * spacing
        
        width to height
    }
}