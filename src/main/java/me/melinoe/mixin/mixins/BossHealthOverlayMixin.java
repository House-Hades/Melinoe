package me.melinoe.mixin.mixins;

import me.melinoe.events.BossBarUpdateEvent;
import me.melinoe.events.core.EventBus;
import me.melinoe.utils.BossBarUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    private final Map<UUID, Float> previousProgress = new HashMap<>();
    private final Map<UUID, Integer> previousNameHash = new HashMap<>();
    private int previousSize = 0;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics guiGraphics, CallbackInfo ci) {
        // Always update BossBarUtils (needed for immediate queries like F6 keybind)
        BossBarUtils.INSTANCE.updateBossBarMap(events);
        
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
