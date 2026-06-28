package me.melinoe.interfaces;

import me.melinoe.features.impl.misc.ChatTab;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public interface ChatTabs {
    void melinoe$swapTab(ChatTab newTab);

    /**
     * Resolves the original message component rendered at the given screen coordinates
     */
    @Nullable
    Component melinoe$getMessageAt(double mouseX, double mouseY);

    /**
     * Resolves the text Style sitting under the given screen coordinates, used to read the
     * click event of the specific part of a message that was clicked
     */
    @Nullable
    Style melinoe$getStyleAt(double mouseX, double mouseY);
}