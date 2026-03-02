package me.melinoe.mixin.mixins;

import me.melinoe.events.ChatPacketEvent;
import me.melinoe.events.core.EventBus;
import me.melinoe.utils.ChatManager;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept chat messages and fire ChatPacketEvent.
 */
@Mixin(ChatListener.class)
public class MessageHandlerMixin {

    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
    private void onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            String value = message.getString();
            
            // Post the event first so handlers can mark messages for cancellation
            EventBus.INSTANCE.post(new ChatPacketEvent(value, message));
            
            // Then check if this message should be cancelled
            if (ChatManager.INSTANCE.shouldCancelMessage(message)) {
                ci.cancel();
                return;
            }
        }
    }
}
