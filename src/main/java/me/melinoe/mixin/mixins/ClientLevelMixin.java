package me.melinoe.mixin.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to block the totem use sound (item.totem.use).
 * This prevents the loud totem sound from playing when bag drops are detected.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    /**
     * Block the totem use sound from playing.
     * This is injected at the HEAD of the playSeededSound method to cancel it before it plays.
     */
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void melinoe$blockTotemSound(
            Entity entity,
            Entity sourceEntity,
            Holder<SoundEvent> sound,
            SoundSource source,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        // Check if this is the totem use sound
        if (sound.value() == SoundEvents.TOTEM_USE) {
            // Cancel the sound from playing
            ci.cancel();
        }
    }

    /**
     * Block the totem use sound from playing (overload for direct position sounds).
     * This is injected at the HEAD of the playSound method to cancel it before it plays.
     */
    @Inject(
            method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void melinoe$blockTotemSoundDirect(
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch,
            boolean distanceDelay,
            long seed,
            CallbackInfo ci
    ) {
        // Check if this is the totem use sound
        if (sound == SoundEvents.TOTEM_USE) {
            // Cancel the sound from playing
            ci.cancel();
        }
    }
}
