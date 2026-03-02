package me.melinoe

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.melinoe.clickgui.ClickGUI

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<ClickGUI> = ConfigScreenFactory { ClickGUI }
}