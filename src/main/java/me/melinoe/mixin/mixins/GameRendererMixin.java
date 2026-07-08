package me.melinoe.mixin.mixins;

import me.melinoe.Melinoe;
import me.melinoe.features.impl.visual.CustomBagsModule;
import me.melinoe.utils.data.BagTracker;
import me.melinoe.utils.render.CustomBagImages;
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

        // Swap the totem to our custom screen + image (only when the configured)
        if (CustomBagsModule.INSTANCE.getEnabled()) {
            CustomBagImages.INSTANCE.applyFor(floatingItem, path);
        }

        // A preview pop from the Custom Bags module must not count as a real drop
        if (CustomBagsModule.INSTANCE.getPreviewing()) return;

        // Match bag types and trigger handlers
        switch (path) {
            case "mob/pouch/royal_totem":
                BagTracker.INSTANCE.onRoyalBagDrop();
                break;
                    
            case "mob/pouch/bloodshot_totem":
                BagTracker.INSTANCE.onBloodshotBagDrop();
                break;
                    
            case "mob/pouch/companion":
                BagTracker.INSTANCE.onCompanionBagDrop();
                break;
                    
            case "mob/pouch/unholy_totem":
                BagTracker.INSTANCE.onUnholyBagDrop();
                break;
                    
            case "mob/pouch/halloween_totem":
            case "mob/pouch/valentine_totem":
            case "mob/pouch/christmas_totem":
                BagTracker.INSTANCE.onEventBagDrop();
                break;
                    
            case "mob/pouch/voidbound_totem":
                BagTracker.INSTANCE.onVoidboundBagDrop();
                break;
                    
            case "mob/pouch/shiny_totem":
                BagTracker.INSTANCE.onShinyBagDrop();
                break;
                    
            default:
                if (path.contains("pouch")) {
                    Melinoe.INSTANCE.getLogger().warn("Unknown bag type: {}", path);
                }
                break;
        }
    }
}
