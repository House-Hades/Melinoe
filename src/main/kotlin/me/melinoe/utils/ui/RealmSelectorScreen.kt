package me.melinoe.utils.ui

import me.melinoe.Melinoe
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.startsWithOneOf
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Realm Selector Screen - displays a grid of server buttons for teleportation
 * 
 * Features:
 * - Auto-detects region (NA/EU/SG) based on current world
 * - Displays servers in a 4-column grid
 * - Hub servers centered at the bottom
 * - Gradient-colored title
 * - Strikethrough text for current server
 */
object RealmSelectorScreen : Screen(Component.literal("Realm Selector")) {

    private val serverButtons = mutableListOf<Button>()
    private val buttonLookup = mutableMapOf<String, Button>()
    
    // Server name arrays by region
    private val naServerNames = arrayOf(
        "Groveridge", "Bayou", "Cedar", "Dakota", "Eagleton",
        "Farrion", "Ashburn", "Holloway", "Hub-1", "Missions"
    )
    
    private val euServerNames = arrayOf(
        "Astra", "Balkan", "Creska", "Draskov", "Estenmoor",
        "Falkenburg", "Galla", "Helmburg", "Ivarn", "Jarnwald",
        "Krausenfeld", "Lindenburg", "Hub-1", "Missions"
    )
    
    private val sgServerNames = arrayOf(
        "Asura", "Bayan", "Chantara", "Hub-1", "Missions"
    )
    
    private enum class Region {
        NA, EU, SG, UNKNOWN
    }
    
    private var cachedRegularServers = listOf<String>()
    private var cachedHubServers = listOf<String>()
    private var cachedButtonWidth = 0
    private var initialized = false
    
    // Melinoe red color (0xFF8A0000)
    private val melinoeRed = 0xFF8A0000.toInt()

    private fun ensureInitialized() {
        if (!initialized) {
            initializeServerNames()
            initialized = true
        }
    }

    private fun initializeServerNames() {
        val servers = getServersForRegion()
        cachedButtonWidth = calculateOptimalButtonWidth()
        cacheServerGroups(servers)
    }

    private fun calculateOptimalButtonWidth(): Int {
        val textRenderer = minecraft!!.font
        var maxWidth = 0
        
        for (servers in listOf(naServerNames, euServerNames, sgServerNames)) {
            for (server in servers) {
                val width = textRenderer.width(server)
                if (width > maxWidth) {
                    maxWidth = width
                }
            }
        }
        
        return maxWidth + 20
    }

    private fun getServersForRegion(): List<String> {
        if (!me.melinoe.utils.ServerUtils.isOnTelos()) {
            return listOf("Not on Telos")
        }
        
        val currentWorld = LocalAPI.getCurrentCharacterWorld()
        val region = classifyRegion(currentWorld)
        
        return when (region) {
            Region.NA -> naServerNames.toList()
            Region.EU -> euServerNames.toList()
            Region.SG -> sgServerNames.toList()
            Region.UNKNOWN -> listOf("Not on Telos")
        }
    }

    private fun classifyRegion(world: String): Region {
        val lower = world.trim().lowercase()
        if (lower.isEmpty()) return Region.UNKNOWN
        
        return when (lower[0]) {
            'n' -> Region.NA
            'g' -> Region.EU
            'a' -> Region.SG
            else -> Region.SG
        }
    }

    private fun cacheServerGroups(servers: List<String>) {
        val regular = mutableListOf<String>()
        val hubs = mutableListOf<String>()
        
        for (server in servers) {
            // Treat both Hub-1 and Missions as hub servers so they appear together at the bottom
            if (server.startsWithOneOf("Hub-", "Missions")) {
                hubs.add(server)
            } else {
                regular.add(server)
            }
        }
        
        cachedRegularServers = regular
        cachedHubServers = hubs
    }

    private fun createButtons(servers: List<String>) {
        serverButtons.clear()
        buttonLookup.clear()
        
        for (serverName in servers) {
            val button = Button.builder(Component.literal(serverName)) { btn ->
                val player = minecraft?.player
                if (player != null && me.melinoe.utils.ServerUtils.isOnTelos()) {
                    player.connection.sendCommand("joinq $serverName")
                    minecraft?.setScreen(null) // Close screen after clicking
                }
            }
            .bounds(0, 0, cachedButtonWidth, 20)
            .build()
            
            serverButtons.add(button)
            buttonLookup[serverName] = button
        }
    }

    override fun init() {
        super.init()
        
        // Recalculate everything on init to handle region changes
        val servers = getServersForRegion()
        cachedButtonWidth = calculateOptimalButtonWidth()
        cacheServerGroups(servers)
        
        // Clear and recreate buttons
        serverButtons.clear()
        buttonLookup.clear()
        
        // Create buttons for all servers
        createButtons(servers)
        
        // Add buttons to screen
        for (button in serverButtons) {
            addRenderableWidget(button)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Render background without blur to avoid "Can only blur once per frame" crash
        renderTransparentBackground(guiGraphics)
        
        // Get current server to disable its button
        val currentServerFull = LocalAPI.getCurrentCharacterWorld()
        // Extract just the server name (after comma and space, e.g., "Germany, Draskov" -> "Draskov")
        val currentServerName = if (currentServerFull.contains(", ")) {
            currentServerFull.substringAfterLast(", ").trim()
        } else {
            currentServerFull.trim()
        }
        
        // Update button states and text based on current server
        for ((serverName, button) in buttonLookup) {
            if (serverName == currentServerName) {
                button.active = false
                // Set strikethrough text for current server
                button.message = Component.literal(serverName).withStyle { style ->
                    style.withStrikethrough(true)
                }
            } else {
                button.active = true
                // Reset to normal text for other servers
                button.message = Component.literal(serverName)
            }
        }
        
        // Calculate grid layout
        val columns = 4
        val buttonSpacing = 6
        val buttonWidth = cachedButtonWidth
        val buttonHeight = 20
        val regularServers = cachedRegularServers
        val hubServers = cachedHubServers
        
        // Calculate grid dimensions
        val regularRows = (regularServers.size + columns - 1) / columns
        val totalRows = regularRows + if (hubServers.isEmpty()) 0 else 1
        val totalGridWidth = (buttonWidth * columns) + (buttonSpacing * (columns - 1))
        val totalGridHeight = (buttonHeight * totalRows) + (buttonSpacing * (totalRows - 1))
        
        // Calculate title height
        val titleHeight = (font.lineHeight * 2.0f).toInt()
        val gapBetweenTitleAndMenu = 10
        val totalMenuHeight = titleHeight + gapBetweenTitleAndMenu + totalGridHeight
        
        // Center the entire menu block on screen
        val centerX = (width - totalGridWidth) / 2
        val centerY = (height - totalMenuHeight) / 2
        
        // Calculate title position
        val titleY = centerY
        val gridStartY = centerY + titleHeight + gapBetweenTitleAndMenu
        
        // Render colored title
        renderColoredTitle(guiGraphics, titleY)
        
        // Position regular server buttons
        for (i in regularServers.indices) {
            val row = i / columns
            val col = i % columns
            
            val buttonX = centerX + (col * (buttonWidth + buttonSpacing))
            val buttonY = gridStartY + (row * (buttonHeight + buttonSpacing))
            
            val serverName = regularServers[i]
            val button = buttonLookup[serverName]
            if (button != null) {
                button.x = buttonX
                button.y = buttonY
            }
        }
        
        // Position hub buttons centered in the last row
        if (hubServers.isNotEmpty()) {
            val hubRow = regularRows
            val hubCount = hubServers.size
            val hubRowWidth = (hubCount * buttonWidth) + ((hubCount - 1) * buttonSpacing)
            val hubStartX = centerX + (totalGridWidth - hubRowWidth) / 2
            
            for (i in hubServers.indices) {
                val buttonX = hubStartX + (i * (buttonWidth + buttonSpacing))
                val buttonY = gridStartY + (hubRow * (buttonHeight + buttonSpacing))
                
                val serverName = hubServers[i]
                val button = buttonLookup[serverName]
                if (button != null) {
                    button.x = buttonX
                    button.y = buttonY
                }
            }
        }
        
        // Render current server indicator text (use full server name)
        if (currentServerFull.isNotEmpty()) {
            // "Current:" in bold dark red (0xFF8A0000), server name in bright red (0xFF3333) without bold
            val currentLabel = "§lCurrent: "
            val serverName = currentServerFull
            
            val labelWidth = font.width(currentLabel)
            val serverWidth = font.width(serverName)
            val totalWidth = labelWidth + serverWidth
            val textX = (width - totalWidth) / 2
            val textY = gridStartY + totalGridHeight + 15
            
            // Draw "Current:" in bold dark red
            guiGraphics.drawString(font, currentLabel, textX, textY, melinoeRed, true)
            
            // Draw server name in bright red (0xFFFF3333) without bold
            val brightRed = 0xFFFF3333.toInt()
            guiGraphics.drawString(font, serverName, textX + labelWidth, textY, brightRed, true)
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    /**
     * Renders the colored title "Realm Selector" with 2.0x font scale
     */
    private fun renderColoredTitle(guiGraphics: GuiGraphics, yPosition: Int) {
        val coloredText = arrayOf(
            "§lR", "§le", "§la", "§ll", "§lm", " ", "§lS", "§le", "§ll", "§le", "§lc", "§lt", "§lo", "§lr"
        )
        
        val colors = intArrayOf(
            0xFF8A0000.toInt(),
            0xFF930404.toInt(),
            0xFF9C0808.toInt(),
            0xFFA50C0C.toInt(),
            0xFFAE1010.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFC01818.toInt(),
            0xFFC91B1B.toInt(),
            0xFFD21F1F.toInt(),
            0xFFDB2323.toInt(),
            0xFFE42727.toInt(),
            0xFFED2B2B.toInt(),
            0xFFF62F2F.toInt(),
            0xFFFF3333.toInt()
        )
        
        // Calculate starting position with 2.0x scale
        val fullText = "Realm Selector"
        val scaledWidth = (font.width(fullText) * 2.0f).toInt()
        val titleX = (width - scaledWidth) / 2
        val titleY = yPosition
        
        // Apply 2.0x scale transformation using pose stack
        val poseStack = guiGraphics.pose()
        poseStack.pushMatrix()
        poseStack.scale(2.0f, 2.0f)
        
        // Render each character with its specific color
        var currentX = titleX / 2
        val scaledTitleY = titleY / 2
        
        for (i in coloredText.indices) {
            val charText = coloredText[i]
            val color = colors[i]
            
            guiGraphics.drawString(font, charText, currentX, scaledTitleY, color, true)
            currentX += font.width(charText)
        }
        
        // Restore matrix
        poseStack.popMatrix()
    }

    override fun isPauseScreen(): Boolean = false
}
