package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import me.melinoe.features.impl.visual.PlayerSizeModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for AvatarRenderer to support PlayerSize module.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
    private void melinoe$scale(AvatarRenderState avatarRenderState, PoseStack poseStack, CallbackInfo ci) {
        PlayerSizeModule.preRenderCallbackScaleHook(avatarRenderState, poseStack);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("HEAD")
    )
    private void melinoe$extractRenderState(Avatar avatar, AvatarRenderState avatarRenderState, float f, CallbackInfo ci) {
        if (!(avatar instanceof AbstractClientPlayer clientAvatarEntity)) return;
        avatarRenderState.setData(PlayerSizeModule.getGAME_PROFILE_KEY(), clientAvatarEntity.getGameProfile());
    }
}
