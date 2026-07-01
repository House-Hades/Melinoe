package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the single EquipmentAssetManager instance so the Hide Armor reveal can ask whether a
 * derived equipment asset actually exists or not
 */
@Mixin(EquipmentAssetManager.class)
public abstract class EquipmentAssetManagerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void melinoe$captureInstance(CallbackInfo ci) {
        HideArmorModule.setEquipmentAssets((EquipmentAssetManager) (Object) this);
    }
}