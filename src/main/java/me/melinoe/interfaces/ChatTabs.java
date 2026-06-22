package me.melinoe.interfaces;

import me.melinoe.features.impl.misc.ChatTab;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface ChatTabs {
    void melinoe$swapTab(ChatTab newTab);

    /**
     * Resolves the original message component rendered at the given screen coordinates
     */
    @Nullable
    Component melinoe$getMessageAt(double mouseX, double mouseY);
}