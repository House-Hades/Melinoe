package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.melinoe.features.impl.combat.AutoSprintModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin for LocalPlayer to implement AutoSprint.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"
            )
    )
    private boolean melinoe$autoSprint(boolean original) {
        return original || AutoSprintModule.INSTANCE.getEnabled();
    }
}
