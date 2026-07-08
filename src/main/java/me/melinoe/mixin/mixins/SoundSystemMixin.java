package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.visual.CustomBagsModule;
import me.melinoe.utils.data.BagTracker;
import me.melinoe.utils.render.CustomBagSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundEngine.class)
public class SoundSystemMixin {

    private static final String CREATE_PREFIX = "noise:player.bags.create_";

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        String soundId = sound.getIdentifier().toString();

        // Detect bag open sound (starts item scanning)
        if (soundId.startsWith("noise:player.bags.open")) {
            BagTracker.INSTANCE.handleLootbagOpen();
            return;
        }

        // Replace a totem's create sound with the user's custom one (when configured)
        if (soundId.startsWith(CREATE_PREFIX) && CustomBagsModule.INSTANCE.getEnabled()) {
            String friendly = CustomBagSounds.INSTANCE.friendlyFromCreateSuffix(soundId.substring(CREATE_PREFIX.length()));
            if (friendly != null && CustomBagSounds.INSTANCE.hasSound(friendly)) {
                CustomBagSounds.INSTANCE.playCreate(friendly, CustomBagsModule.INSTANCE.soundVolumeFor(friendly));
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED); // suppress the vanilla sound
            }
        }
    }
}
