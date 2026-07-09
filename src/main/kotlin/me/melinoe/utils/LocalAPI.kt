package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.events.*
import me.melinoe.events.core.EventBus
import me.melinoe.events.core.on
import me.melinoe.events.core.onReceive
import me.melinoe.features.impl.combat.ArmorCooldownsModule
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import kotlin.math.max

/**
 * Local API for tracking player state and server information.
 * Now uses packet-based updates for instant response and better performance.
 */
object LocalAPI {
    private var currentCharacterType = ""
    private var currentCharacterClass = ""
    private var currentCharacterLevel = -1
    private var currentCharacterWorld = ""
    private var currentCharacterArea = ""
    private var currentCharacterDimension = ""
    private var currentCharacterFighting = ""
    private var lastKnownBoss = ""
    private var lastKnownBossHash = 0
    private var countdownLock = false
    private var currentPortalCall = ""
    private var currentPortalCallTime = 0
    
    private var portalCountdownTicks = 0
    private var initialized = false
    
    private var lastFiredDimension = ""
    private var lastFiredWorld = ""

    // Tracks realm servers only, ignoring hubs/dungeons
    // lastRealm = most recently visited realm, previousRealm = the one before it.
    private var lastRealm = ""
    private var previousRealm = ""
    
    /**
     * Initialize LocalAPI and register event handlers.
     * MUST be called AFTER EventBus.subscribe(LocalAPI) in Melinoe.kt
     */
    fun initialize() {
        if (initialized) return
        initialized = true
        
        Melinoe.logger.info("LocalAPI: Initializing event-based systems")
        
        // Packet-based character info updates - fires only when tab list changes
        onReceive<ClientboundPlayerInfoUpdatePacket> {
            // Only process if it's a display name update
            if (!actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) return@onReceive
            
            updateCharacterInfo()
        }

        // Event-based boss bar updates - fires only when boss bars change
        on<BossBarUpdateEvent> {
            updateCharacterArea(bossBarMap)
        }
        
        // When a new world loads, remove the world name until the new Tab List is received
        on<WorldLoadEvent> {
            val mc = Melinoe.mc
            val level = mc.level ?: return@on
            currentCharacterDimension = level.dimension().identifier().path
            currentCharacterWorld = "" // Invalidate to prevent sending incorrect data
        }
        
        // Keep tick handler only for portal countdown (client-side timer) and dimension tracking
        on<TickEvent.End> {
            // Track dimension changes for dungeon chains
            val mc = Melinoe.mc
            val level = mc.level
            if (level != null) {
                val newDimension = level.dimension().identifier().path
                if (newDimension != currentCharacterDimension) {
                    val previousDimension = currentCharacterDimension
                    currentCharacterDimension = newDimension
                    onDimensionChanged(previousDimension, newDimension)
                }
            }
            
            // Handle portal countdown
            if (portalCountdownTicks > 0) {
                portalCountdownTicks--
                currentPortalCallTime = max(0, (portalCountdownTicks + 19) / 20)
                if (portalCountdownTicks <= 0) {
                    currentPortalCall = ""
                    countdownLock = false
                }
            } else if (currentPortalCallTime > 0) {
                currentPortalCallTime = 0
            }
        }
    }

    /**
     * Update character info from tab list.
     * Called immediately when tab list packets arrive.
     */
    private fun updateCharacterInfo() {
        // Parse tab list once and extract all values
        val tabData = TabListUtils.parseTabList()
        
        val info = tabData.charInfo ?: run {
            Melinoe.logger.debug("LocalAPI: No char info from tab list")
            return
        }
        
        val charInfo = info.split(" ")
        if (charInfo.size < 4) {
            Melinoe.logger.debug("LocalAPI: Char info size < 4: ${charInfo.size}")
            return
        }
        
        Melinoe.logger.debug("LocalAPI: Updating with char info: $info")

        // Parse character type from format: "(MASTERY)(GAMEMODE) (LEVEL) (CLASS)"
        // Gives specific colors for group ironmans - utilized for the DiscordRPC Module
        currentCharacterType = when (charInfo[0].drop(2).hashCode()) {
            880 -> "normal"
            881 -> "hardcore_ironman"
            1771714 -> "black"
            1771715 -> "blue"
            1771716 -> "brown"
            1771717 -> "gray"
            1771718 -> "green"
            1771719 -> "light_blue"
            1771720 -> "light_brown"
            1771721 -> "light_green"
            1771722 -> "light_orange"
            1771723 -> "light_pink"
            1771724 -> "light_purple"
            1771725 -> "light_red"
            1771726 -> "light_yellow"
            1771727 -> "orange"
            1771728 -> "pink"
            1771729 -> "purple"
            1771730 -> "red"
            1771731 -> "yellow"
            1771573 -> "ghardcore_ironman"
            1771574 -> "gnormal"
            1771575 -> "gseasonal"
            1771576 -> "gblack"
            1771577 -> "gblue"
            1771578 -> "gbrown"
            1771579 -> "ggray"
            1771580 -> "ggreen"
            1771581 -> "glight_blue"
            1771582 -> "glight_brown"
            1771583 -> "glight_green"
            1771584 -> "glight_orange"
            1771585 -> "glight_pink"
            1771586 -> "glight_purple"
            1771587 -> "glight_red"
            1771588 -> "glight_yellow"
            1771589 -> "gorange"
            1771590 -> "gpink"
            1771591 -> "gpurple"
            1771592 -> "gred"
            1771593 -> "gyellow"
            else -> "unknown"
        }

        try {
            currentCharacterLevel = charInfo[2].toInt()
            currentCharacterClass = charInfo[3]
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to parse character info", e)
        }
        
        // Update realm/world from tab list (Server: line)
        val realm = tabData.server
        if (realm != null) {
            val cleanRealm = realm.replace(Regex("[\\[\\]]+"), "")
            if (cleanRealm != currentCharacterWorld) {
                currentCharacterWorld = cleanRealm
                checkLocationChange()
            }
        }
    }
    
    private fun checkLocationChange() {
        val currentDim = currentCharacterDimension
        val currentWorld = currentCharacterWorld
        
        if (currentDim.isEmpty() || currentWorld.isEmpty()) return
        
        if (lastFiredDimension.isEmpty()) {
            lastFiredDimension = currentDim
            lastFiredWorld = currentWorld
            
            me.melinoe.network.ModWebSocket.sendLocationUpdate(currentWorld)
            
            if (currentDim == "realm") {
                EventBus.post(HubToRealmEvent("Login", currentWorld))
            }
            return
        }
        
        if (currentDim == lastFiredDimension && currentWorld == lastFiredWorld) return
        
        val wasHub = lastFiredDimension == "hub" || lastFiredDimension == "daily"
        val isHub = currentDim == "hub" || currentDim == "daily"
        val wasRealm = lastFiredDimension == "realm"
        val isRealm = currentDim == "realm"

        // Track realm history (realms only) for the "Previous Location" button
        if (isRealm && currentWorld != lastRealm) {
            if (lastRealm.isNotEmpty()) {
                previousRealm = lastRealm
            }
            lastRealm = currentWorld
        }

        if (wasHub && isRealm) {
            EventBus.post(HubToRealmEvent(lastFiredWorld, currentWorld))
        } else if (wasRealm && isHub) {
            EventBus.post(RealmToHubEvent(lastFiredWorld, currentWorld))
        } else if (wasRealm && isRealm && currentWorld != lastFiredWorld) {
            EventBus.post(RealmToRealmEvent(lastFiredWorld, currentWorld))
        }
        
        lastFiredDimension = currentDim
        lastFiredWorld = currentWorld
        
        me.melinoe.network.ModWebSocket.sendLocationUpdate(currentWorld)
    }
    
    /**
     * Update character area from boss bars.
     * Now called only when boss bars change via BossBarUpdateEvent.
     */
    fun updateCharacterArea(bossBarMap: Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent>) {
        if (bossBarMap.isEmpty()) return
        
        val bossBars = bossBarMap.values.toList()
        
        // Only process if we have exactly 5 boss bars
        if (bossBars.size != 5) {
            currentCharacterFighting = ""
            Melinoe.logger.debug("LocalAPI: Boss bar count is ${bossBars.size}, expected 5 - skipping area parsing")
            return
        }
        
        // Find the area bar (contains letters after stripping formatting)
        val areaBar = bossBars.firstOrNull { bar ->
            val stripped = bar.name.string
                .noControlCodes // Remove color codes (more efficient)
                .replace(Regex("[^\\p{ASCII}]"), "") // Remove non-ASCII characters
            stripped.isNotEmpty() && stripped.any { it.isLetter() }
        }
        
        if (areaBar == null) {
            currentCharacterFighting = ""
            Melinoe.logger.debug("LocalAPI: No boss bar found with area name")
            return
        }
        
        val rawAreaName = areaBar.name.string
        
        // Strip formatting codes (§x) and non-ASCII characters (like Unicode icons)
        val area = rawAreaName
            .noControlCodes // Remove color codes (more efficient)
            .replace(Regex("[^\\p{ASCII}]"), "") // Remove non-ASCII characters (Unicode icons)
        
        val newArea = area.replace(Regex("[^a-zA-z ']+"), "") // Remove numbers at the end
        
        // Check if area changed and fire dungeon events
        if (newArea != currentCharacterArea) {
            val previousArea = currentCharacterArea
            currentCharacterArea = newArea
            onAreaChanged(previousArea, newArea)
        }
        
        // Find the boss bar (not the area bar, and not empty)
        // The boss bar has a Unicode icon but no ASCII letters
        val bossBar = bossBars.firstOrNull { bar ->
            if (bar == areaBar) return@firstOrNull false // Skip the area bar
            val rawName = bar.name.string
            if (rawName.isEmpty()) return@firstOrNull false // Skip empty bars
            
            // Boss bar has Unicode icon but no ASCII letters
            val stripped = rawName.replace(Regex("[^\\p{ASCII}]"), "")
            val hasLetters = stripped.any { it.isLetter() }
            
            // Boss bar should have content but no letters after stripping non-ASCII
            rawName.isNotEmpty() && !hasLetters
        }
            ?: bossBars.firstOrNull { it != areaBar && it.name.string.isNotEmpty() } // Fallback: any non-area, non-empty bar
        
        if (bossBar == null) {
            // Check portal countdown if we just finished a boss
            if (currentCharacterFighting.isNotEmpty() && lastKnownBoss.isNotEmpty()) {
                currentPortalCall = when (lastKnownBoss) {
                    "Chungus" -> "void"
                    "Illarius" -> "loa"
                    "Astaroth" -> "shatters"
                    "Glumi" -> "fungal"
                    "Lotil" -> "omni"
                    "Tidol" -> "corsairs"
                    "Valus" -> "cultists"
                    "Oozul" -> "chronos"
                    "Freddy" -> "pizza"
                    "Anubis" -> "alair"
                    "Defender" -> "cprov"
                    else -> ""
                }
                if (currentPortalCall.isNotEmpty() && !countdownLock) {
                    startPortalCountdown()
                }
            }
            currentCharacterFighting = ""
            Melinoe.logger.debug("LocalAPI: No boss bar found")
            return
        }
        
        val currentBossHash = bossBar.name.hashCode()
        currentCharacterFighting = getBossNameFromHash(currentBossHash)
        
        if (lastKnownBossHash != currentBossHash) {
            // Check if this is an unknown boss
            if (currentCharacterFighting.isEmpty()) {
                Melinoe.logger.warn("═══════════════════════════════════════════════════════")
                Melinoe.logger.warn("Please report this to the developers:")
                Melinoe.logger.warn("")
                Melinoe.logger.warn("Boss Hash: $currentBossHash")
                Melinoe.logger.warn("Area: $currentCharacterArea")
                Melinoe.logger.warn("")
                Melinoe.logger.warn("Copy the above info and send it to:")
                Melinoe.logger.warn("• Discord: https://discord.gg/Nxhmxjt3kR")
                Melinoe.logger.warn("• GitHub: https://github.com/House-Hades/Melinoe/issues")
                Melinoe.logger.warn("═══════════════════════════════════════════════════════")
            } else {
                Melinoe.logger.info("Boss detected: $currentCharacterFighting (hash: $currentBossHash) in area: $currentCharacterArea")
            }
            
            lastKnownBoss = currentCharacterFighting
            lastKnownBossHash = currentBossHash
        }
    }

    /**
     * Resolves a boss name from the hash of its boss-bar name [net.minecraft.network.chat.Component]
     * Returns an empty string if the hash does not correspond to a known boss
     *
     * Hashes are loaded from bosses.json via [me.melinoe.utils.data.TelosData]
     */
    fun getBossNameFromHash(hash: Int): String = BossData.findByHash(hash)?.label ?: ""

    /**
     * Start the portal countdown timer.
     */
    private fun startPortalCountdown() {
        if (countdownLock) return
        
        countdownLock = true
        portalCountdownTicks = 32 * 20 // 32 seconds
        currentPortalCallTime = 32
    }

    /**
     * Called when the character area changes.
     * Fires dungeon entry/exit/change events.
     */
    private fun onAreaChanged(previousArea: String, newArea: String) {
        Melinoe.logger.info("LocalAPI: Area changed from '$previousArea' to '$newArea'")
        
        val previousDungeon = DungeonData.findByKey(previousArea)
        val currentDungeon = DungeonData.findByKey(newArea)
        
        Melinoe.logger.info("LocalAPI: Previous dungeon: ${previousDungeon?.areaName ?: "none"}, Current dungeon: ${currentDungeon?.areaName ?: "none"}")
        
        // Dungeon entry
        if (currentDungeon != null && previousDungeon == null) {
            Melinoe.logger.info("LocalAPI: Firing DungeonEntryEvent for ${currentDungeon.areaName}")
            EventBus.post(DungeonEntryEvent(currentDungeon))
        }
        // Dungeon exit
        else if (currentDungeon == null && previousDungeon != null) {
            Melinoe.logger.info("LocalAPI: Firing DungeonExitEvent for ${previousDungeon.areaName}")
            EventBus.post(DungeonExitEvent(previousDungeon))
        }
        // Dungeon change (chains)
        else if (currentDungeon != null && previousDungeon != null && currentDungeon != previousDungeon) {
            Melinoe.logger.info("LocalAPI: Firing DungeonChangeEvent from ${previousDungeon.areaName} to ${currentDungeon.areaName}")
            EventBus.post(DungeonChangeEvent(previousDungeon, currentDungeon))
        }
    }

    /**
     * Called when the dimension changes.
     * Fires dungeon chain events when area stays the same but dimension changes (e.g., telos:dungeon -> telos:dungeon/1).
     * Skips Rustborn Kingdom as it's a split dungeon, not a chain.
     */
    private fun onDimensionChanged(previousDimension: String, newDimension: String) {
        // Only care about dungeon dimension changes (dungeon -> dungeon/1, dungeon/1 -> dungeon/2, etc.)
        if (!previousDimension.startsWith("dungeon") || !newDimension.startsWith("dungeon")) {
            return
        }
        
        // If dimension changed but area stayed the same, this is a dungeon chain
        val currentDungeon = DungeonData.findByKey(currentCharacterArea)
        
        if (currentDungeon != null) {
            // Skip Rustborn Kingdom - it's a split dungeon, not a chain
            if (currentDungeon.name == "RUSTBORN_KINGDOM") {
                ArmorCooldownsModule.reset()
                Melinoe.logger.info("LocalAPI: Dimension changed in Rustborn Kingdom (split dungeon), skipping chain event")
                return
            }
            
            Melinoe.logger.info("LocalAPI: Dimension changed from '$previousDimension' to '$newDimension' in ${currentDungeon.areaName} - firing DungeonChangeEvent (chain)")
            // Fire a chain event - same dungeon to same dungeon (represents continuing the chain)
            EventBus.post(DungeonChangeEvent(currentDungeon, currentDungeon))
        }
    }
    
    /**
     * Check if player is currently in a dungeon.
     * @return true if player is in a dungeon, false otherwise
     */
    fun isInDungeon(): Boolean {
        return try {
            val currentDungeon = DungeonData.findByKey(getCurrentCharacterArea())
            currentDungeon != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if player is currently in the Nexus.
     * @return true if player is in the Nexus, false otherwise
     */
    fun isInNexus(): Boolean {
        return try {
            getCurrentCharacterArea() == "The Nexus"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if player is currently in the Realm.
     * @return true if player is in the realm, false otherwise
     */
    fun isInRealm(): Boolean {
        return try {
            !isInDungeon() && !isInNexus()
        } catch (e: Exception) {
            false
        }
    }

    // Getters
    fun getCurrentCharacterType(): String = currentCharacterType
    fun getCurrentCharacterClass(): String = currentCharacterClass
    fun getCurrentCharacterLevel(): Int = currentCharacterLevel
    fun getCurrentCharacterWorld(): String = currentCharacterWorld

    /**
     * Returns the realm the player was on before their current location.
     * If the player is currently in a realm, this is the realm visited before it
     * If the player is elsewhere (hub/dungeon), this is the most recent realm
     * Returns an empty string if no prior realm is known
     */
    fun getPreviousRealm(): String =
        if (currentCharacterWorld == lastRealm) previousRealm else lastRealm
    fun getCurrentCharacterFighting(): String = currentCharacterFighting
    fun getCurrentCharacterArea(): String = currentCharacterArea
    fun getCurrentPortalCall(): String = currentPortalCall
    fun getCurrentPortalCallTime(): Int = currentPortalCallTime

    /**
     * Shutdown LocalAPI and reset all state.
     */
    fun shutdown() {
        currentCharacterType = ""
        currentCharacterClass = ""
        currentCharacterLevel = -1
        currentCharacterWorld = ""
        currentCharacterArea = ""
        currentCharacterDimension = ""
        currentCharacterFighting = ""
        lastKnownBoss = ""
        lastKnownBossHash = 0
        countdownLock = false
        currentPortalCall = ""
        currentPortalCallTime = 0
        portalCountdownTicks = 0
        lastFiredDimension = ""
        lastFiredWorld = ""
        lastRealm = ""
        previousRealm = ""
    }
}