package me.melinoe.events

import me.melinoe.events.core.CancellableEvent as CoreCancellableEvent

/**
 * Base class for events that can be cancelled.
 * This is a type alias/wrapper for the core CancellableEvent class.
 */
typealias CancellableEvent = CoreCancellableEvent
