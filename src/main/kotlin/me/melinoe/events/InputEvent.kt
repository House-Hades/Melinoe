package me.melinoe.events

import com.mojang.blaze3d.platform.InputConstants

class InputEvent(val key: InputConstants.Key) : CancellableEvent()

/**
 * Fired when a key or mouse button is released
 */
class InputReleaseEvent(val key: InputConstants.Key) : Event