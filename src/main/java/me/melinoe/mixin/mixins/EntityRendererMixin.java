package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import me.melinoe.features.impl.visual.PlayerSizeModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void melinoe$extractNametag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!PlayerSizeModule.INSTANCE.getEnabled()) return;
        if (entity instanceof Display.TextDisplay textDisplay) {
            boolean isNametag = PlayerSizeModule.getNametag(textDisplay);
            state.setData(PlayerSizeModule.getREALM_NAMETAG_KEY(), isNametag);

            if (isNametag) {
                boolean isPersonal = PlayerSizeModule.isPersonalNametag(entity);
                state.setData(PlayerSizeModule.getIS_PERSONAL_KEY(), isPersonal);
            }
        }
    }

    @Inject(
            method = "submit",
            at = @At("HEAD")
    )
    private void melinoe$hideOrScaleNametag(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        Boolean isNametag = state.getData(PlayerSizeModule.getREALM_NAMETAG_KEY());

        if (Boolean.TRUE.equals(isNametag)) {
            Boolean isPersonal = state.getData(PlayerSizeModule.getIS_PERSONAL_KEY());
            
            // Apply PlayerSize scaling
            PlayerSizeModule.textDisplayScaleHook(true, isPersonal, poseStack);
        }
    }
}