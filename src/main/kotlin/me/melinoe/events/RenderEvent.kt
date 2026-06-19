package me.melinoe.events

import me.melinoe.events.core.Event
import net.fabricmc.fabric.api.client.rendering.v1.level.AbstractLevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

/**
 * Render events for world rendering.
 */
abstract class RenderEvent(open val context: AbstractLevelRenderContext) : Event {
    class Extract(override val context: LevelExtractionContext, val consumer: me.melinoe.utils.render.RenderConsumer) : RenderEvent(context)
    class Last(override val context: LevelRenderContext) : RenderEvent(context)
}
