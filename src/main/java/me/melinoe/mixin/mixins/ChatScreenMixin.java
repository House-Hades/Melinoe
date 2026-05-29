package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.ChatModule;
import me.melinoe.utils.emoji.EmojiShortcodes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts user inputs while the chat is open
 * Hooks render loops to process live text conversion (Emoji Shortcodes), render custom
 * Chat Tabs, and handles intercepting outgoing messages for the queueing system.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    /**
     * Handles Alt+Arrow hotkeys to swap chat tabs quickly
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void melinoe$onAltArrowKeys(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (ChatModule.INSTANCE.keyPressed(keyEvent.key(), keyEvent.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Intercepts messages to the internal queue if we are currently switching server channels
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void melinoe$queueOutgoingDuringSwitch(String string, boolean bl, CallbackInfo ci) {
        if (ChatModule.INSTANCE.interceptOutgoingMessage(string, bl)) {
            ci.cancel();
        }
    }

    /**
     * Tracks user-sent messages to help bypass server chat cooldown limits
     */
    @Inject(method = "handleChatInput", at = @At("RETURN"))
    private void melinoe$recordMessageSendTime(String string, boolean bl, CallbackInfo ci) {
        if (!ci.isCancelled() && ChatModule.INSTANCE.getEnabled()) {
            ChatModule.INSTANCE.recordNativeMessageSent(string);
        }
    }

    /**
     * Replaces standard shortcodes with actual Emoji icons the instant they are completed.
     * We hook `render` because ChatScreen does not override `tick()`
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void melinoe$processLiveChatInput(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (this.input != null) {
            EmojiShortcodes.INSTANCE.processEditBox(this.input);
        }
    }

    /**
     * Draws the Chat Tab UI above the standard chat input box
     */
    @Inject(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")
    )
    private void melinoe$renderChatTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ChatModule.INSTANCE.renderTabs(graphics, mouseX, mouseY);
    }

    /**
     * Handles clicking the Chat Tabs UI
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void melinoe$onMouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (ChatModule.INSTANCE.mouseClicked(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Safely reverts the unicode Emojis back into server-safe `:shortcodes:` the exact moment the user hits Enter.
     */
    @ModifyVariable(
            method = "handleChatInput",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String melinoe$convertEmojisToShortcodes(String message) {
        return EmojiShortcodes.INSTANCE.replaceEmojiWithShortcodes(message);
    }
}