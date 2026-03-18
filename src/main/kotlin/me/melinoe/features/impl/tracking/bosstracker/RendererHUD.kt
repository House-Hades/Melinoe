package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.utils.Color
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.TelosItemUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.LodestoneTracker
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Renders the boss tracker HUD widget
 */
object RendererHUD {
    
    var widgetColor = Color(0xFF8A0000.toInt())
    var showHud = true
    
    /**
     * Create HUD setting for the boss tracker display
     */
    fun createHUDSetting(module: me.melinoe.features.Module): HUDSetting {
        return HUDSetting(
            name = "Boss Tracker Display",
            x = 10,
            y = 200,
            scale = 1f,
            toggleable = false,
            description = "Position of the boss tracker display",
            module = module
        ) render@{ example ->
            if (!showHud && !example) return@render Pair(0, 0)
            
            if (!ServerUtils.isOnTelos() && !example) return@render Pair(0, 0)
            
            // Check if we should show the tracker (only in realms)
            val level = mc.level
            val dimensionPath = level?.dimension()?.location()?.path
            if (dimensionPath != Constants.DIMENSION_REALM && !example) return@render Pair(0, 0)
            
            val hiddenShadowlandsBosses = listOf("Reaper", "Warden", "Herald")
            
            val aliveBosses = if (example) {
                listOf(
                    BossState.TrackedBoss("Anubis", BlockPos(100, 64, 200), BossState.State.ALIVE, BossData.ANUBIS).apply {
                        distanceMarkerValue = 3.5
                        calledPlayerName = "Player1"
                    },
                    BossState.TrackedBoss("Astaroth", BlockPos(150, 64, 250), BossState.State.DEFEATED_PORTAL_ACTIVE, BossData.ASTAROTH).apply {
                        distanceMarkerValue = 5.2
                        portalTimer = 100
                    }
                )
            } else {
                BossState.getBossesByState(BossState.State.ALIVE).filter { it.name !in hiddenShadowlandsBosses }
            }
            
            val portalBosses = if (example) {
                emptyList()
            } else {
                BossState.getBossesByState(BossState.State.DEFEATED_PORTAL_ACTIVE).filter { it.name != "Raphael" && it.name !in hiddenShadowlandsBosses }
            }
            
            val allActiveBosses = aliveBosses + portalBosses
            
            val raphaelPortalBoss = if (!example) BossState.getBoss("Raphael") else null
            val showRaphael = !example && (allActiveBosses.isNotEmpty() || BossState.raphaelProgress > 0 || raphaelPortalBoss != null)
            val totalBossCount = allActiveBosses.size + (if (showRaphael) 1 else 0)
            
            if (totalBossCount == 0 && !example) return@render Pair(0, 0)
            
            val font = mc.font
            val title = "Realm Bosses"
            val titleComponent = Component.literal(title).withStyle(ChatFormatting.BOLD)
            val titleColor = widgetColor.rgba and 0x00FFFFFF
            val borderColor = 0xFF000000.toInt() or titleColor
            val bgColor = 0xC00C0C0C.toInt()
            
            val r = ((titleColor shr 16) and 0xFF)
            val g = ((titleColor shr 8) and 0xFF)
            val b = (titleColor and 0xFF)
            val lighterR = minOf(255, (r * 1.8).toInt())
            val lighterG = minOf(255, (g * 1.8).toInt())
            val lighterB = minOf(255, (b * 1.8).toInt())
            val labelColor = 0xFF000000.toInt() or (lighterR shl 16) or (lighterG shl 8) or lighterB
            
            val lineSpacing = 16
            val iconSize = 16
            val iconPadding = 2
            val titleWidth = font.width(titleComponent)
            
            val raphaelTextWidth = if (showRaphael) {
                val raphaelTextContent = if (raphaelPortalBoss != null && raphaelPortalBoss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
                    val secondsRemaining = raphaelPortalBoss.portalTimer / 20
                    "Raphael [${BossState.raphaelProgress}/${BossState.raphaelMaxProgress}] (${secondsRemaining}s)"
                } else {
                    "Raphael [${BossState.raphaelProgress}/${BossState.raphaelMaxProgress}]"
                }
                (iconSize + iconPadding) * 2 + font.width(Constants.DISTANCE_MARKER) + 2 + font.width(raphaelTextContent)
            } else {
                0
            }
            
            val maxLabelWidth = if (allActiveBosses.isNotEmpty() || showRaphael) {
                val bossWidths = allActiveBosses.map { boss ->
                    val bossText = buildBossText(boss)
                    val iconsWidth = if (boss.state != BossState.State.DEFEATED) {
                        (iconSize + iconPadding) * 2
                    } else {
                        iconSize + iconPadding
                    }
                    iconsWidth + font.width(bossText)
                }
                maxOf(
                    bossWidths.maxOrNull() ?: 0,
                    raphaelTextWidth,
                    (iconSize + iconPadding) * 2 + font.width("Example Boss")
                )
            } else {
                font.width("No bosses tracked")
            }
            
            val contentWidth = maxLabelWidth
            val boxWidth = maxOf(titleWidth + 16, contentWidth + 12)
            val boxHeight = font.lineHeight + 2 + (maxOf(totalBossCount, 1) * lineSpacing) + 4
            
            // Draw background
            fill(1, 0, boxWidth - 1, boxHeight, bgColor)
            fill(0, 1, 1, boxHeight - 1, bgColor)
            fill(boxWidth - 1, 1, boxWidth, boxHeight - 1, bgColor)
            
            // Draw borders
            val strHeightHalf = font.lineHeight / 2
            val strAreaWidth = titleWidth + 4
            
            fill(2, 1 + strHeightHalf, 6, 2 + strHeightHalf, borderColor)
            fill(2 + strAreaWidth + 4, 1 + strHeightHalf, boxWidth - 2, 2 + strHeightHalf, borderColor)
            fill(2, boxHeight - 2, boxWidth - 2, boxHeight - 1, borderColor)
            fill(1, 2 + strHeightHalf, 2, boxHeight - 2, borderColor)
            fill(boxWidth - 2, 2 + strHeightHalf, boxWidth - 1, boxHeight - 2, borderColor)
            
            // Draw title
            drawString(font, titleComponent, 8, 2, borderColor, false)
            
            // Draw boss lines
            if (allActiveBosses.isNotEmpty()) {
                var yOffset = font.lineHeight + 4
                val leftPadding = 6
                
                for (boss in allActiveBosses) {
                    var xOffset = leftPadding
                    
                    val bossIcon = boss.getBossIcon()
                    renderItem(bossIcon, xOffset, yOffset - 2)
                    xOffset += iconSize + iconPadding
                    
                    if (boss.state != BossState.State.DEFEATED) {
                        val compass = boss.getCompass()
                        renderItem(compass, xOffset, yOffset - 2)
                        xOffset += iconSize + iconPadding
                    }
                    
                    val bossText = buildBossText(boss)
                    drawString(font, bossText, xOffset, yOffset, labelColor, false)
                    
                    yOffset += lineSpacing
                }
                
                if (showRaphael) {
                    var xOffset = leftPadding
                    
                    val raphaelIcon = TelosItemUtils.createItemStack(TelosItemUtils.BOSS_RAPHAEL)
                    renderItem(raphaelIcon, xOffset, yOffset - 2)
                    xOffset += iconSize + iconPadding
                    
                    val raphaelCompass = if (raphaelPortalBoss != null) {
                        raphaelPortalBoss.getCompass()
                    } else {
                        createRaphaelCompass()
                    }
                    renderItem(raphaelCompass, xOffset, yOffset - 2)
                    xOffset += iconSize + iconPadding
                    
                    val player = mc.player
                    val raphaelDistance = if (player != null) {
                        val playerPos = Vec3.atCenterOf(player.blockPosition())
                        val raphaelPos = Vec3.atCenterOf(BlockPos(-15, 243, 88))
                        Math.floor(playerPos.distanceTo(raphaelPos) * 0.008)
                    } else {
                        0.0
                    }
                    
                    val distanceColor = when {
                        raphaelDistance <= 2.0 -> 0x00FF00 // Green
                        raphaelDistance <= 4.0 -> 0xFFFF55 // Bright Yellow
                        raphaelDistance <= 6.0 -> 0xFFAA00 // Orange/Gold
                        raphaelDistance <= 8.0 -> 0xFF3333 // Red
                        else -> 0xAA0000 // Dark Red
                    }
                    val distanceMarker = Component.literal(Constants.DISTANCE_MARKER).withStyle { it.withColor(distanceColor) }
                    drawString(font, distanceMarker, xOffset, yOffset, labelColor, false)
                    xOffset += font.width(Constants.DISTANCE_MARKER) + 2
                    
                    val raphaelText = Component.literal("Raphael ").withStyle { it.withColor(0xFF3333) }
                        .append(Component.literal("[").withStyle { it.withColor(0xAAAAAA) })
                        .append(Component.literal("${BossState.raphaelProgress}").withStyle { it.withColor(
                            if (BossState.raphaelProgress >= BossState.raphaelMaxProgress) 0x00FF00 else 0xFFFF00
                        )})
                        .append(Component.literal("/${BossState.raphaelMaxProgress}").withStyle { it.withColor(0xAAAAAA) })
                        .append(Component.literal("]").withStyle { it.withColor(0xAAAAAA) })
                    
                    if (raphaelPortalBoss != null && raphaelPortalBoss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
                        val secondsRemaining = raphaelPortalBoss.portalTimer / 20
                        raphaelText.append(Component.literal(" (${secondsRemaining}s)").withStyle { it.withColor(0xFFD700) })
                    }
                    
                    drawString(font, raphaelText, xOffset, yOffset, labelColor, false)
                }
            } else {
                val exampleText = "No bosses tracked"
                drawString(font, exampleText, 6, font.lineHeight + 4, 0xFF808080.toInt(), false)
            }
            
            Pair(boxWidth, boxHeight)
        }
    }
    
    /**
     * Build the text component for a boss
     */
    private fun buildBossText(boss: BossState.TrackedBoss): Component {
        val text = Component.empty()
        
        val distanceColor = when {
            boss.distanceMarkerValue <= 2.0 -> 0x00FF00 // Green
            boss.distanceMarkerValue <= 4.0 -> 0xFFFF55 // Bright Yellow
            boss.distanceMarkerValue <= 6.0 -> 0xFFAA00 // Orange/Gold
            boss.distanceMarkerValue <= 8.0 -> 0xFF3333 // Red
            else -> 0xAA0000 // Dark Red
        }
        text.append(Component.literal(Constants.DISTANCE_MARKER).withStyle { it.withColor(distanceColor) })
        text.append(" ")
        
        val textColor = when {
            boss.state == BossState.State.DEFEATED -> 0x555555 // Dark Gray
            boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE -> 0xFFD700 // Gold
            boss.calledPlayerName != null -> 0x00FF00 // Green
            else -> 0xFFFFFF // White
        }
        
        text.append(Component.literal(boss.name).withStyle { it.withColor(textColor) })
        
        if (boss.calledPlayerName != null) {
            val isChatFocused = mc.screen is net.minecraft.client.gui.screens.ChatScreen
            val playerName = if (isChatFocused) {
                boss.calledPlayerName
            } else {
                boss.calledPlayerName!!.substring(0, 3.coerceAtMost(boss.calledPlayerName!!.length))
            }
            text.append(Component.literal(" [$playerName]").withStyle { it.withColor(0x00FF00) })
        }
        
        if (boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
            text.append(Component.literal(" (${boss.portalTimer / 20}s)").withStyle { it.withColor(0xFFD700) })
        }
        
        return text
    }
    
    /**
     * Create compass pointing to Raphael's castle
     */
    private fun createRaphaelCompass(): ItemStack {
        val compass = ItemStack(Items.COMPASS)
        val dimensionKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("telos", "realm")
        )
        val globalPos = GlobalPos(dimensionKey, BlockPos(-15, 243, 88))
        val lodestoneTracker = LodestoneTracker(Optional.of(globalPos), false)
        compass.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker)
        compass.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
        return compass
    }
}