package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.ChatModule;
import me.melinoe.features.impl.misc.ChatTab;
import me.melinoe.features.impl.misc.KeybindsModule;
import me.melinoe.interfaces.ChatTabs;
import me.melinoe.utils.emoji.EmojiReplacer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hooks into the Chat HUD to implement the visual Chat Tabs, route incoming messages
 * to their correct tabs, and extend the maximum message limit
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatTabs {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;
    @Shadow public abstract int getWidth();
    @Shadow public abstract double getScale();
    @Shadow protected abstract boolean isChatFocused();
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract void scrollChat(int pos);

    // Stores the individual chat history for every single tab
    @Unique
    private final Map<ChatTab, List<GuiMessage.Line>> melinoe$tabDisplayQueues = new java.util.HashMap<>();

    // Tracks if the chat is currently resizing so we don't accidentally save duplicate messages
    @Unique
    private boolean melinoe$isRescaling = false;

    /**
     * Gets the message history list for a specific tab
     */
    @Unique
    private List<GuiMessage.Line> melinoe$getQueue(ChatTab tab) {
        return melinoe$tabDisplayQueues.computeIfAbsent(tab, k -> new ArrayList<>());
    }

    /**
     * Replaces the currently visible chat messages with the history of the selected tab
     */
    @Override
    public void melinoe$swapTab(ChatTab newTab) {
        this.trimmedMessages.clear();
        this.trimmedMessages.addAll(melinoe$getQueue(newTab));

        // Reset scroll position so the user is looking at the newest messages
        this.chatScrollbarPos = 0;
        this.newMessageSinceScroll = false;
    }

    /**
     * Triggered when the user resizes the game window or changes chat scale
     * Clear the queues and enable a flag to block transient messages from saving
     */
    @Inject(method = "rescaleChat", at = @At("HEAD"))
    private void melinoe$onRescaleStart(CallbackInfo ci) {
        this.melinoe$tabDisplayQueues.clear();
        this.melinoe$isRescaling = true; // Block transient messages from re-rendering
    }

    @Inject(method = "rescaleChat", at = @At("RETURN"))
    private void melinoe$onRescaleEnd(CallbackInfo ci) {
        this.melinoe$isRescaling = false; // Restore state
    }

    /**
     * Intercepts messages before they are added to the screen so we can route them
     * to their specific tab or hide them
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void melinoe$customAddMessageToQueue(GuiMessage message, CallbackInfo ci) {
        if (!ChatModule.INSTANCE.getEnabled() || !ChatModule.INSTANCE.isChatTabsEnabled()) return;

        // Process the message to figure out its tab category and if it should be censored
        ChatModule.ProcessedMessage processed = ChatModule.INSTANCE.getProcessedMessage(message.content());
        ChatTab messageTab = processed.getCategory();
        boolean isTransient = processed.isTransient();

        // Block useless utility messages entirely if the user enabled that setting
        if (messageTab == ChatTab.UTILITY && ChatModule.INSTANCE.isHideUtilityMessages()) {
            ci.cancel();
            return;
        }

        Component contentToWrap = processed.getCensoredContent();

        // Calculate the maximum width of the chat box based on the user's GUI scale settings
        int width = Mth.floor((double) this.getWidth() / this.getScale());

        // Break the long message into multiple lines so it fits on the screen
        List<FormattedCharSequence> wrappedLines = ComponentRenderUtils.wrapComponents(contentToWrap, width, this.minecraft.font);

        boolean chatFocused = this.isChatFocused();
        ChatTab activeTab = ChatModule.INSTANCE.getActiveTab();

        // Convert the wrapped text into Minecraft's native format
        List<GuiMessage.Line> newLines = new ArrayList<>();
        for (int i = 0; i < wrappedLines.size(); i++) {
            boolean isLast = (i == wrappedLines.size() - 1);
            newLines.add(new GuiMessage.Line(message.addedTime(), wrappedLines.get(i), message.tag(), isLast));
        }

        // Add to main history (ALL tab) as permanent log
        melinoe$addToTabQueue(ChatTab.ALL, newLines, activeTab == ChatTab.ALL, chatFocused, false);

        if (isTransient) {
            // Transient messages are temporary (like error warnings)
            // Force display on the currently active tab but never save them to the background history
            if (activeTab != ChatTab.ALL && !this.melinoe$isRescaling) {
                melinoe$addToTabQueue(activeTab, newLines, true, chatFocused, true);
            }
        } else {
            // Check if it's a Callout, which requires rendering on both Chat and Callouts tabs
            if (messageTab == ChatTab.CALLOUTS) {
                melinoe$addToTabQueue(ChatTab.CALLOUTS, newLines, activeTab == ChatTab.CALLOUTS, chatFocused, false);
                melinoe$addToTabQueue(ChatTab.CHAT, newLines, activeTab == ChatTab.CHAT, chatFocused, false);
            } else if (messageTab != ChatTab.ALL) {
                // Standard categorized message queue
                melinoe$addToTabQueue(messageTab, newLines, activeTab == messageTab, chatFocused, false);
            }
        }

        // Cancel vanilla method so we don't render the message twice
        ci.cancel();
    }

    /**
     * Pushes a message into a specific chat tab's history
     */
    @Unique
    private void melinoe$addToTabQueue(ChatTab tab, List<GuiMessage.Line> newLines, boolean isActive, boolean chatFocused, boolean isTransient) {
        List<GuiMessage.Line> queue = melinoe$getQueue(tab);

        for (GuiMessage.Line line : newLines) {
            // Only add to background persistent list if it is not a transient message
            if (!isTransient) {
                queue.addFirst(line);
            }

            // If the user is currently looking at this tab, show it on the screen immediately
            if (isActive) {
                this.trimmedMessages.addFirst(line);

                // If they are scrolled up to read old messages, push them down slightly so they don't lose their place
                if (chatFocused && this.chatScrollbarPos > 0) {
                    this.newMessageSinceScroll = true;
                    this.scrollChat(1);
                }
            }
        }

        // Clean up memory by dropping lines older than our custom max limit
        if (!isTransient) {
            while (queue.size() > 16384) {
                queue.removeLast();
            }
        }

        if (isActive) {
            while (this.trimmedMessages.size() > 16384) {
                this.trimmedMessages.removeLast();
            }
        }
    }

    /**
     * Replaces Minecraft's hardcoded 100-message limit 16,384
     */
    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue",
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"
            },
            constant = @Constant(intValue = 100)
    )
    private int melinoe$extendChatLimit(int defaultLimit) {
        return 16384;
    }

    /**
     * Ticks our ChatModule logic before rendering chat elements
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void melinoe$checkChatRefresh(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ChatModule.INSTANCE.onRender(tickCount);
    }

    /**
     * Intercepts incoming messages to catch chat mode switches
     */
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void melinoe$catchServerChatModeSwitch(Component message, CallbackInfo ci) {
        if (ChatModule.INSTANCE.handleChatModeMessage(message.getString())) {
            ci.cancel(); // Hide the raw server notification if we successfully handled it internally
        }
    }

    /**
     * Processes emojis and injects unconditionally the click-to-teleport logic to the component immediately.
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component melinoe$processIncomingMessage(Component message) {
        Component withEmojis = EmojiReplacer.INSTANCE.replaceIn(message);
        return KeybindsModule.INSTANCE.applyCalloutClickEvent(withEmojis);
    }
}