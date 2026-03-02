package me.melinoe.mixin.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import me.melinoe.events.InputEvent;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyBindingMixin {

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void onKeyPressed(InputConstants.Key key, CallbackInfo ci) {
        if (new me.melinoe.events.InputEvent(key).postAndCatch()) {
            ci.cancel();
        }
    }
}
