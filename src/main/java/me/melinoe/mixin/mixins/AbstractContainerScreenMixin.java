package me.melinoe.mixin.mixins;

import me.melinoe.events.GuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to fire GuiEvents for container screens (like /bosses menu).
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    protected void onInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.Open(screen).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    protected void onClose(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.Close(screen).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    protected void onRender(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.Draw(screen, context, mouseX, mouseY).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    protected void onRenderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.DrawBackground(screen, context, mouseX, mouseY).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.DrawSlot(screen, guiGraphics, slot).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    public void onMouseClickedSlot(Slot slot, int slotId, int button, ClickType actionType, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.SlotClick(screen, slotId, button).postAndCatch()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void onMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.MouseClick(screen, click, doubled).postAndCatch()) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    public void onMouseReleased(MouseButtonEvent mouseButtonEvent, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.MouseRelease(screen, mouseButtonEvent).postAndCatch()) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.KeyPress(screen, input).postAndCatch()) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    public void onDrawMouseoverTooltip(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (new GuiEvent.DrawTooltip(screen, context, mouseX, mouseY).postAndCatch()) {
            ci.cancel();
        }
    }
}
