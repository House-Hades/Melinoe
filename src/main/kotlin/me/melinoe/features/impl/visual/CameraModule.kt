package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.Melinoe
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.CameraType

/**
 * Camera Module - disables front camera when enabled.
 */
object CameraModule : Module(
    name = "Camera",
    category = Category.VISUAL,
    description = "Disables front camera when enabled."
) {

    init {
        // Register for client tick events - check enabled state in handler
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (enabled && client.options.cameraType == CameraType.THIRD_PERSON_FRONT) {
                client.options.cameraType = CameraType.FIRST_PERSON
            }
        }
    }
}
