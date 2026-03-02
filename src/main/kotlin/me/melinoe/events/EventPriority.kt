package me.melinoe.events

/**
 * Priority levels for event handling.
 * Higher priority listeners are called first.
 */
enum class EventPriority(val value: Int) {
    LOWEST(-64),
    LOW(-32),
    NORMAL(0),
    HIGH(32),
    HIGHEST(64)
}