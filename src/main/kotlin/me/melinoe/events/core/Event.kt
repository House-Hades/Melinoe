package me.melinoe.events.core

import me.melinoe.utils.logError

/**
 * Base interface for all events in Melinoe v2.
 */
interface Event {

    /**
     * Post this event to EventBus and catch any exceptions.
     * @return false for non-cancellable events, isCancelled for cancellable events
     */
    fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logError(it, this)
        }
        return false
    }
}
