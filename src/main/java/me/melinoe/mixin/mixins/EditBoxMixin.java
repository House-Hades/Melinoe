package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.melinoe.utils.emoji.EmojiShortcodes;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EditBox.class)
public abstract class EditBoxMixin {

    @Shadow public abstract String getValue();
    @Shadow public abstract void setValue(String string);
    @Shadow public abstract int getCursorPosition();
    @Shadow public abstract void setCursorPosition(int i);
    @Shadow public abstract void setHighlightPos(int i);

    /**
     * Forces emoji glyphs to always render at full white, regardless of the text color the active
     * formatter applies
     */
    @ModifyReturnValue(method = "applyFormat", at = @At("RETURN"))
    private FormattedCharSequence melinoe$whitenEmojiGlyphs(FormattedCharSequence original) {
        return sink -> original.accept((position, style, codepoint) -> {
            // Mod emojis and server emojis
            if ((codepoint >= 0xF400 && codepoint <= 0xF4FF) || (codepoint >= 0x1525E && codepoint <= 0x152AB)) {
                return sink.accept(position, style.withColor(0xFFFFFF), codepoint);
            }
            return sink.accept(position, style, codepoint);
        });
    }

    /**
     * Pressing backspace reverts the unicode Emoji back into `:shortcode` minus colon.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void melinoe$onBackspaceEmoji(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        // 259 corresponds to GLFW_KEY_BACKSPACE
        if (keyEvent.key() == 259) {
            String text = this.getValue();
            int cursor = this.getCursorPosition();

            if (cursor > 0) {
                String emojiBefore = EmojiShortcodes.INSTANCE.getEmojiBeforeCursor(text, cursor);
                if (emojiBefore != null) {
                    String shortcode = EmojiShortcodes.INSTANCE.getReverseMappings().get(emojiBefore);

                    if (shortcode != null) {
                        cir.setReturnValue(true); // Cancel standard char deletion

                        // Remove trailing ':'
                        String strippedShortcode = shortcode.substring(0, shortcode.length() - 1);

                        // Inject the incomplete shortcode cleanly to pop open suggestions natively
                        String newText = text.substring(0, cursor - emojiBefore.length()) + strippedShortcode + text.substring(cursor);
                        this.setValue(newText);

                        int newCursor = cursor - emojiBefore.length() + strippedShortcode.length();
                        this.setCursorPosition(newCursor);
                        this.setHighlightPos(newCursor);
                    }
                }
            }
        }
    }
}