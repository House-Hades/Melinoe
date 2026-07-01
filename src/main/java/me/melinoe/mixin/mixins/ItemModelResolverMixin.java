package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets the Hide Armor module's "Show True Items" reveal swap a cosmetic skin for the real model
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack melinoe$revealTrueItem(ItemStack stack) {
        return HideArmorModule.INSTANCE.resolveRenderStack(stack);
    }
}
