package me.melinoe.events

import net.minecraft.client.gui.components.LerpingBossEvent
import java.util.*

/**
 * Event fired when boss bars are updated.
 * This event is fired from BossHealthOverlayMixin when the boss bar map changes.
 */
class BossBarUpdateEvent(
    val bossBarMap: Map<UUID, LerpingBossEvent>
) : Event
