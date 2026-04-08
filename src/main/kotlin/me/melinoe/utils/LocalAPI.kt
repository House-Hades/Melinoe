package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.events.*
import me.melinoe.events.core.EventBus
import me.melinoe.events.core.on
import me.melinoe.events.core.onReceive
import me.melinoe.features.impl.combat.NaturesGiftModule
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
            currentCharacterDimension = level.dimension().location().path
            currentCharacterWorld = "" // Invalidate to prevent sending incorrect data
        }
        
        // Keep tick handler only for portal countdown (client-side timer) and dimension tracking
        on<TickEvent.End> {
            // Track dimension changes for dungeon chains
            val mc = Melinoe.mc
            val level = mc.level
            if (level != null) {
                val newDimension = level.dimension().location().path
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
        currentCharacterType = when (charInfo[0].substring(2).hashCode()) {
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
        
        // All updated as of 21th January 2026
        currentCharacterFighting = when (currentBossHash) {
            // Lowlands
            -167372549 -> "Eddie"
            -1381648356 -> "Zhum"
            -1659173216 -> "Drayruk"
            -973820290 -> "Miraj"
            230865898 -> "Khufu"
            -167370627 -> "Choji"
            291455870 -> "Flora"
            
            // Centre
            -1019671711 -> "Malfas"
            -1659169372 -> "Heptavius"
            -1659167450 -> "Arctic Colossus"
            -1659168411 -> "Frostgaze"
            -1659166489 -> "Magnus"
            -1253198526 -> "Pyro"
            -1621485232 -> "Thalor"
            -167373510 -> "Ashenclaw"
            832610318 -> "Corvack"
            
            // Realm Bosses
            -168181711 -> "Chungus"
            1368623635 -> "Illarius"
            -1253632898 -> "Astaroth"
            -168176906 -> "Glumi"
            -1254008649 -> "Lotil"
            1368934038 -> "Tidol"
            -1622056066 -> "Valus"
            -1907114029 -> "Oozul"
            -1343349613 -> "Freddy"
            -342545608 -> "Anubis"
            -1240191621 -> "Hollowbane"
            -1048713371 -> "Claus"
            
            // High
            -1254007688 -> "Omnipotent"
            -708336010 -> "Prismara"
            -1621744702 -> "Thalassar"
            -422985676 -> "Golden Freddy"
            290925398 -> "Chronos"
            -342534076 -> "Kurvaros"
            1420701227 -> "Malthar"
            -1643392642 -> "Silex"
            -1382454635 -> "Loa"
            -828991878 -> "Aetheris"
            1824190226 -> "Solarflare"
            2131893865 -> "Orion & Osiris"
            254038329 -> "Raphael"
            -1253581965 -> "Voided Omnipotent"
            -132746136 -> "Valerion"
            -707883379 -> "Mithrion"
            -829226362 -> "Nebula"
            -132585649 -> "Ophanim"
            -132915272 -> "True Ophan"
            
            // Shadowlands
            -1370656917 -> "Warden"
            -1370655956 -> "Herald"
            -1370654995 -> "Reaper"
            230903377 -> "Sylvaris"
            1301379752 -> "Unrest"
            -1370654034 -> "Defender"
            -1622067598 -> "Asmodeus"
            -1643406096 -> "Seraphim"
            -1643245609 -> "True Seraph"
            else -> ""
        }
        
        // Improved system to find HashCodes
        // This can honestly be kept in if needed, it does not spam logs like before very useful to get Hash's
        // If the initial hash is known and the player is on an actual boss
        if (lastKnownBossHash != currentBossHash) {
            // Comparing Hash cause they are unique, else if we fight two unknown bosses back to back it won't print
            
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
            if (currentDungeon == DungeonData.RUSTBORN_KINGDOM) {
                NaturesGiftModule.resetCooldown()
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
    }
}