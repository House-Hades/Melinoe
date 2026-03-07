package me.melinoe.features.impl.visual

import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.CameraType

/**
 * Camera Module - manages camera perspective and front camera disabling.
 */
object CameraModule : Module(
    name = "Camera",
    category = Category.VISUAL,
    description = "Manages camera perspective and front camera disabling."
) {
    
    private val autoPerspective by BooleanSetting(name = "Auto Perspective", default = false, desc = "Automatically changes perspective based on player pitch")
    private val pitchThreshold by NumberSetting(name = "Pitch Threshold", default = 15, min = 0, max = 90, desc = "Pitch angle required to trigger perspective switch")
    
    private var inverted = false
    
    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!enabled) return@register
            
            val player = client.player ?: return@register
            val options = client.options
            
            // Ran when the toggle perspective keybind is pressed
            while (options.keyTogglePerspective.consumeClick()) {
                if (autoPerspective) {
                    // Invert if auto perspective is on
                    inverted = !inverted
                } else {
                    // Otherwise simply switch between first & third person
                    options.cameraType = if (options.cameraType == CameraType.THIRD_PERSON_BACK) {
                        CameraType.FIRST_PERSON
                    } else {
                        CameraType.THIRD_PERSON_BACK
                    }
                }
            }
            
            if (autoPerspective) {
                val shouldBeThirdPerson = inverted != (player.xRot > pitchThreshold.toDouble())
                
                val targetType = if (shouldBeThirdPerson) CameraType.THIRD_PERSON_BACK else CameraType.FIRST_PERSON
                
                if (options.cameraType != targetType) {
                    options.cameraType = targetType
                }
            } else {
                // Failsafe
                if (options.cameraType == CameraType.THIRD_PERSON_FRONT) {
                    options.cameraType = CameraType.FIRST_PERSON
                }
            }
        }
    }
}