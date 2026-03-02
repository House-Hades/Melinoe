package me.melinoe.events

/**
 * Keybind event for handling key presses.
 */
data class KeybindEvent(
    val key: Int,
    val action: Int, // GLFW_PRESS, GLFW_RELEASE, GLFW_REPEAT
    val modifiers: Int
) : Event {
    val pressed: Boolean get() = action == 1 // GLFW_PRESS
    val released: Boolean get() = action == 0 // GLFW_RELEASE
    val repeat: Boolean get() = action == 2 // GLFW_REPEAT
}
