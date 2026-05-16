package me.melinoe.mixin.mixins;

import me.melinoe.events.GuiEvent;
import me.melinoe.features.impl.misc.KeybindsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    /**
     * Intercepts chat component clicks before Minecraft validates the command
     */
    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void melinoe$interceptTpClickEvents(ClickEvent event, Minecraft minecraft, Screen activeScreen, CallbackInfo ci) {
        // Check if it's a run command record
        if (event instanceof ClickEvent.RunCommand(String cmd)) {

            // If it's the teleport command, handle it internally and bypass the warning screen
            if (cmd.startsWith("/tp ")) {
                String player = cmd.substring(4).trim();
                if (KeybindsModule.INSTANCE.handleCalloutTeleport(player)) {
                    // Return true to bypass the confirmation screen
                    ci.cancel();
                }
            }
        }
    }
//
//    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
//    protected void onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
//        Screen screen = (Screen) (Object) this;
//        if (new GuiEvent.DrawBackground(screen, graphics, mouseX, mouseY).postAndCatch()) {
//            ci.cancel();
//        }
//    }
}