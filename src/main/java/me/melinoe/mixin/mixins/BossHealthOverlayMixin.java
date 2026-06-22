package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.melinoe.events.BossBarUpdateEvent;
import me.melinoe.events.core.EventBus;
import me.melinoe.features.impl.visual.BossBarScaleModule;
import me.melinoe.utils.BossBarUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Shadow @Final private Map<UUID, LerpingBossEvent> events;

    // Cache previous state - need both progress AND name hash for change detection
    @Unique private final Map<UUID, Float> previousProgress = new HashMap<>();
    @Unique private final Map<UUID, Integer> previousNameHash = new HashMap<>();
    @Unique private int previousSize = 0;

    // Centre
    @Unique private static final float MELINOE_BAR_HALF_WIDTH = 91.0f;
    // Vertical gap between a bar and the title above it
    @Unique private static final int MELINOE_TITLE_GAP = 9;
    @Unique private static final float MELINOE_BAR_SLOT = 19.0f;

    // Boss Bar Scale state, valid for the bar currently being drawn
    @Unique private float melinoe$yShift = 0.0f;
    @Unique private float melinoe$barScale = 1.0f;
    @Unique private int melinoe$bossBarY = 0;

    // Each boss bar is kept at its natural vanilla y and only shrunk in place
    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"
        )
    )
    private void melinoe$scaleBossBar(BossHealthOverlay self, GuiGraphicsExtractor graphics, int x, int y, BossEvent event, Operation<Void> original) {
        if (!BossBarScaleModule.shouldScale(event.getName())) {
            melinoe$barScale = 1.0f;                      // sprite & title wraps pass through
            original.call(self, graphics, x, y, event);   // leave non-boss bars untouched
            return;
        }

        float scale = BossBarScaleModule.getScale();
        melinoe$barScale = scale;
        // Natural y, only pulled up to close gaps left by earlier scaled bars
        melinoe$bossBarY = Math.round(y - melinoe$yShift);
        original.call(self, graphics, x, y, event);

        // Reclaim the slot space this scaled bar saved so the next boss bar hugs below it
        melinoe$yShift += MELINOE_BAR_SLOT * (1.0f - scale);
    }

    // The bar SPRITES are redrawn at scaled INTEGER dimensions
    @WrapOperation(
        method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"
        )
    )
    private void melinoe$scaleBossBarSprite(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int texWidth, int texHeight, int u, int v, int x, int y, int width, int height, Operation<Void> original) {
        float s = melinoe$barScale;
        if (s == 1.0f) {
            original.call(graphics, pipeline, sprite, texWidth, texHeight, u, v, x, y, width, height);
            return;
        }

        int newX = Math.round(x + MELINOE_BAR_HALF_WIDTH * (1.0f - s)); // keep centered
        int newWidth = Math.max(0, Math.round(width * s));
        int newHeight = Math.max(1, Math.round(height * s));
        original.call(graphics, pipeline, sprite, texWidth, texHeight, u, v, newX, melinoe$bossBarY, newWidth, newHeight);
    }

    // Title text is matrix-scaled (text does not tile) and moved up with its bar
    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
    )
    private void melinoe$scaleBossBarTitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Operation<Void> original) {
        float scale = melinoe$barScale;
        if (scale == 1.0f) {
            original.call(graphics, font, text, x, y, color); // leave non-boss titles untouched
            return;
        }

        int barY = melinoe$bossBarY;
        float anchorX = graphics.guiWidth() / 2.0f;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(anchorX, barY);
        pose.scale(scale, scale);
        pose.translate(-anchorX, -barY);
        original.call(graphics, font, text, x, barY - MELINOE_TITLE_GAP, color);
        pose.popMatrix();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRender(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        // Reset per-frame Boss Bar Scale state before the boss-bar loop runs
        melinoe$yShift = 0.0f;
        melinoe$barScale = 1.0f;

        // Always update BossBarUtils
        BossBarUtils.updateBossBarMap(events);

        // Fast path: check size first (most common change - boss spawn/death)
        if (events.size() != previousSize) {
            fireEventAndUpdateCache();
            return;
        }

        // Check for progress or name changes
        for (Map.Entry<UUID, LerpingBossEvent> entry : events.entrySet()) {
            UUID id = entry.getKey();
            LerpingBossEvent bossBar = entry.getValue();

            float currentProgress = bossBar.getProgress();
            int currentNameHash = bossBar.getName().hashCode();

            Float cachedProgress = previousProgress.get(id);
            Integer cachedNameHash = previousNameHash.get(id);

            // New boss, progress changed, or name changed
            if (cachedProgress == null ||
                cachedNameHash == null ||
                Math.abs(currentProgress - cachedProgress) > 0.001f ||
                currentNameHash != cachedNameHash) {
                fireEventAndUpdateCache();
                return;
            }
        }
    }

    @Unique
    private void fireEventAndUpdateCache() {
        // Fire event (create defensive copy to prevent concurrent modification)
        EventBus.post(new BossBarUpdateEvent(new HashMap<>(events)));

        // Update cache
        previousSize = events.size();
        previousProgress.clear();
        previousNameHash.clear();
        for (Map.Entry<UUID, LerpingBossEvent> entry : events.entrySet()) {
            previousProgress.put(entry.getKey(), entry.getValue().getProgress());
            previousNameHash.put(entry.getKey(), entry.getValue().getName().hashCode());
        }
    }
}