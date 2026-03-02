package me.melinoe.mixin.mixins;

import me.melinoe.utils.data.BagTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundEngine.class)
public class SoundSystemMixin {
    
    @Inject(method = "play", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        ResourceLocation soundId = sound.getLocation();
        
        // Detect bag open sound
        if (soundId.toString().startsWith("noise:player.bags.open")) {
            BagTracker.INSTANCE.handleLootbagOpen();
        }
    }
}
