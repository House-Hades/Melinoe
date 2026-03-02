package me.melinoe.mixin.accessors;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for ServerboundInteractPacket to get the entity ID being interacted with.
 * Useful for tracking entity interactions (right-click on entities).
 */
@Mixin(ServerboundInteractPacket.class)
public interface ServerboundInteractPacketAccessor {
    
    @Accessor("entityId")
    int getEntityId();
}
