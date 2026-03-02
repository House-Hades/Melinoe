package me.melinoe.events

import com.mojang.blaze3d.platform.InputConstants

class InputEvent(val key: InputConstants.Key) : CancellableEvent()