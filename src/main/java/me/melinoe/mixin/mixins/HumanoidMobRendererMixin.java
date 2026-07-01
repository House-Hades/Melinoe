package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The render state's per-slot armor is populated by getEquipmentIfRenderable, which drops any
 * piece that isn't classed as armor (no equipment asset)
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {

    @Inject(method = "getEquipmentIfRenderable", at = @At("HEAD"), cancellable = true)
    private static void melinoe$revealWornArmor(LivingEntity entity, EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (!HideArmorModule.isRevealActive()) return;

        ItemStack real = entity.getItemBySlot(slot);
        ItemStack worn = HideArmorModule.INSTANCE.resolveWornStack(real);
        // Only override when it actually resolved to a real worn asset
        if (worn != real) {
            cir.setReturnValue(worn);
        }
    }
}