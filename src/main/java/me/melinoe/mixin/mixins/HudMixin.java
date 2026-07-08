package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import me.melinoe.features.impl.visual.ItemRarityModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudMixin {

    @Inject(
        method = "extractItemHotbar",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            ordinal = 0
        )
    )
    private void melinoe$drawHotbarRarityIndicator(
        CallbackInfo ci,
        @Local(name = "graphics") GuiGraphicsExtractor graphics,
        @Local(name = "i") int index,
        @Local(name = "x") int x,
        @Local(name = "y") int y,
        @Local(name = "player") Player player
    ) {
        ItemStack stack = player.getInventory().getNonEquipmentItems().get(index);
        if (stack.isEmpty()) return;

        ItemRarityModule.drawHotbarIndicator(stack, graphics, x, y);
    }
}