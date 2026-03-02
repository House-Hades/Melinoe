package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.melinoe.features.impl.visual.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin for LightTexture to provide fullbright via ambient light modification.
 * Based on NoFrills' Ambient mode implementation.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    /**
     * Modify ambient light to provide fullbright effect
     * This is the cleanest method with no visual overlay
     */
    @ModifyExpressionValue(
        method = "updateLightTexture",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F")
    )
    private float melinoe$modifyAmbientLight(float original) {
        if (FullbrightModule.INSTANCE.getEnabled()) {
            return 1.0f; // Full ambient light
        }
        return original;
    }
}
