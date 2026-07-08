package me.melinoe.mixin.mixins;

import me.melinoe.utils.render.CustomBagSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Serves the Custom Bags module's supplied audio to the vanilla sound engine
 */
@Environment(EnvType.CLIENT)
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void onGetStream(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (CustomBagSounds.INSTANCE.isCustomSound(id)) {
            // Decode off-thread
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> CustomBagSounds.INSTANCE.openStream(id), Util.ioPool()));
        }
    }
}