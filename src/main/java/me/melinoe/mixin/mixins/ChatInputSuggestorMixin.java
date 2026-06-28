package me.melinoe.mixin.mixins;

import com.mojang.brigadier.suggestion.Suggestions;
import me.melinoe.utils.emoji.EmojiSuggestionProvider;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Intercepts the game's auto-complete suggestion builder
 */
@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Shadow
    @Nullable
    private CommandSuggestions.SuggestionsList suggestions;

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void melinoe$setEmojiSuggestions(CallbackInfo ci) {
        if (this.input == null) return;

        String text = this.input.getValue();
        if (text == null || text.isEmpty()) return;

        // Bypasses logic entirely if the user is typing standard commands
        if (!EmojiSuggestionProvider.INSTANCE.isTypingEmoji(this.input)) return;

        // Emoji suggestions are computed locally and are always immediately available
        CompletableFuture<Suggestions> emojiSuggestionsFuture = EmojiSuggestionProvider.INSTANCE.provideSuggestions(
                text, this.input.getCursorPosition()
        );

        CompletableFuture<Suggestions> serverFuture = this.pendingSuggestions;

        if (serverFuture != null && serverFuture.isDone()) {
            // Server suggestions are already here
            this.pendingSuggestions = emojiSuggestionsFuture.thenApply(modSugs ->
                    EmojiSuggestionProvider.INSTANCE.mergeAndCheckPerks(serverFuture.join(), modSugs, text)
            );
        } else {
            // Dynamically combine mod suggestions with the server's live suggestion packet and show
            // suggestions in message commands
            this.pendingSuggestions = emojiSuggestionsFuture;

            if (serverFuture != null) {
                serverFuture.thenAccept(serverSugs -> {
                    // Only re-merge if we are still the active suggestion future
                    if (this.pendingSuggestions == emojiSuggestionsFuture) {
                        this.pendingSuggestions = CompletableFuture.completedFuture(
                                EmojiSuggestionProvider.INSTANCE.mergeAndCheckPerks(serverSugs, emojiSuggestionsFuture.join(), text)
                        );
                        this.showSuggestions(false);
                    }
                });
            }
        }

        this.showSuggestions(false);
    }
}