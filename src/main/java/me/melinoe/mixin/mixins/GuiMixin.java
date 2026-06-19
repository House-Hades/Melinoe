package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.HideHeldTooltipsModule;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept and cancel the rendering of held item tooltips
 */
@Mixin(Gui.class)
public class GuiMixin {
    
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void onRenderSelectedItemName(CallbackInfo ci) {
        if (HideHeldTooltipsModule.INSTANCE.getEnabled()) {
            ci.cancel();
        }
    }
}
