package me.melinoe.features.impl.visual

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import java.util.WeakHashMap


/**
 * Player Size Module - changes the size of the player.
 */
object PlayerSizeModule : Module(
    name = "Player Size",
    category = Category.VISUAL,
    description = "Changes the size of the player."
) {
    private val target by SelectorSetting("Target", "Personal Only", listOf("Both", "Personal Only", "Others Only"), desc = "Which players to scale")
    
    private val sizeX by NumberSetting("Size X", 1f, -1f, 3f, 0.1f, desc = "X scale of the player")
    private val sizeY by NumberSetting("Size Y", 1f, -1f, 3f, 0.1f, desc = "Y scale of the player")
    private val sizeZ by NumberSetting("Size Z", 1f, -1f, 3f, 0.1f, desc = "Z scale of the player")
    
    private class NametagData(var lastComponent: Component?, var isNametag: Boolean)
    private val nametagCache = WeakHashMap<Entity, NametagData>()
    
    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "melinoe:game_profile" }
    
    @JvmStatic
    val REALM_NAMETAG_KEY: RenderStateDataKey<Boolean> = RenderStateDataKey.create { "melinoe:realm_nametag" }
    
    @JvmStatic
    val IS_PERSONAL_KEY: RenderStateDataKey<Boolean> = RenderStateDataKey.create { "melinoe:is_personal_nametag" }
    
    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        if (!enabled) return
        
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val isPersonal = gameProfile.name == Melinoe.mc.player?.gameProfile?.name
        
        val shouldScale = when (target) {
            0 -> true
            1 -> isPersonal
            2 -> !isPersonal
            else -> false
        }
        
        if (shouldScale) applyScale(matrix)
    }
    
    @JvmStatic
    fun getNametag(entity: Display.TextDisplay): Boolean {
        val currentText = entity.text ?: return false
        val data = nametagCache.getOrPut(entity) { NametagData(null, false) }
        
        if (data.lastComponent === currentText) {
            return data.isNametag
        }
        
        data.lastComponent = currentText
        data.isNametag = currentText.string.contains("HP:")
        
        return data.isNametag
    }
    
    @JvmStatic
    fun isPersonalNametag(entity: Entity): Boolean {
        val localPlayer = Melinoe.mc.player ?: return false
        
        return entity.distanceToSqr(localPlayer) < 4.0
    }
    
    @JvmStatic
    fun textDisplayScaleHook(isNametag: Boolean?, isPersonal: Boolean?, matrix: PoseStack) {
        if (!enabled || isNametag != true) return
        
        val personal = isPersonal == true
        val shouldScale = when (target) {
            0 -> true
            1 -> personal
            2 -> !personal
            else -> false
        }
        
        if (shouldScale) {
            // Push the nametag up to match the newly scaled head height
            val heightOffset = 1.8 * (sizeY - 1.0)
            matrix.translate(0.0, heightOffset, 0.0)
            
            applyScale(matrix)
        }
    }
    
    private fun applyScale(matrix: PoseStack) {
        if (sizeY < 0) matrix.translate(0.0, (sizeY * 2).toDouble(), 0.0)
        matrix.scale(sizeX, sizeY, sizeZ)
    }
}