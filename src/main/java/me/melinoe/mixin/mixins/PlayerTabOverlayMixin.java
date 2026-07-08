package me.melinoe.mixin.mixins;

import me.melinoe.network.ModWebSocket;
import me.melinoe.utils.ServerUtils;
import me.melinoe.utils.TabListUtils;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Unique
    private static final Identifier melinoe$ICON_FONT = Identifier.fromNamespaceAndPath("melinoe", "icons");

    @Unique
    private static final Style melinoe$ICON_STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(melinoe$ICON_FONT))
            .withColor(0xFFFFFF);

    @Unique
    private static final List<TabListUtils.StatDefinition> melinoe$STAT_DEFINITIONS =
            TabListUtils.INSTANCE.getSTAT_DEFINITIONS();

    @Unique
    private static final List<Pattern> melinoe$DISPLAY_PATTERNS = melinoe$STAT_DEFINITIONS.stream()
            .map(def -> Pattern.compile(
                    "(" + Pattern.quote(def.getLabel()) + ":\\s*)(\\+" + def.getValuePattern() + ")"))
            .toList();

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void melinoe$appendIndicator(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (!ServerUtils.INSTANCE.isOnTelos()) return;

        Component currentName = cir.getReturnValue();
        if (currentName == null) return;

        String displayName = currentName.getString();
        if (displayName.isEmpty()) return;

        Component nameWithFormulas = melinoe$injectStatFormulas(currentName, displayName);

        if (nameWithFormulas.getString().contains("\uE000")) {
            cir.setReturnValue(nameWithFormulas);
            return;
        }

        for (String modUser : ModWebSocket.INSTANCE.getActiveModUsers()) {
            if (melinoe$isStrictMatch(displayName, modUser)) {
                MutableComponent result = nameWithFormulas.copy()
                        .append(Component.literal(" "))
                        .append(Component.literal("\uE000").withStyle(melinoe$ICON_STYLE));

                cir.setReturnValue(result);
                return;
            }
        }

        if (!nameWithFormulas.equals(currentName)) {
            cir.setReturnValue(nameWithFormulas);
        }
    }

    @Unique
    private Component melinoe$injectStatFormulas(Component original, String displayName) {
        Map<String, Double> stats = TabListUtils.INSTANCE.getStatValues();
        if (stats.isEmpty()) {
            return original;
        }

        for (int i = 0; i < melinoe$STAT_DEFINITIONS.size(); i++) {
            Matcher matcher = melinoe$DISPLAY_PATTERNS.get(i).matcher(displayName);
            if (!matcher.find()) continue;

            String key = melinoe$STAT_DEFINITIONS.get(i).getKey();
            Double value = stats.get(key);
            if (value == null) continue;

            String formula = TabListUtils.INSTANCE.formatStatValue(key, value);
            return melinoe$buildStyledComponent(original, matcher, formula);
        }

        return original;
    }

    @Unique
    private Component melinoe$buildStyledComponent(Component original, Matcher matcher, String formula) {
        String prefix = matcher.group(1);
        String value = matcher.group(2);

        // prefix + white value + gray formula
        return Component.literal(prefix)
                .withStyle(original.getStyle())
                .append(Component.literal(value).withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                .append(Component.literal(" " + formula).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
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