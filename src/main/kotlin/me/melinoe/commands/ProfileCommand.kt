package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import me.melinoe.Melinoe
import me.melinoe.clickgui.ProfileScreen
import me.melinoe.utils.Message

/**
 * Opens the Telos profile viewer. Usage: /profile [username] (alias: /pv).
 * With no username it uses the local player.
 */
val profileCommand = Commodore("profile", "pv") {
    
    runs {
        val self = Melinoe.mc.player?.gameProfile?.name
        if (self == null) {
            Message.error("Usage: /profile <username>")
        } else {
            ProfileScreen.open(self)
        }
    }
    
    executable {
        param("username") {}
        
        runs { username: String ->
            ProfileScreen.open(username)
        }
    }
}