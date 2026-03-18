package me.melinoe.features.impl.visual

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.entity.state.AvatarRenderState

/**
 * Player Size Module - changes the size of the player.
 */
object PlayerSizeModule : Module(
    name = "Player Size",
    category = Category.VISUAL,
    description = "Changes the size of the player."
) {
    private val sizeX by NumberSetting("Size X", 1f, -1f, 3f, 0.1f, desc = "X scale of the player")
    private val sizeY by NumberSetting("Size Y", 1f, -1f, 3f, 0.1f, desc = "Y scale of the player")
    private val sizeZ by NumberSetting("Size Z", 1f, -1f, 3f, 0.1f, desc = "Z scale of the player")

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val playerName = Melinoe.mc.player?.gameProfile?.name
        if (enabled && gameProfile.name == playerName) {
            if (sizeY < 0) matrix.translate(0.0, (sizeY * 2).toDouble(), 0.0)
            matrix.scale(sizeX, sizeY, sizeZ)
        }
    }

    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "melinoe:game_profile" }
}
