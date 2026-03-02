package me.melinoe.events

import me.melinoe.Melinoe
import me.melinoe.utils.render.RenderBatchManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents

/**
 * Bridges Fabric lifecycle events into the melinoe EventBus.
 */
object EventDispatcher {

    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            WorldLoadEvent().postAndCatch()
        }

        ClientTickEvents.START_WORLD_TICK.register { world ->
            TickEvent.Start(world).postAndCatch()
        }

        ClientTickEvents.END_WORLD_TICK.register { world ->
            TickEvent.End(world).postAndCatch()
        }

        WorldRenderEvents.END_EXTRACTION.register { handler ->
            Melinoe.mc.level?.let { RenderEvent.Extract(handler, RenderBatchManager.renderConsumer).postAndCatch() }
        }

        WorldRenderEvents.END_MAIN.register { context ->
            Melinoe.mc.level?.let { RenderEvent.Last(context).postAndCatch() }
        }
        
        // Note: ChatPacketEvent is fired by MessageHandlerMixin
        // Note: Chat message filtering is handled by MessageHandlerMixin
    }
}

