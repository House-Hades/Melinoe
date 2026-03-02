package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.data.DungeonDifficulty
import net.minecraft.network.chat.Component

/**
 * Handles gradient text generation for dungeon completion messages.
 */
object GradientTextBuilder {
    
    /**
     * Gradient color schemes for each difficulty tier.
     * Each array contains 4 RGB color values with darker edges and brighter middle.
     */
    private val gradientSchemes = mapOf(
        DungeonDifficulty.LOWLANDS to intArrayOf(0x6BCB77, 0xC1FBA4, 0xC1FBA4, 0x6BCB77),
        DungeonDifficulty.CENTER to intArrayOf(0x4DA8DA, 0xBDE0FE, 0xBDE0FE, 0x4DA8DA),
        DungeonDifficulty.BOSS to intArrayOf(0x3A0CA3, 0x6E4CBE, 0x6E4CBE, 0x3A0CA3),
        DungeonDifficulty.ENDGAME to intArrayOf(0x581616, 0x9B2226, 0x9B2226, 0x581616)
    )
    
    /**
     * Builds a gradient-colored, bold text component.
     */
    fun buildGradientText(text: String, difficulty: DungeonDifficulty, bold: Boolean = true): Component {
        val colors = gradientSchemes[difficulty] ?: return Component.literal(text)
        val chars = text.toCharArray()
        
        if (chars.isEmpty()) return Component.empty()
        
        val result = Component.empty()
        for (i in chars.indices) {
            // Calculate position in gradient (0.0 to 1.0)
            val position = if (chars.size == 1) 0f else i.toFloat() / (chars.size - 1)
            
            // Find which two colors to interpolate between
            val scaledPosition = position * (colors.size - 1)
            val colorIndex1 = scaledPosition.toInt().coerceIn(0, colors.size - 1)
            val colorIndex2 = (colorIndex1 + 1).coerceIn(0, colors.size - 1)
            
            // Calculate interpolation factor between the two colors
            val factor = scaledPosition - colorIndex1
            
            // Interpolate RGB components
            val color1 = colors[colorIndex1]
            val color2 = colors[colorIndex2]
            
            val r1 = (color1 shr 16) and 0xFF
            val g1 = (color1 shr 8) and 0xFF
            val b1 = color1 and 0xFF
            
            val r2 = (color2 shr 16) and 0xFF
            val g2 = (color2 shr 8) and 0xFF
            val b2 = color2 and 0xFF
            
            val r = (r1 + (r2 - r1) * factor).toInt()
            val g = (g1 + (g2 - g1) * factor).toInt()
            val b = (b1 + (b2 - b1) * factor).toInt()
            
            val interpolatedColor = (r shl 16) or (g shl 8) or b
            
            // Create component for this character with color and optional bold
            val charComponent = Component.literal(chars[i].toString()).withStyle { style ->
                if (bold) {
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(interpolatedColor)).withBold(true)
                } else {
                    style.withColor(net.minecraft.network.chat.TextColor.fromRgb(interpolatedColor))
                }
            }
            result.append(charComponent)
        }
        
        return result
    }
    
    /**
     * Gets the middle (bright) color from the gradient for boss names.
     */
    fun getBrightColor(difficulty: DungeonDifficulty): Int {
        val colors = gradientSchemes[difficulty] ?: return 0xFFFFFF
        return colors[1] // Middle bright color
    }
    
    /**
     * Gets the edge (dark) color from the gradient for completion times.
     */
    fun getDarkColor(difficulty: DungeonDifficulty): Int {
        val colors = gradientSchemes[difficulty] ?: return 0xFFFFFF
        return colors[0] // Edge dark color
    }
}
