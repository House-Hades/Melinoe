package me.melinoe.features.impl.visual

import me.melinoe.events.ChatPacketEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.ChatManager.hideMessage

/**
 * Chat Filter Module - Hides annoying/spam chat messages
 */
object ChatFilterModule : Module(
    name = "Chat Filter",
    category = Category.VISUAL,
    description = "Hides specific chat messages like auction updates and fame notifications"
) {

    init {
        on<ChatPacketEvent> {
            if (!enabled) return@on
            
            val message = value
            
            // Hide auction update messages
            if (message == "Auction items have been updated.") {
                hideMessage()
                return@on
            }
            
            // Hide fame gain messages
            if (message.matches(Regex("^\\+\\d+ Fame gained!$"))) {
                hideMessage()
                return@on
            }
            
            // Hide autosell messages
            if (message.matches(Regex("^Auto-sell earnings: (.+)$"))) {
                hideMessage()
                return@on
            }
        }
    }
}
