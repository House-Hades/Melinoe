package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.StringSetting
import me.melinoe.clickgui.settings.impl.ActionSetting
import me.melinoe.utils.Color
import me.melinoe.utils.Colors
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import com.mojang.authlib.GameProfile

/**
 * Player Size Module - changes the size of the player.
 */
object PlayerSizeModule : Module(
    name = "Player Size",
    category = Category.VISUAL,
    description = "Changes the size of the player."
) {
    private val devSize by BooleanSetting("Dev Size", true, desc = "Toggles client side dev size for your own player").withDependency { isRandom }
    private val devSizeX by NumberSetting("Size X", 1f, -1f, 3f, 0.1f, desc = "X scale of the dev size")
    private val devSizeY by NumberSetting("Size Y", 1f, -1f, 3f, 0.1f, desc = "Y scale of the dev size")
    private val devSizeZ by NumberSetting("Size Z", 1f, -1f, 3f, 0.1f, desc = "Z scale of the dev size")
    private val devWings by BooleanSetting("Wings", false, desc = "Toggles dragon wings").withDependency { isRandom }
    private val devWingsColor by ColorSetting("Wings Color", Colors.WHITE, desc = "Color of the dev wings").withDependency { devWings && isRandom }
    private var showHidden by DropdownSetting("Show Hidden").withDependency { isRandom }
    private val passcode by StringSetting("Passcode", "melinoe", desc = "Passcode for dev features").withDependency { showHidden && isRandom }

    // TODO: Add web utils support for dev server integration
    // const val DEV_SERVER = "https://api.nowayitzjoey.com/devs/"

    // Simplified version without web functionality - can be added later
    val sendDevData by ActionSetting("Send Dev Data", desc = "Sends dev data to the server (requires web utils)") {
        showHidden = false
        fun valid(v: Float) = (v in 0.8f..1.6f) || (v in -1.0f..-0.8f)
        if (!valid(devSizeX) || !valid(devSizeY) || !valid(devSizeZ)) {
            Melinoe.logger.warn("Player Size: Global values must be between 0.8..1.6 or -1..-0.8")
            return@ActionSetting
        }
        // TODO: Add web utils integration
        Melinoe.logger.info("Player Size: Send dev data functionality requires web utils integration")
    }.withDependency { isRandom }

    // Simplified random player storage (without web sync for now)
    private val randoms: HashMap<String, RandomPlayer> = HashMap()
    private val isRandom get() = randoms.containsKey(Melinoe.mc.user?.name ?: "")

    data class RandomPlayer(
        val customName: String? = null,
        val name: String,
        val isDev: Boolean? = null,
        val wingsColor: List<Int> = emptyList(),
        val scale: List<Float> = listOf(1f, 1f, 1f),
        val wings: Boolean = false
    )

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val playerName = Melinoe.mc.player?.gameProfile?.name
        if (enabled && gameProfile.name == playerName && !randoms.containsKey(gameProfile.name)) {
            if (devSizeY < 0) matrix.translate(0.0, (devSizeY * 2).toDouble(), 0.0)
            matrix.scale(devSizeX, devSizeY, devSizeZ)
        }
        if (!randoms.containsKey(gameProfile.name)) return
        if (!devSize && gameProfile.name == playerName) return
        val random = randoms[gameProfile.name] ?: return
        if (random.scale[1] < 0) matrix.translate(0.0, (random.scale[1] * 2).toDouble(), 1.0)
        matrix.scale(random.scale[0], random.scale[1], random.scale[2])
    }

    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "melinoe:game_profile" }
}
