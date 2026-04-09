package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.interfaces.ChatTabs
import me.melinoe.utils.Message
import me.melinoe.utils.emoji.EmojiShortcodes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents the different visual tabs above the chat box
 */
enum class ChatTab(val displayName: String) {
    ALL("All"), CHAT("Chat"), CALLOUTS("Callouts"), GROUP("Group"), GUILD("Guild"),
    MESSAGES("Messages"), DROPS("Drops"), DEATHS("Deaths"), CRAFTS("Crafts"), UTILITY("Utility")
}

/**
 * Represents the server side chat modes
 */
enum class ServerChatCategory(val id: String) {
    DEFAULT("default"), GUILD("guild"), GROUP("group")
}

private data class QueuedMessage(val targetCategory: ServerChatCategory, val message: String)

/**
 * Tracks automatically switching modes, sending a message, and switching back
 */
private enum class QueueState {
    IDLE,                  // Not currently sending anything
    WAITING_FOR_SWITCH,    // Waiting for server to confirm the switch to the new channel
    READY_TO_SEND_MSG,     // Safe to send the message
    WAITING_FOR_RESTORE    // Waiting to switch back to the original channel
}

object ChatModule : Module(
    name = "Chat",
    category = Category.MISC,
    description = "Adds tabs, filtering, and hiding of messages"
) {
    private val hideUtilityMessages by BooleanSetting("Hide Utility Messages", true, "Hides auction, fame, and auto-sell messages")
    private val enableChatTabs by BooleanSetting("Enable Chat Tabs", true, "Enable the chat tab grouping system")
    
    private val tabsDropdown by DropdownSetting("Chat Tabs", false).withDependency { enableChatTabs }
    private val showChatTab by BooleanSetting("Show Chat Tab", false, "Display the Chat tab").withDependency { tabsDropdown && enableChatTabs }
    private val showCalloutsTab by BooleanSetting("Show Callouts Tab", true, "Display the Callouts tab").withDependency { tabsDropdown && enableChatTabs }
    private val showGroupTab by BooleanSetting("Show Group Tab", false, "Display the Group tab").withDependency { tabsDropdown && enableChatTabs }
    private val showGuildTab by BooleanSetting("Show Guild Tab", true, "Display the Guild tab").withDependency { tabsDropdown && enableChatTabs }
    private val showMessagesTab by BooleanSetting("Show Messages Tab", true, "Display the Messages tab").withDependency { tabsDropdown && enableChatTabs }
    private val showDropsTab by BooleanSetting("Show Drops Tab", true, "Display the Drops tab").withDependency { tabsDropdown && enableChatTabs }
    private val showDeathsTab by BooleanSetting("Show Deaths Tab", false, "Display the Deaths tab").withDependency { tabsDropdown && enableChatTabs }
    private val showCraftsTab by BooleanSetting("Show Crafts Tab", false, "Display the Crafts tab").withDependency { tabsDropdown && enableChatTabs }
    
    var hideGroupContent = false; private set
    var hideGuildContent = false; private set
    
    private val toggleHideGroupKey by KeybindSetting("Censor Group", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle replacing group messages with ...")
        .onPress {
            hideGroupContent = !hideGroupContent
            processedCache.clear() // Clear cache so chat regenerates with censored text
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
    
    private val toggleHideGuildKey by KeybindSetting("Censor Guild", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle replacing guild messages with ...")
        .onPress {
            hideGuildContent = !hideGuildContent
            processedCache.clear() // Clear cache so chat regenerates with censored text
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
    
    /**
     * Prevents the user from switching tabs while in a custom, specific tab
     */
    private fun checkRestrictedTabSwitch(): Boolean {
        if (activeTab == ChatTab.GUILD || activeTab == ChatTab.GROUP || activeTab == ChatTab.CHAT) {
            Message.error("You cannot manually switch chat modes while in a specific chat tab. Use /ac, /gc, or /grc.")
            return true
        }
        return false
    }
    
    private val switchGuildChatKey by KeybindSetting("Toggle Guild", GLFW.GLFW_KEY_UNKNOWN, desc = "Switch between default and guild chat")
        .onPress {
            if (!checkRestrictedTabSwitch()) toggleServerChatMode(ServerChatCategory.GUILD)
        }
    
    private val switchGroupChatKey by KeybindSetting("Toggle Group", GLFW.GLFW_KEY_UNKNOWN, desc = "Switch between default and group chat")
        .onPress {
            if (!checkRestrictedTabSwitch()) toggleServerChatMode(ServerChatCategory.GROUP)
        }
    
    var activeTab: ChatTab = ChatTab.ALL
        private set
    
    private var lastTabState: ChatTab = ChatTab.ALL
    private var lastEnabledState: Boolean = false
    private var scrollOffset = 0
    private var lastTickCount = -1
    
    private var cachedAvailableTabs: List<ChatTab> = emptyList()
    private var lastTabSettingsHash = -1
    private val tabWidthCache = mutableMapOf<ChatTab, Int>()
    
    data class ProcessedMessage(val category: ChatTab, val censoredContent: Component, val isTransient: Boolean = false)
    
    // Caches parsed messages to save CPU power from checking regex repeatedly
    private val processedCache = WeakHashMap<Component, ProcessedMessage>()
    
    val isChatTabsEnabled: Boolean get() = enableChatTabs
    val isHideUtilityMessages: Boolean get() = hideUtilityMessages
    
    // Regex patterns used to identify message types
    private val contentRegex = Regex("^(.*?\\[(?:Group|Guild)\\].*?:\\s*)", RegexOption.IGNORE_CASE)
    private val modeSwitchRegex = Regex("^Set your chat mode to (\\w+)\\.", RegexOption.IGNORE_CASE)
    
    private val deathCauses = arrayOf(
        " fell off against ", " had their bits blown off by ", " was torn in half by ",
        " was spangled by ", " got snapped in half by ", " had their head removed by ",
        " was diagnosed with 'skill issue' by ", " dieded by ", " had their day ruined by "
    )
    
    var currentServerCategory = ServerChatCategory.DEFAULT
        private set
    
    // Queue System Variables
    private val messageQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private var queueState = QueueState.IDLE
    private var originalCategory = ServerChatCategory.DEFAULT
    private var pendingMessage = ""
    private var stateStartTime = 0L
    private var lastMessageTime = 0L
    private var lastTabSwitchRequest = 0L
    
    init {
        loadChatCategory()
    }
    
    /**
     * Builds and caches the list of currently enabled chat tabs
     */
    private val availableTabs: List<ChatTab>
        get() {
            var hash = 0
            if (showChatTab) hash = hash or 1
            if (showGroupTab) hash = hash or 2
            if (showGuildTab) hash = hash or 4
            if (showMessagesTab) hash = hash or 8
            if (showDropsTab) hash = hash or 16
            if (showDeathsTab) hash = hash or 32
            if (showCraftsTab) hash = hash or 64
            if (showCalloutsTab) hash = hash or 128
            
            // Rebuild the list only if the toggles have changed
            if (hash != lastTabSettingsHash) {
                lastTabSettingsHash = hash
                cachedAvailableTabs = buildList(10) {
                    add(ChatTab.ALL) // ALL tab is always visible
                    if (showChatTab) add(ChatTab.CHAT)
                    if (showCalloutsTab) add(ChatTab.CALLOUTS)
                    if (showGroupTab) add(ChatTab.GROUP)
                    if (showGuildTab) add(ChatTab.GUILD)
                    if (showMessagesTab) add(ChatTab.MESSAGES)
                    if (showDropsTab) add(ChatTab.DROPS)
                    if (showDeathsTab) add(ChatTab.DEATHS)
                    if (showCraftsTab) add(ChatTab.CRAFTS)
                }
            }
            return cachedAvailableTabs
        }
    
    /**
     * Caches text width measurements so we don't recalculate font widths every single frame
     */
    private fun getTabWidth(tab: ChatTab, font: Font): Int {
        return tabWidthCache.getOrPut(tab) { font.width(tab.displayName) }
    }
    
    /**
     * Sends the server side command to switch chat modes
     */
    private fun setServerChatMode(target: ServerChatCategory) {
        if (currentServerCategory != target) {
            lastTabSwitchRequest = System.currentTimeMillis()
            Minecraft.getInstance().connection?.sendCommand("chat ${target.id}")
        }
    }
    
    /**
     * Swaps the active UI tab, forces a server channel switch if necessary,
     * and tells the Mixin to render the new history list
     */
    private fun setActiveTab(tab: ChatTab) {
        if (activeTab != tab) {
            activeTab = tab
            
            // Automatically switch the chat mode when entering specific tabs
            when (tab) {
                ChatTab.CHAT -> setServerChatMode(ServerChatCategory.DEFAULT)
                ChatTab.GUILD -> setServerChatMode(ServerChatCategory.GUILD)
                ChatTab.GROUP -> setServerChatMode(ServerChatCategory.GROUP)
                else -> {}
            }
            
            ensureActiveTabVisible()
            (Minecraft.getInstance().gui?.chat as? ChatTabs)?.`melinoe$swapTab`(tab)
        }
    }
    
    fun enqueueMessage(category: ServerChatCategory, message: String) {
        messageQueue.add(QueuedMessage(category, message))
    }
    
    private fun toggleServerChatMode(target: ServerChatCategory) {
        val mc = Minecraft.getInstance()
        if (currentServerCategory == target) {
            mc.connection?.sendCommand("chat default")
        } else {
            mc.connection?.sendCommand("chat ${target.id}")
        }
    }
    
    fun recordNativeMessageSent(message: String) {
        if (!message.startsWith("/")) {
            lastMessageTime = System.currentTimeMillis()
        }
    }
    
    private fun tickQueue() {
        if (queueState == QueueState.IDLE && messageQueue.isEmpty()) return
        
        val connection = Minecraft.getInstance().connection ?: return
        val now = System.currentTimeMillis()
        
        // Timeout protection: If a switch takes longer than 3 seconds, abort so we don't get stuck
        if (queueState != QueueState.IDLE && now - stateStartTime > 3000) {
            queueState = QueueState.IDLE
        }
        
        // Handle pending messages
        if (queueState == QueueState.IDLE && messageQueue.isNotEmpty()) {
            val next = messageQueue.peek() ?: return
            
            // If we're already in the right chat mode, send the message
            if (currentServerCategory == next.targetCategory) {
                if (now - lastMessageTime >= 1100) {
                    messageQueue.poll()
                    connection.sendChat(next.message)
                    lastMessageTime = now
                }
            } else {
                // If we are in the wrong chat mode, save the current chat mode, tell the server to switch, and wait
                originalCategory = currentServerCategory
                pendingMessage = next.message
                queueState = QueueState.WAITING_FOR_SWITCH
                stateStartTime = now
                connection.sendCommand("chat ${next.targetCategory.id}")
            }
        }
        // Send the message once the confirmation message is received
        else if (queueState == QueueState.READY_TO_SEND_MSG) {
            if (now - lastMessageTime >= 1100) {
                messageQueue.poll()
                connection.sendChat(pendingMessage)
                lastMessageTime = now
                
                // Now switch back to our original channel
                queueState = QueueState.WAITING_FOR_RESTORE
                stateStartTime = now
                connection.sendCommand("chat ${originalCategory.id}")
            }
        }
    }
    
    /**
     * Reads incoming messages to find the server side chat mode confirmation
     */
    fun handleChatModeMessage(plainText: String): Boolean {
        val match = modeSwitchRegex.find(plainText) ?: return false
        val newMode = match.groupValues[1].lowercase()
        
        currentServerCategory = ServerChatCategory.entries.find { it.id == newMode } ?: ServerChatCategory.DEFAULT
        saveChatCategory()
        
        if (queueState == QueueState.WAITING_FOR_SWITCH) {
            queueState = QueueState.READY_TO_SEND_MSG
            return true
        }
        
        if (queueState == QueueState.WAITING_FOR_RESTORE) {
            queueState = QueueState.IDLE
            return true
        }
        
        if (System.currentTimeMillis() - lastTabSwitchRequest < 1500) {
            lastTabSwitchRequest = 0L
            return true
        }
        
        return false
    }
    
    /**
     * Catches the user typing and sending to route it to the queuing system instead of sending directly
     */
    fun interceptOutgoingMessage(rawMessage: String, addToHistory: Boolean): Boolean {
        if (!enabled) return false
        
        // Don't intercept server commands unless they are restricted chat tab commands
        if (rawMessage.startsWith("/")) {
            val lowerCmd = rawMessage.lowercase()
            if (lowerCmd == "/chat" || lowerCmd.startsWith("/chat ")) {
                if (checkRestrictedTabSwitch()) {
                    if (addToHistory) Minecraft.getInstance().gui?.chat?.addRecentChat(rawMessage)
                    return true
                }
            }
            return false
        }
        
        // If we're already busy switching channels, intercept the message into the queue safely
        if (queueState != QueueState.IDLE) {
            val processedMessage = EmojiShortcodes.replaceEmojiWithShortcodes(rawMessage)
            enqueueMessage(originalCategory, processedMessage)
            if (addToHistory) Minecraft.getInstance().gui?.chat?.addRecentChat(rawMessage)
            return true
        }
        return false
    }
    
    private fun saveChatCategory() {
        runCatching {
            val file = File(Minecraft.getInstance().gameDirectory, "config/chat_state.txt")
            file.parentFile.mkdirs()
            file.writeText(currentServerCategory.id)
        }
    }
    
    private fun loadChatCategory() {
        runCatching {
            val file = File(Minecraft.getInstance().gameDirectory, "config/chat_state.txt")
            if (file.exists()) {
                val saved = file.readText().trim()
                currentServerCategory = ServerChatCategory.entries.find { it.id == saved } ?: ServerChatCategory.DEFAULT
            }
        }
    }
    
    fun onRender(tickCount: Int) {
        if (tickCount == lastTickCount) return
        lastTickCount = tickCount
        checkAndRefreshChat()
        tickQueue()
    }
    
    /**
     * Syncs the Tab UI to match settings
     */
    private fun checkAndRefreshChat() {
        val currentEnabled = enabled
        var needsFullRescale = false
        var needsTabSwap = false
        
        if (lastEnabledState != currentEnabled) {
            lastEnabledState = currentEnabled
            if (!currentEnabled) activeTab = ChatTab.ALL
            needsFullRescale = true
        }
        
        if (!currentEnabled) {
            if (needsFullRescale) Minecraft.getInstance().gui?.chat?.rescaleChat()
            return
        }
        
        // Reset to ALL tab if tabs were disabled mid-session
        if (!enableChatTabs && activeTab != ChatTab.ALL) {
            activeTab = ChatTab.ALL
            needsFullRescale = true
        }
        
        val tabs = availableTabs
        if (activeTab != ChatTab.ALL && activeTab !in tabs) {
            activeTab = ChatTab.ALL
            needsTabSwap = true
        }
        
        if (lastTabState != activeTab) {
            lastTabState = activeTab
            needsTabSwap = true
        }
        
        if (needsFullRescale) {
            ensureActiveTabVisible()
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        } else if (needsTabSwap) {
            ensureActiveTabVisible()
            (Minecraft.getInstance().gui?.chat as? ChatTabs)?.`melinoe$swapTab`(activeTab)
        }
    }
    
    /**
     * Pre-processes incoming messages: categorizes them and blanks out guild/group messages if censored
     */
    fun getProcessedMessage(original: Component): ProcessedMessage {
        val plainText = original.string
        
        // Flag transient (temporary) client-side messages so they don't clog up the real chat log file
        if (plainText.contains("You cannot manually switch chat modes") || (plainText.contains("Melinoe") && plainText.contains("›"))) {
            return ProcessedMessage(ChatTab.ALL, original, isTransient = true)
        }
        
        return processedCache.getOrPut(original) {
            val isCallout = KeybindsModule.isCallout(plainText)
            val category = if (isCallout) ChatTab.CALLOUTS else determineCategory(plainText)
            
            val shouldCensor = (hideGroupContent && category == ChatTab.GROUP) ||
                    (hideGuildContent && category == ChatTab.GUILD)
            
            val processed = if (shouldCensor) censorComponent(original, plainText) else original
            
            ProcessedMessage(category, processed, isTransient = false)
        }
    }
    
    /**
     * Sorts strings into specific tabs
     */
    private fun determineCategory(message: String): ChatTab {
        // Handle explicit server chat mode notices first
        val modeMatch = modeSwitchRegex.find(message)
        if (modeMatch != null) {
            return when (modeMatch.groupValues[1].lowercase()) {
                "guild" -> ChatTab.GUILD
                "group" -> ChatTab.GROUP
                "default", "all" -> ChatTab.CHAT
                else -> ChatTab.ALL
            }
        }
        
        // Guild
        if (message.contains("[Guild]")) return ChatTab.GUILD
        
        // Group
        if (message.contains("[Group]")) return ChatTab.GROUP
        
        // Utility messages which will be hidden
        if (message == "Auction items have been updated." ||
            message.startsWith("Auto-sell earnings: ") ||
            (message.startsWith("+") && message.contains(" Fame gained!"))) {
            return ChatTab.UTILITY
        }
        
        // Chat
        if (message.contains(": ")) return ChatTab.CHAT
        
        // Messages
        if ((message.contains("To ") || message.contains("From ")) && message.contains("┅")) return ChatTab.MESSAGES
        
        // Drops
        if ((message.contains(" got ") && message.contains(" from ")) ||
            (message.contains(" - Dropped ") && message.contains(" pity from "))) {
            return ChatTab.DROPS
        }
        
        // Crafts
        if (message.contains(" has just crafted ")) return ChatTab.CRAFTS
        
        // Deaths
        for (i in deathCauses.indices) {
            if (message.contains(deathCauses[i])) return ChatTab.DEATHS
        }
        
        return ChatTab.ALL
    }
    
    /**
     * Hides the body of a message leaving only the prefix, used for the censoring of guild and group
     */
    private fun censorComponent(original: Component, plainText: String): Component {
        val match = contentRegex.find(plainText) ?: return original
        val prefixLength = match.value.length
        
        val newComp = Component.empty()
        var currentLen = 0
        var appendedDots = false
        
        // Loop through the text formatting components one by one
        original.visit({ style: Style, text: String ->
            if (appendedDots) return@visit Optional.empty<Unit>()
            
            val remaining = prefixLength - currentLen
            if (remaining > 0) {
                // If we are still reading the prefix, keep it
                if (text.length <= remaining) {
                    newComp.append(Component.literal(text).withStyle(style))
                    currentLen += text.length
                } else {
                    // Split the text right at the end of the prefix, and add "..."
                    val portion = text.substring(0, remaining)
                    newComp.append(Component.literal(portion).withStyle(style))
                    currentLen += portion.length
                    newComp.append(Component.literal("...").withStyle(style))
                    appendedDots = true
                }
            } else {
                newComp.append(Component.literal("...").withStyle(style))
                appendedDots = true
            }
            Optional.empty<Unit>() // Continue traversing the component tree
        }, Style.EMPTY)
        
        if (!appendedDots) newComp.append(Component.literal("..."))
        return newComp
    }
    
    /**
     * Auto-scrolls the Tab UI left or right if there are too many tabs to fit on the screen
     */
    private fun ensureActiveTabVisible() {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        var currentX = 0
        val tabPadding = 2
        val tabSpacing = 2
        
        for (tab in availableTabs) {
            val w = getTabWidth(tab, font) + (tabPadding * 2)
            if (tab == activeTab) {
                // Scroll left if tab is cut off
                if (currentX < scrollOffset) {
                    scrollOffset = currentX
                }
                // Scroll right if tab is cut off
                else if (currentX + w > scrollOffset + maxWidth) {
                    scrollOffset = (currentX + w) - maxWidth
                }
                break
            }
            currentX += w + tabSpacing
        }
    }
    
    /**
     * Draws the Tab buttons above the chat input box
     */
    fun renderTabs(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!enabled || !enableChatTabs) return
        
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        val currentY = mc.window.guiScaledHeight - 32 // Offset just above input box
        val tabPadding = 2
        val tabSpacing = 2
        val tabHeight = font.lineHeight + (tabPadding * 2)
        
        guiGraphics.enableScissor(4, currentY - 2, 4 + maxWidth, currentY + tabHeight + 2)
        
        var currentX = 4 - scrollOffset
        for (tab in availableTabs) {
            val tabWidth = getTabWidth(tab, font) + (tabPadding * 2)
            
            // Only draw tabs that are actually visible on screen
            if (currentX + tabWidth > 4 && currentX < 4 + maxWidth) {
                val isHovered = mouseX >= currentX && mouseX <= currentX + tabWidth && mouseY >= currentY && mouseY <= currentY + tabHeight
                
                val textColor = when {
                    tab == activeTab -> -1              // White text for active
                    isHovered -> 0xFFEEEEEE.toInt()     // Light gray on hover
                    else -> 0xFFAAAAAA.toInt()          // Normal gray
                }
                guiGraphics.drawString(font, tab.displayName, currentX + tabPadding, currentY + tabPadding, textColor, true)
            }
            currentX += tabWidth + tabSpacing
        }
        
        guiGraphics.disableScissor()
    }
    
    /**
     * Handles left-clicking tabs
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!enabled || !enableChatTabs || button != 0) return false
        
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        val currentY = mc.window.guiScaledHeight - 32
        val tabPadding = 2
        val tabSpacing = 2
        val tabHeight = font.lineHeight + (tabPadding * 2)
        
        // Ensure the click was actually inside our horizontal UI strip
        if (mouseX < 4 || mouseX > 4 + maxWidth || mouseY < currentY || mouseY > currentY + tabHeight) return false
        
        var currentX = 4 - scrollOffset
        for (tab in availableTabs) {
            val tabWidth = getTabWidth(tab, font) + (tabPadding * 2)
            if (mouseX >= currentX && mouseX <= currentX + tabWidth) {
                setActiveTab(tab)
                return true
            }
            currentX += tabWidth + tabSpacing
        }
        return false
    }
    
    /**
     * Allows hot-swapping tabs left and right using Alt + Arrow Keys
     */
    fun keyPressed(keyCode: Int, modifiers: Int): Boolean {
        if (!enabled || !enableChatTabs) return false
        
        if ((modifiers and GLFW.GLFW_MOD_ALT) != 0) {
            val tabs = availableTabs
            val currentIndex = tabs.indexOf(activeTab)
            
            // Wrap left
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                val newIndex = if (currentIndex > 0) currentIndex - 1 else tabs.size - 1
                setActiveTab(tabs[newIndex])
                return true
            }
            // Wrap right
            else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                val newIndex = if (currentIndex < tabs.size - 1) currentIndex + 1 else 0
                setActiveTab(tabs[newIndex])
                return true
            }
        }
        return false
    }
}