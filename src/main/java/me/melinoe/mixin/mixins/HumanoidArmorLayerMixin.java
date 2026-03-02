package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide armor rendering when Hide Armor module is enabled.
 * Cancels renderArmorPiece to hide armor for players only (not other humanoid entities).
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(
            method = "renderArmorPiece",
            at = @At("HEAD"),
            cancellable = true
    )
    private <S extends HumanoidRenderState> void melinoe$hideArmor(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ItemStack itemStack,
            EquipmentSlot equipmentSlot,
            int light,
            S humanoidRenderState,
            CallbackInfo ci
    ) {
        if (HideArmorModule.INSTANCE.getEnabled()) {
            // Only hide armor for players (AvatarRenderState)
            // This excludes zombies, skeletons, piglins, and other humanoid mobs
            if (humanoidRenderState instanceof AvatarRenderState) {
                // Check if it's the local player (nameTag is null for local player)
                boolean isLocalPlayer = humanoidRenderState.nameTag == null;
                if (isLocalPlayer) {
                    ci.cancel();
                }
            }
        }
    }
}
