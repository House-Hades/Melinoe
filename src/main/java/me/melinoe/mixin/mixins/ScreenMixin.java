package me.melinoe.mixin.mixins;

import me.melinoe.events.GuiEvent;
import me.melinoe.features.impl.misc.KeybindsModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    /**
     * Intercepts chat component clicks before Minecraft asks for permission
     */
    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void melinoe$interceptTpClickEvents(LocalPlayer player, String command, Screen activeScreen, CallbackInfo ci) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;

        if (cmd.startsWith("tp ")) {
            String target = cmd.substring(3).trim();
            if (!target.isEmpty() && KeybindsModule.INSTANCE.handleCalloutTeleport(target)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    protected void onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.DrawBackground(screen, graphics, mouseX, mouseY).postAndCatch()) {
            ci.cancel();
        }
    }
}