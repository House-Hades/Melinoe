package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.HideArmorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class CustomHeadLayerMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    // Hidden helmet models
    @Unique
    private final List<String> HIDDEN_MODELS = List.of(
            "material/armour/heavy/helmet/ut-mandorla",
            "material/armour/heavy/helmet/ut-crown",
            "material/armour/heavy/helmet/ex-crown",
            "material/armour/magical/helmet/ut-mage",
            "material/armour/magical/helmet/ex-mage",
            "material/armour/light/helmet/ut-sentinel",
            "material/armour/light/helmet/ex-sentinel",
            "material/armour/light/helmet/ut-persistent"
    );

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void melinoe$hideCustomHeadModel(
            T entity,
            S state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (HideArmorModule.INSTANCE.getEnabled()) {
            if (entity.getType() != EntityType.PLAYER) {
                return;
            }

            ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
            if (stack.isEmpty()) return;

            ResourceLocation modelID = stack.get(DataComponents.ITEM_MODEL);

            if (modelID != null && HIDDEN_MODELS.contains(modelID.getPath())) {
                state.headItem.clear();
            }
        }
    }
}