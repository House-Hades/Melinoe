package me.melinoe.features.impl.misc

import com.mojang.blaze3d.vertex.PoseStack
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * No Nametags Module - Cancels the rendering of player nametags
 */
object NoNametagsModule : Module(
    name = "No Nametags",
    category = Category.MISC,
    description = "Hides player nametags from rendering"
) {
    private val hideSelf by BooleanSetting("Hide Self", true, "Hide your own nametag")
    private val hideOthers by BooleanSetting("Hide Others", true, "Hide other players' nametags")
    
    /**
     * Checks if a nametag should be rendered based on whether it's personal or not
     * @param isPersonal Whether this is the local player's nametag
     * @return true if the nametag should be hidden, false otherwise
     */
    @JvmStatic
    fun shouldHideNametag(isPersonal: Boolean): Boolean {
        if (!enabled) return false
        return if (isPersonal) hideSelf else hideOthers
    }
    
    /**
     * Hook for hiding TextDisplay nametags by scaling them to 0
     * This mirrors the textDisplayScaleHook pattern from PlayerSizeModule
     */
    @JvmStatic
    fun textDisplayHideHook(isNametag: Boolean?, isPersonal: Boolean?, matrix: PoseStack) {
        if (!enabled || isNametag != true) return
        
        val personal = isPersonal == true
        val shouldHide = if (personal) hideSelf else hideOthers
        
        if (shouldHide) {
            // Scale to 0 to make the nametag invisible
            matrix.scale(0f, 0f, 0f)
        }
    }
}
