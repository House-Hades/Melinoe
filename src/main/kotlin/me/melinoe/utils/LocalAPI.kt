package me.melinoe.utils

import me.melinoe.Melinoe
import me.melinoe.events.BossBarUpdateEvent
import me.melinoe.events.DungeonChangeEvent
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.PacketEvent
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.events.core.onReceive
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
    private var initialHash = 0
    private var initialHashSet = false
    
    private var portalCountdownTicks = 0
    private var initialized = false

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
        currentCharacterType = when (charInfo[0].substring(2).hashCode()) {
            880 -> "Normal"
            881 -> "Hardcore"
            882 -> "Seasonal"
            else -> "GHardcore" // 1771717 -> 1771734 inclusive
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
            currentCharacterWorld = realm.replace(Regex("[\\[\\]]+"), "")
        }
    }

    /**
     * Update character area from boss bars.
     * Now called only when boss bars change via BossBarUpdateEvent.
     */
    private fun updateCharacterArea(bossBarMap: Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent>) {
        if (bossBarMap.isEmpty()) return
        
        val bossBars = bossBarMap.values.toList()
        
        // Only process if we have exactly 5 boss bars
        if (bossBars.size != 5) {
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
        } ?: bossBars.firstOrNull { it != areaBar && it.name.string.isNotEmpty() } // Fallback: any non-area, non-empty bar
        
        if (bossBar == null) {
            Melinoe.logger.debug("LocalAPI: No boss bar found")
            return
        }
        
        if (!initialHashSet) {
            initialHash = bossBar.name.hashCode() // This is the trash hash we can ignore
            initialHashSet = true // Once player loads onto the server we know the default hash of the empty bossbar
            Melinoe.logger.info("Initial Boss hash set! and hash is: $initialHash")
        }

        val currentBossHash = bossBar.name.hashCode()

        // All updated as of 21th January 2026
        currentCharacterFighting = when (currentBossHash) {
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
            1824190226 -> "Shadowflare"
            -1382454635 -> "Loa"
            -132746136 -> "Valerion"
            -829226362 -> "Nebula"
            -132585649 -> "Ophanim"
            -708336010 -> "Prismara"
            -1254007688 -> "Omnipotent"
            -1621744702 -> "Thalassar"
            -1643392642 -> "Silex"
            290925398 -> "Chronos"
            -422985676 -> "Golden Freddy"
            -342534076 -> "Kurvaros"
            -1370656917 -> "Warden"
            -1370655956 -> "Herald"
            -1370654995 -> "Reaper"
            -1370654034 -> "Defender"
            -1622067598 -> "Asmodeus"
            -1643406096 -> "Seraphim"
            -1643245609 -> "True Seraph"
            -132915272 -> "True Ophan"
            2131893865 -> "Raphael's Castle"
            254038329 -> "Raphael"
            230903377 -> "Sylvaris"
            -1253581965 -> "Voided Omnipotent"
            1301379752 -> "Unrest"
            -828991878 -> "Aetheris"
            1420701227 -> "Malthar"
            else -> ""
        }

        // Improved system to find HashCodes
        // This can honestly be kept in if needed, it does not spam logs like before very useful to get Hash's
        // If the initial hash is known and the player is on an actual boss
        if (initialHash != currentBossHash && lastKnownBossHash != currentBossHash) {
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
                Melinoe.logger.warn("• Discord: https://discord.gg/melinoe")
                Melinoe.logger.warn("• GitHub: https://github.com/NoWayItzJoey/Melinoe/issues")
                Melinoe.logger.warn("═══════════════════════════════════════════════════════")
            } else {
                Melinoe.logger.info("Boss detected: $currentCharacterFighting (hash: $currentBossHash) in area: $currentCharacterArea")
            }
            
            lastKnownBoss = currentCharacterFighting
            lastKnownBossHash = currentBossHash
        }

        // This means a boss has died recently
        if (lastKnownBoss.isNotEmpty() && currentBossHash == initialHash) {
            // We are making sure there is a last known boss and that there is no current boss
            // It's not guaranteed that the player even killed the boss or just moved away
            // As it is rn, this is called every tick if a boss died or even if you move away from a boss
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

            if (currentPortalCall.isEmpty() || countdownLock) {
                return
            }
            // A boss portal has dropped, start timer
            startPortalCountdown()
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
            me.melinoe.events.core.EventBus.post(DungeonEntryEvent(currentDungeon))
        }
        // Dungeon exit
        else if (currentDungeon == null && previousDungeon != null) {
            Melinoe.logger.info("LocalAPI: Firing DungeonExitEvent for ${previousDungeon.areaName}")
            me.melinoe.events.core.EventBus.post(DungeonExitEvent(previousDungeon))
        }
        // Dungeon change (chains)
        else if (currentDungeon != null && previousDungeon != null && currentDungeon != previousDungeon) {
            Melinoe.logger.info("LocalAPI: Firing DungeonChangeEvent from ${previousDungeon.areaName} to ${currentDungeon.areaName}")
            me.melinoe.events.core.EventBus.post(DungeonChangeEvent(previousDungeon, currentDungeon))
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
                Melinoe.logger.info("LocalAPI: Dimension changed in Rustborn Kingdom (split dungeon), skipping chain event")
                return
            }
            
            Melinoe.logger.info("LocalAPI: Dimension changed from '$previousDimension' to '$newDimension' in ${currentDungeon.areaName} - firing DungeonChangeEvent (chain)")
            // Fire a chain event - same dungeon to same dungeon (represents continuing the chain)
            me.melinoe.events.core.EventBus.post(DungeonChangeEvent(currentDungeon, currentDungeon))
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
        initialHash = 0
        initialHashSet = false
        portalCountdownTicks = 0
    }
}
