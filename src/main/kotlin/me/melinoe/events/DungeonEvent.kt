package me.melinoe.events

import me.melinoe.utils.data.DungeonData

/**
 * Base class for dungeon-related events.
 */
sealed class DungeonEvent(val dungeon: DungeonData) : Event

/**
 * Event fired when the player enters a dungeon.
 */
class DungeonEntryEvent(dungeon: DungeonData) : DungeonEvent(dungeon)

/**
 * Event fired when the player exits a dungeon.
 */
class DungeonExitEvent(dungeon: DungeonData) : DungeonEvent(dungeon)

/**
 * Event fired when the player changes from one dungeon to another (dungeon chains).
 */
class DungeonChangeEvent(
    val previousDungeon: DungeonData,
    val newDungeon: DungeonData
) : DungeonEvent(newDungeon)
