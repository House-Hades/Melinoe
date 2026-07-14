package me.melinoe.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.melinoe.Melinoe;
import me.melinoe.features.impl.misc.ViewModelModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    private boolean isSelf() {
        return (Object) this == Melinoe.INSTANCE.getMc().player;
    }

    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int modifySwingSpeed(int original) {
        if (ViewModelModule.INSTANCE.getEnabled() && isSelf()) {
            // Swing speed
            if (ViewModelModule.INSTANCE.getSpeed() > 0) {
                return ViewModelModule.INSTANCE.getSpeed();
            }
        }
        return original;
    }
}
