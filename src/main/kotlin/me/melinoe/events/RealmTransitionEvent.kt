package me.melinoe.events

/**
 * Base class for realm transition events.
 */
sealed class RealmTransitionEvent(val previousWorld: String, val newWorld: String) : Event

/**
 * Event fired when the player enters a realm from a hub or upon logging in.
 */
class HubToRealmEvent(previousWorld: String, newWorld: String) : RealmTransitionEvent(previousWorld, newWorld)

/**
 * Event fired when the player returns to a hub from a realm.
 */
class RealmToHubEvent(previousWorld: String, newWorld: String) : RealmTransitionEvent(previousWorld, newWorld)

/**
 * Event fired when the player changes from one realm to another.
 */
class RealmToRealmEvent(previousWorld: String, newWorld: String) : RealmTransitionEvent(previousWorld, newWorld)