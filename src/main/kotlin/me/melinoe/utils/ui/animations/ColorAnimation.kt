package me.melinoe.utils.ui.animations

import me.melinoe.utils.Color

/**
 * Color animation for smooth color transitions.
 */
class ColorAnimation(duration: Long) {

    private val anim = LinearAnimation<Int>(duration)

    fun start() = anim.start()

    fun isAnimating(): Boolean = anim.isAnimating()

    fun percent(): Float = anim.getPercent()

    fun get(start: Color, end: Color, reverse: Boolean): Color =
        Color(
            anim.get(start.red, end.red, reverse),
            anim.get(start.green, end.green, reverse),
            anim.get(start.blue, end.blue, reverse),
            anim.get(start.alpha, end.alpha, reverse) / 255f,
        )
}
