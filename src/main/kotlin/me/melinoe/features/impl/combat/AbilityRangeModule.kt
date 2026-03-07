package me.melinoe.features.impl.combat

import me.melinoe.Melinoe
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.utils.Color
import me.melinoe.utils.ItemUtils
import me.melinoe.utils.render.drawArc
import me.melinoe.utils.render.drawCircle
import me.melinoe.utils.render.drawLine
import me.melinoe.utils.render.drawSquare
import me.melinoe.utils.render.drawThickLine
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Ability Range Module - displays range circles for off-hand abilities.
 */
object AbilityRangeModule : Module(
    name = "Ability Range",
    category = Category.COMBAT,
    description = "Displays range circles for off-hand abilities."
) {

    // Settings
    private val rangeColor by ColorSetting("Color", Color(0x80611784.toInt()), desc = "Color of the range circle")
    private val rangeType = registerSetting(SelectorSetting("Type", "Circle", listOf("Circle", "Arc"), desc = "Shape of the range display"))
    private val showLine by BooleanSetting("Line", false, desc = "Show line from player to range")

    // Cached values to avoid recalculation
    private var cachedRange = -1f
    private var cachedOffset = 0f
    private var cachedItemStack: net.minecraft.world.item.ItemStack? = null
    private var cachedIsHeraldEssence = false

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            
            val player = Melinoe.mc.player ?: return@on
            if (player.isPassenger) return@on

            // Check both hands for an ability
            val mainHandStack = player.mainHandItem
            val offHandStack = player.offhandItem
            
            val stack = when {
                ItemUtils.isAbility(mainHandStack) -> mainHandStack
                ItemUtils.isAbility(offHandStack) -> offHandStack
                else -> {
                    cachedRange = -1f
                    cachedOffset = 0f
                    cachedItemStack = null
                    return@on
                }
            }

            // Only recalculate if item changed
            if (stack != cachedItemStack) {
                val (range, offset) = ItemUtils.getItemRangeWithOffset(stack)
                cachedRange = range
                cachedOffset = offset
                cachedItemStack = stack
                cachedIsHeraldEssence = ItemUtils.isHeraldEssence(stack)
            }
            
            val range = cachedRange
            val offset = cachedOffset

            if (range < 0) return@on

            // Calculate player position with interpolation
            val tickDelta = Melinoe.mc.deltaTracker.getGameTimeDeltaPartialTick(false)
            val playerPos = Vec3(
                Mth.lerp(tickDelta.toDouble(), player.xOld, player.x),
                Mth.lerp(tickDelta.toDouble(), player.yOld, player.y) + 0.01, // Slight offset to prevent z-fighting with weapon range
                Mth.lerp(tickDelta.toDouble(), player.zOld, player.z)
            )

            // Calculate center position with offset applied in the direction player is facing
            val yaw = player.yRot
            val yawRad = Math.toRadians(yaw.toDouble())
            val offsetX = -offset * kotlin.math.sin(yawRad)
            val offsetZ = offset * kotlin.math.cos(yawRad)
            
            val center = playerPos.add(offsetX, 0.0, offsetZ)

            // Skip main range rendering for Herald Essence (only show center square)
            val isHeraldEssence = cachedIsHeraldEssence
            
            if (!isHeraldEssence) {
                // Draw based on range type
                val type = rangeType.selected
                when (type) {
                    "Circle" -> {
                        if (showLine) {
                            // Draw thick line from player to range edge with gradient
                            val lineEnd = center.add(-range * kotlin.math.sin(yawRad), 0.0, range * kotlin.math.cos(yawRad))
                            val transparentColor = Color((rangeColor.rgba and 0x00FFFFFF) or 0x00000000)
                            drawThickLine(playerPos, lineEnd, transparentColor, rangeColor, thickness = 0.125f, depth = true)
                        }
                        drawCircle(center, range, rangeColor, segments = 64, thickness = 1f, depth = true)
                    }
                "Arc" -> {
                    if (showLine) {
                        // Draw thick line from player to range edge with gradient
                        val lineEnd = center.add(-range * kotlin.math.sin(yawRad), 0.0, range * kotlin.math.cos(yawRad))
                        val transparentColor = Color((rangeColor.rgba and 0x00FFFFFF) or 0x00000000)
                        drawThickLine(playerPos, lineEnd, transparentColor, rangeColor, thickness = 0.125f, depth = true)
                    }
                    
                    // Draw arc as triangle strip with fade at edges
                    val segments = 32
                    // Reduced arc angle from 90° to 60° (PI/4 to PI/6)
                    val arcStartAngle = yawRad - Math.PI / 6
                    val arcEndAngle = yawRad + Math.PI / 6
                    
                    val arcPoints = mutableListOf<me.melinoe.utils.render.TriangleStripPoint>()
                    for (i in 0..segments) {
                        val t = i.toDouble() / segments
                        val angle = arcStartAngle + (arcEndAngle - arcStartAngle) * t
                        
                        val x = center.x - range * kotlin.math.sin(angle)
                        val z = center.z + range * kotlin.math.cos(angle)
                        
                        // Calculate fade: only fade at the outer 20% on each edge
                        val distanceFromCenter = kotlin.math.abs(t - 0.5) * 2.0 // 0 at center, 1 at edges
                        val fadeAmount = if (distanceFromCenter > 0.8) {
                            // Fade from 100% to 0% in the last 20% on each side
                            1.0 - ((distanceFromCenter - 0.8) / 0.2)
                        } else {
                            1.0 // Full opacity in the middle 80%
                        }
                        
                        // Apply fade to alpha channel
                        val baseAlpha = (rangeColor.rgba shr 24) and 0xFF
                        val fadedAlpha = (baseAlpha * fadeAmount).toInt()
                        val fadedColor = (rangeColor.rgba and 0x00FFFFFF) or (fadedAlpha shl 24)
                        
                        // Create ribbon by alternating heights
                        arcPoints.add(me.melinoe.utils.render.TriangleStripPoint(Vec3(x, center.y, z), fadedColor))
                        arcPoints.add(me.melinoe.utils.render.TriangleStripPoint(Vec3(x, center.y + 0.2, z), fadedColor))
                    }
                    
                    // Use depth index (0 = with depth, 1 = no depth)
                    consumer.triangleStrips[0].add(me.melinoe.utils.render.TriangleStripData(arcPoints))
                }
            }
            }

            // Draw center square for Herald Essence or center dot for other items with offset
            if (offset != 0f) {
                if (isHeraldEssence) {
                    drawSquare(center, 0.5f, rangeColor, yaw, depth = true)
                } else {
                    drawCircle(center, 0.15f, rangeColor, segments = 8, thickness = 1f, depth = true)
                }
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        cachedRange = -1f
        cachedOffset = 0f
        cachedItemStack = null
        cachedIsHeraldEssence = false
    }
}
