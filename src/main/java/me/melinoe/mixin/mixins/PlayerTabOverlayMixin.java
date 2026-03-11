package me.melinoe.mixin.mixins;

import me.melinoe.network.ModWebSocket;
import me.melinoe.utils.ServerUtils;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Unique
    private static final ResourceLocation melinoe$ICON_FONT = ResourceLocation.fromNamespaceAndPath("melinoe", "icons");

    @Unique
    private static final Style melinoe$ICON_STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(melinoe$ICON_FONT))
            .withColor(0xFFFFFF);

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void melinoe$appendIndicator(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (!ServerUtils.INSTANCE.isOnTelos()) return;

        Component currentName = cir.getReturnValue();
        if (currentName == null) return;

        String displayName = currentName.getString();
        if (displayName.isEmpty()) return;

        if (displayName.contains("\uE000")) return;

        for (String modUser : ModWebSocket.INSTANCE.getActiveModUsers()) {
            if (melinoe$isStrictMatch(displayName, modUser)) {

                MutableComponent result = currentName.copy()
                        .append(Component.literal(" "))
                        .append(Component.literal("\uE000").withStyle(melinoe$ICON_STYLE));

                cir.setReturnValue(result);
                return;
            }
        }
    }

    @Unique
    private boolean melinoe$isStrictMatch(String text, String name) {
        String lowerText = text.toLowerCase();
        String lowerName = name.toLowerCase();

        int index = lowerText.indexOf(lowerName);
        while (index != -1) {
            boolean startOk = (index == 0) || !melinoe$isMcNameChar(lowerText.charAt(index - 1));

            boolean endOk = (index + lowerName.length() == lowerText.length()) ||
                    !melinoe$isMcNameChar(lowerText.charAt(index + lowerName.length()));

            if (startOk && endOk) return true;

            index = lowerText.indexOf(lowerName, index + 1);
        }
        return false;
    }

    @Unique
    private boolean melinoe$isMcNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
}