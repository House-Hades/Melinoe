package me.melinoe.events

import net.minecraft.client.multiplayer.ClientLevel

abstract class TickEvent : Event {
    class Start(val world: ClientLevel) : TickEvent()
    class End(val world: ClientLevel) : TickEvent()
    
    /**
     * Server tick event - fired on ping packets.
     */
    object Server : TickEvent()
}
