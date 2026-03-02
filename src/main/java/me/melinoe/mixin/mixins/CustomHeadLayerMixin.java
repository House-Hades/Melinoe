package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide custom head/helmet rendering (including 3D resource pack helmets).
 * This handles helmets with custom models from resource packs.
 * Only affects players, not other humanoid entities.
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <S extends LivingEntityRenderState> void melinoe$hideCustomHead(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int light,
            S livingEntityRenderState,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (HideArmorModule.INSTANCE.getEnabled()) {
            // Only hide for players (AvatarRenderState)
            // This excludes zombies, skeletons, piglins, and other humanoid mobs
            if (livingEntityRenderState instanceof HumanoidRenderState humanoidRenderState &&
                humanoidRenderState instanceof AvatarRenderState) {
                // Check if it's the local player (nameTag is null for local player)
                boolean isLocalPlayer = humanoidRenderState.nameTag == null;
                if (isLocalPlayer) {
                    ci.cancel();
                }
            }
        }
    }
}
