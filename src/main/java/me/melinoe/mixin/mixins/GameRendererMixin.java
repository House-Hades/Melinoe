package me.melinoe.mixin.mixins;

import me.melinoe.Melinoe;
import me.melinoe.utils.data.BagTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Detects bag drops by intercepting the totem animation.
 * In Telos, bags use the totem animation when they drop.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "displayItemActivation", at = @At("HEAD"))
    private void onDisplayItemActivation(ItemStack floatingItem, CallbackInfo ci) {
        if (!floatingItem.has(DataComponents.ITEM_MODEL)) return;

        var cmd = Objects.requireNonNull(floatingItem.get(DataComponents.ITEM_MODEL));
        String path = cmd.getPath();
            
        Melinoe.INSTANCE.getLogger().info("Totem animation: {} (path: {})", 
            floatingItem.getItem().getDescriptionId(), path);
            
        // Match bag types and trigger handlers
        // Note: path is just the path part, not including namespace (e.g., "entity/pouch/bloodshot_totem")
        switch (path) {
            case "entity/pouch/royal_totem":
                BagTracker.INSTANCE.onRoyalBagDrop();
                break;
                    
            case "entity/pouch/bloodshot_totem":
                BagTracker.INSTANCE.onBloodshotBagDrop();
                break;
                    
            case "entity/pouch/companion":
                BagTracker.INSTANCE.onCompanionBagDrop();
                break;
                    
            case "entity/pouch/unholy_totem":
                BagTracker.INSTANCE.onUnholyBagDrop();
                break;
                    
            case "entity/pouch/halloween_totem":
            case "entity/pouch/valentine_totem":
            case "entity/pouch/christmas_totem":
                BagTracker.INSTANCE.onEventBagDrop();
                break;
                    
            case "entity/pouch/voidbound_totem":
                BagTracker.INSTANCE.onVoidboundBagDrop();
                break;
                    
            default:
                if (path.contains("pouch")) {
                    Melinoe.INSTANCE.getLogger().warn("Unknown bag type: {}", path);
                }
                break;
        }
    }
}
