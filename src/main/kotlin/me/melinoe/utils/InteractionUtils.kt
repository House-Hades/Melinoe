package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.mixin.accessors.ServerboundInteractPacketAccessor
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.entity.Entity

/**
 * Utility functions for tracking entity interactions.
 * Uses ServerboundInteractPacketAccessor to get entity IDs from interaction packets.
 */
object InteractionUtils {
    
    /**
     * Get the entity ID from a ServerboundInteractPacket.
     * This is the entity the player is interacting with (right-clicking).
     */
    fun getEntityIdFromPacket(packet: ServerboundInteractPacket): Int {
        return (packet as ServerboundInteractPacketAccessor).entityId
    }
    
    /**
     * Get the actual Entity object from a ServerboundInteractPacket.
     * Returns null if the entity is not loaded in the world.
     */
    fun getEntityFromPacket(packet: ServerboundInteractPacket): Entity? {
        val entityId = getEntityIdFromPacket(packet)
        return Melinoe.mc.level?.getEntity(entityId)
    }
    
    /**
     * Check if a packet is interacting with a specific entity.
     */
    fun isInteractingWith(packet: ServerboundInteractPacket, entity: Entity): Boolean {
        return getEntityIdFromPacket(packet) == entity.id
    }
    
    /**
     * Check if a packet is interacting with an entity of a specific type.
     */
    inline fun <reified T : Entity> isInteractingWithType(packet: ServerboundInteractPacket): Boolean {
        val entity = getEntityFromPacket(packet)
        return entity is T
    }
}
