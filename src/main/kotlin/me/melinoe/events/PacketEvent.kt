package me.melinoe.events

import me.melinoe.events.core.CancellableEvent
import net.minecraft.network.protocol.Packet

/**
 * Event fired when packets are sent or received.
 * Allows for packet interception and cancellation.
 */
abstract class PacketEvent(val packet: Packet<*>) : CancellableEvent() {

    /**
     * Event fired when a packet is received from the server.
     */
    class Receive(packet: Packet<*>) : PacketEvent(packet)

    /**
     * Event fired when a packet is about to be sent to the server.
     */
    class Send(packet: Packet<*>) : PacketEvent(packet)
}
