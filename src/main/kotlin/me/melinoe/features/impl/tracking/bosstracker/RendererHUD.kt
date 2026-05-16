package me.melinoe.features.impl.tracking.bosstracker

import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.utils.Color
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.TelosItemUtils
import me.melinoe.utils.toNative
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.LodestoneTracker
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.floor

/**
 * Renders the boss tracker HUD widget
 */
object RendererHUD {
    
    var widgetColor = Color(0xFF8A0000.toInt())
    var showHud = true
    
    // Cache Vars
    private var lastUpdateMs = 0L
    private var lastChatFocused = false
    private var lastExample = false
    
    private var cachedBoxWidth = 0
    private var cachedBoxHeight = 0
    private var cachedBossLines = mutableListOf<CachedBossLine>()
    
    // Pre-parsed static components
    private val titleComponent by lazy { "<bold>Realm Bosses</bold>".toNative() }
    private val emptyTrackerComponent by lazy { "<#808080>No bosses tracked</#808080>".toNative() }
    
    private val raphaelIconCache by lazy { TelosItemUtils.createItemStack(TelosItemUtils.BOSS_RAPHAEL) }
    private val raphaelCompassCache by lazy { createRaphaelCompass() }
    
    private data class CachedBossLine(
        val icon: ItemStack,
        val compass: ItemStack?,
        val textComponent: Component
    )
    
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
            val dimensionPath = level?.dimension()?.identifier()?.path
            if (dimensionPath != Constants.DIMENSION_REALM && !example) return@render Pair(0, 0)
            
            val isChatFocused = mc.screen is net.minecraft.client.gui.screens.ChatScreen
            val now = System.currentTimeMillis()
            
            if (now - lastUpdateMs > 50L || isChatFocused != lastChatFocused || example != lastExample) {
                lastUpdateMs = now
                lastChatFocused = isChatFocused
                lastExample = example
                
                rebuildCache(example, isChatFocused)
            }
            
            if (cachedBossLines.isEmpty() && !example) return@render Pair(0, 0)
            
            val font = mc.font
            val titleColor = widgetColor.rgba and 0x00FFFFFF
            val borderColor = 0xFF000000.toInt() or titleColor
            val bgColor = 0xC00C0C0C.toInt()
            
            val r = ((titleColor shr 16) and 0xFF)
            val g = ((titleColor shr 8) and 0xFF)
            val b = (titleColor and 0xFF)
            val labelColor = 0xFF000000.toInt() or (minOf(255, (r * 1.8).toInt()) shl 16) or (minOf(255, (g * 1.8).toInt()) shl 8) or minOf(255, (b * 1.8).toInt())
            
            // Draw background
            fill(1, 0, cachedBoxWidth - 1, cachedBoxHeight, bgColor)
            fill(0, 1, 1, cachedBoxHeight - 1, bgColor)
            fill(cachedBoxWidth - 1, 1, cachedBoxWidth, cachedBoxHeight - 1, bgColor)
            
            // Draw borders
            val strHeightHalf = font.lineHeight / 2
            val strAreaWidth = font.width(titleComponent) + 4
            
            fill(2, 1 + strHeightHalf, 6, 2 + strHeightHalf, borderColor)
            fill(2 + strAreaWidth + 4, 1 + strHeightHalf, cachedBoxWidth - 2, 2 + strHeightHalf, borderColor)
            fill(2, cachedBoxHeight - 2, cachedBoxWidth - 2, cachedBoxHeight - 1, borderColor)
            fill(1, 2 + strHeightHalf, 2, cachedBoxHeight - 2, borderColor)
            fill(cachedBoxWidth - 2, 2 + strHeightHalf, cachedBoxWidth - 1, cachedBoxHeight - 2, borderColor)
            
            // Draw title
            text(font, titleComponent, 8, 2, borderColor, false)
            
            // Draw boss lines
            if (cachedBossLines.isNotEmpty()) {
                var yOffset = font.lineHeight + 4
                val leftPadding = 6
                val iconSize = 16
                val iconPadding = 2
                val lineSpacing = 16
                
                for (line in cachedBossLines) {
                    var xOffset = leftPadding
                    
                    item(line.icon, xOffset, yOffset - 2)
                    xOffset += iconSize + iconPadding
                    
                    if (line.compass != null) {
                        item(line.compass, xOffset, yOffset - 2)
                        xOffset += iconSize + iconPadding
                    }
                    
                    text(font, line.textComponent, xOffset, yOffset, labelColor, false)
                    yOffset += lineSpacing
                }
            } else {
                text(font, emptyTrackerComponent, 6, font.lineHeight + 4, 0xFF808080.toInt(), false)
            }
            
            Pair(cachedBoxWidth, cachedBoxHeight)
        }
    }
    
    private fun rebuildCache(example: Boolean, isChatFocused: Boolean) {
        cachedBossLines.clear()
        
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
        
        val portalBosses = if (example) emptyList() else BossState.getBossesByState(BossState.State.DEFEATED_PORTAL_ACTIVE).filter { it.name != "Raphael" && it.name !in hiddenShadowlandsBosses }
        
        val allActiveBosses = aliveBosses + portalBosses
        val raphaelPortalBoss = if (!example) BossState.getBoss("Raphael") else null
        val showRaphael = !example && (allActiveBosses.isNotEmpty() || BossState.raphaelProgress > 0 || raphaelPortalBoss != null)
        
        val font = mc.font
        var maxLabelWidth = 0
        val iconSize = 16
        val iconPadding = 2
        
        // Build Active Bosses
        for (boss in allActiveBosses) {
            val compass = if (boss.state != BossState.State.DEFEATED) boss.getCompass() else null
            val textComp = buildBossText(boss, isChatFocused)
            
            val iconsWidth = if (compass != null) (iconSize + iconPadding) * 2 else iconSize + iconPadding
            val lineWidth = iconsWidth + font.width(textComp)
            if (lineWidth > maxLabelWidth) maxLabelWidth = lineWidth
            
            cachedBossLines.add(CachedBossLine(boss.getBossIcon(), compass, textComp))
        }
        
        // Build Raphael Line
        if (showRaphael) {
            val raphaelCompass = raphaelPortalBoss?.getCompass() ?: raphaelCompassCache
            val textComp = buildRaphaelText(raphaelPortalBoss)
            
            val iconsWidth = (iconSize + iconPadding) * 2
            val lineWidth = iconsWidth + font.width(textComp)
            if (lineWidth > maxLabelWidth) maxLabelWidth = lineWidth
            
            cachedBossLines.add(CachedBossLine(raphaelIconCache, raphaelCompass, textComp))
        }
        
        if (cachedBossLines.isEmpty()) {
            maxLabelWidth = font.width(emptyTrackerComponent)
        }
        
        val titleWidth = font.width(titleComponent)
        val totalBossCount = cachedBossLines.size
        
        cachedBoxWidth = maxOf(titleWidth + 16, maxLabelWidth + 12)
        cachedBoxHeight = font.lineHeight + 2 + (maxOf(totalBossCount, 1) * 16) + 4
    }
    
    /**
     * Build the text component for a boss
     */
    private fun buildBossText(boss: BossState.TrackedBoss, isChatFocused: Boolean): Component {
        val distColor = when {
            boss.distanceMarkerValue <= 2.0 -> "#00FF00"
            boss.distanceMarkerValue <= 4.0 -> "#FFFF55"
            boss.distanceMarkerValue <= 6.0 -> "#FFAA00"
            boss.distanceMarkerValue <= 8.0 -> "#FF3333"
            else -> "#AA0000"
        }
        
        val nameColor = when {
            boss.state == BossState.State.DEFEATED -> "#555555"
            boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE -> "#FFD700"
            boss.calledPlayerName != null -> "#00FF00"
            else -> "#FFFFFF"
        }
        
        var text = "<$distColor>${Constants.DISTANCE_MARKER}</$distColor> <$nameColor>${boss.name}</$nameColor>"
        
        if (boss.calledPlayerName != null) {
            val playerName = if (isChatFocused) boss.calledPlayerName else boss.calledPlayerName!!.substring(0, 3.coerceAtMost(boss.calledPlayerName!!.length))
            text += " <#00FF00>[$playerName]</#00FF00>"
        }
        
        if (boss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
            text += " <#FFD700>(${boss.portalTimer / 20}s)</#FFD700>"
        }
        
        return text.toNative()
    }
    
    private fun buildRaphaelText(raphaelPortalBoss: BossState.TrackedBoss?): Component {
        val player = mc.player
        val raphaelDistance = if (player != null) {
            val playerPos = Vec3.atCenterOf(player.blockPosition())
            val raphaelPos = Vec3.atCenterOf(BlockPos(-15, 243, 88))
            floor(playerPos.distanceTo(raphaelPos) * 0.008)
        } else {
            0.0
        }
        
        val distColor = when {
            raphaelDistance <= 2.0 -> "#00FF00"
            raphaelDistance <= 4.0 -> "#FFFF55"
            raphaelDistance <= 6.0 -> "#FFAA00"
            raphaelDistance <= 8.0 -> "#FF3333"
            else -> "#AA0000"
        }
        
        val progColor = if (BossState.raphaelProgress >= BossState.raphaelMaxProgress) "#00FF00" else "#FFFF00"
        
        var text = "<$distColor>${Constants.DISTANCE_MARKER}</$distColor> <#FF3333>Raphael</#FF3333> <#AAAAAA>[</#AAAAAA><$progColor>${BossState.raphaelProgress}</$progColor><#AAAAAA>/${BossState.raphaelMaxProgress}]</#AAAAAA>"
        
        if (raphaelPortalBoss != null && raphaelPortalBoss.state == BossState.State.DEFEATED_PORTAL_ACTIVE) {
            val secondsRemaining = raphaelPortalBoss.portalTimer / 20
            text += " <#FFD700>(${secondsRemaining}s)</#FFD700>"
        }
        
        return text.toNative()
    }
    
    /**
     * Create compass pointing to Raphael's castle
     */
    private fun createRaphaelCompass(): ItemStack {
        val compass = ItemStack(Items.COMPASS)
        val dimensionKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("telos", "realm")
        )
        val globalPos = GlobalPos(dimensionKey, BlockPos(-15, 243, 88))
        val lodestoneTracker = LodestoneTracker(Optional.of(globalPos), false)
        compass.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker)
        compass.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
        return compass
    }
}