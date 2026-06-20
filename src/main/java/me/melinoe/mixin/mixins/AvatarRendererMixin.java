package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.authlib.GameProfile;
import me.melinoe.Melinoe;
import me.melinoe.features.impl.misc.NoNametagsModule;
import me.melinoe.features.impl.visual.PlayerSizeModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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

    @Inject(
            method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private void melinoe$scale(AvatarRenderState avatarRenderState, PoseStack poseStack, CallbackInfo ci) {
        PlayerSizeModule.preRenderCallbackScaleHook(avatarRenderState, poseStack);
    }

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void melinoe$checkHideNametag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        // Check if we should hide this nametag
        GameProfile gameProfile = state.getData(PlayerSizeModule.getGAME_PROFILE_KEY());
        if (gameProfile != null) {
            boolean isPersonal = Melinoe.getMc().player != null && 
                               gameProfile.name().equals(Melinoe.getMc().player.getGameProfile().name());
            
            if (NoNametagsModule.shouldHideNametag(isPersonal)) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void melinoe$scaleNametag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        PlayerSizeModule.preRenderCallbackScaleHook(state, poseStack);
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