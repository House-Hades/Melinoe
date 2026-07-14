package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.melinoe.features.impl.misc.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow
    private float mainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(
        method = "renderArmWithItem",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void applyPositionOffset(
        AbstractClientPlayer player,
        float tickProgress,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector orderedRenderCommandQueue,
        int light,
        CallbackInfo ci
    ) {
        if (!ViewModelModule.INSTANCE.getEnabled()) return;
        if (!ViewModelModule.INSTANCE.getApplyToHand() && item.isEmpty()) return;
        
        double offsetX = ViewModelModule.INSTANCE.getOffsetX();
        double offsetY = ViewModelModule.INSTANCE.getOffsetY();
        double offsetZ = ViewModelModule.INSTANCE.getOffsetZ();
        
        if (hand == InteractionHand.MAIN_HAND) {
            matrices.translate(offsetX, offsetY, offsetZ);
        } else {
            matrices.translate(-offsetX, offsetY, offsetZ);
        }
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    private void applyRotationAndScale(
        AbstractClientPlayer player,
        float tickProgress,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        SubmitNodeCollector orderedRenderCommandQueue,
        int light,
        CallbackInfo ci
    ) {
        if (!ViewModelModule.INSTANCE.getEnabled()) return;
        
        // Apply rotation
        matrices.mulPose(Axis.XP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotX()));
        matrices.mulPose(Axis.YP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotY()));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotZ()));
        
        // Apply scale
        matrices.scale(
            (float) ViewModelModule.INSTANCE.getScaleX(),
            (float) ViewModelModule.INSTANCE.getScaleY(),
            (float) ViewModelModule.INSTANCE.getScaleZ()
        );
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void applyHandPositionOffset(
        PoseStack matrices,
        SubmitNodeCollector queue,
        int light,
        float equipProgress,
        float swingProgress,
        HumanoidArm arm,
        CallbackInfo ci
    ) {
        if (!ViewModelModule.INSTANCE.getEnabled() || !ViewModelModule.INSTANCE.getApplyToHand()) return;
        
        double offsetX = ViewModelModule.INSTANCE.getOffsetX();
        double offsetY = ViewModelModule.INSTANCE.getOffsetY();
        double offsetZ = ViewModelModule.INSTANCE.getOffsetZ();
        
        if (arm == HumanoidArm.RIGHT) {
            matrices.translate(offsetX, offsetY, offsetZ);
        } else {
            matrices.translate(-offsetX, offsetY, offsetZ);
        }
    }

    @Inject(
        method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;")
    )
    private void applyHandRotationAndScale(
        PoseStack matrices,
        SubmitNodeCollector queue,
        int light,
        float equipProgress,
        float swingProgress,
        HumanoidArm arm,
        CallbackInfo ci
    ) {
        if (!ViewModelModule.INSTANCE.getEnabled() || !ViewModelModule.INSTANCE.getApplyToHand()) return;
        
        // Apply rotation
        matrices.mulPose(Axis.XP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotX()));
        matrices.mulPose(Axis.YP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotY()));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) ViewModelModule.INSTANCE.getRotZ()));
        
        // Apply scale
        matrices.scale(
            (float) ViewModelModule.INSTANCE.getScaleX(),
            (float) ViewModelModule.INSTANCE.getScaleY(),
            (float) ViewModelModule.INSTANCE.getScaleZ()
        );
    }

    @Redirect(
        method = "swingArm",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0)
    )
    private void modifySwingTranslation(PoseStack instance, float x, float y, float z) {
        if (ViewModelModule.INSTANCE.getEnabled()) {
            instance.translate(
                x * ViewModelModule.INSTANCE.getSwingX(),
                y * ViewModelModule.INSTANCE.getSwingY(),
                z * ViewModelModule.INSTANCE.getSwingZ()
            );
        } else {
            instance.translate(x, y, z);
        }
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void skipEquipAnimation(ItemStack from, ItemStack _to, CallbackInfoReturnable<Boolean> cir) {
        if (ViewModelModule.INSTANCE.getEnabled() && ViewModelModule.INSTANCE.getNoEquipAnimation()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void forceItemHeight(CallbackInfo ci) {
        if (ViewModelModule.INSTANCE.getEnabled() && ViewModelModule.INSTANCE.getNoEquipAnimation()) {
            this.mainHandHeight = 1.0f;
            this.offHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }
}
