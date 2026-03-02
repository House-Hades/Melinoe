package me.melinoe.utils

import me.melinoe.events.ChatPacketEvent
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages chat message cancellation.
 */
object ChatManager {
    private val cancelQueue = ConcurrentLinkedQueue<Component>()

    fun ChatPacketEvent.hideMessage() {
        cancelQueue.add(component)
    }

    fun shouldCancelMessage(message: Component): Boolean {
        return cancelQueue.remove(message)
    }
}
