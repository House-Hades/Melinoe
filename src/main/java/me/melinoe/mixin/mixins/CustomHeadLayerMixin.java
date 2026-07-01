package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides helmets that render as a head item,oOnly actual armour helmets are hidden
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CustomHeadLayerMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void melinoe$hideCustomHeadModel(
            T entity,
            S state,
            float partialTick,
            CallbackInfo ci
    ) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty()) return;

        // During a reveal, a worn helmet renders via the 3D armor layer, so drop its flat head item
        if (HideArmorModule.INSTANCE.revealsAsWorn(stack)) {
            state.headItem.clear();
            return;
        }

        // Hide only real armour helmets, never other models that are also rendered on the head
        if (HideArmorModule.INSTANCE.getEnabled() && HideArmorModule.getHiding()
                && HideArmorModule.INSTANCE.getHideHelmet() && !HideArmorModule.isRevealActive()) {
            if (!HideArmorModule.INSTANCE.getHideOthers() && entity.getId() != Minecraft.getInstance().player.getId()) return;

            if (HideArmorModule.INSTANCE.isArmorPiece(stack)) {
                state.headItem.clear();
            }
        }
    }
}