package me.melinoe.utils

import me.melinoe.Melinoe
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.StringUtil

fun playSoundSettings(soundSettings: Triple<String, Float, Float>) {
    val (soundName, volume, pitch) = soundSettings
    val soundEvent = SoundEvent.createVariableRangeEvent(Identifier.parse(StringUtil.filterText(soundName))) ?: return
    playSoundAtPlayer(soundEvent, volume, pitch)
}

fun playSoundAtPlayer(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) = Melinoe.mc.execute {
    Melinoe.mc.soundManager.playDelayed(SimpleSoundInstance.forUI(event, pitch, volume), 0)
}

fun setTitle(title: String) {
    Melinoe.mc.gui.setTimes(0, 20, 5)
    Melinoe.mc.gui.setTitle(Component.literal(title))
}

fun alert(title: String, playSound: Boolean = true) {
    setTitle(title)
    if (playSound) playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value())
}
