package me.melinoe.features.impl.misc

import dev.firstdark.rpc.DiscordRpc
import dev.firstdark.rpc.enums.ActivityType
import dev.firstdark.rpc.enums.ErrorCode
import dev.firstdark.rpc.exceptions.UnsupportedOsType
import dev.firstdark.rpc.handlers.RPCEventHandler
import dev.firstdark.rpc.models.DiscordRichPresence
import dev.firstdark.rpc.models.User
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.StringSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.BossBarUtils
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.DungeonData
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Discord RPC Module - broadcasts game presence
 */
object DiscordRPCModule : Module(
    name = "Discord RPC",
    category = Category.MISC,
    description = "Displays your current game status on Discord."
) {
    private val showCharacter by BooleanSetting("Show Character", true, desc = "Displays your character information")
    private val showLocation by BooleanSetting("Show Location", true, desc = "Displays your current world")
    private val showDungeon by BooleanSetting("Show Dungeon", true, desc = "Displays your current area or dungeon")
    private val showCustom by BooleanSetting("Show Custom", false, desc = "Displays custom text")
    private val customText by StringSetting("Custom Text", "Vibing...", desc = "The custom text to display")

    private const val APPLICATION_ID = "1469756563811209338"
    private var scheduler: ScheduledExecutorService? = null
    private var rpc: DiscordRpc? = null

    private var startTime = 0L
    private var initialized = false
    private var hasConnectedBefore = false // Tracks if they have played yet during this session

    override fun onEnable() {
        if (startTime == 0L) {
            startTime = System.currentTimeMillis() / 1000
        }

        if (!initialized) {
            initRpc()
        }

        if (scheduler == null || scheduler?.isShutdown == true) {
            scheduler = Executors.newSingleThreadScheduledExecutor()
            scheduler?.scheduleAtFixedRate(::updatePresence, 0, 5, TimeUnit.SECONDS)
        }
    }

    override fun onDisable() {
        stopRpc()
    }

    private fun stopRpc() {
        initialized = false
        startTime = 0L
        hasConnectedBefore = false

        scheduler?.shutdownNow()
        scheduler = null

        rpc?.shutdown()
        rpc = null
    }

    private fun initRpc() {
        rpc = DiscordRpc()

        val handler = object : RPCEventHandler() {
            override fun ready(user: User) {
                val detailsText = if (hasConnectedBefore) "Deciding whether to play..." else "Logging onto Telos"
                val presence = DiscordRichPresence.builder()
                    .name("Telos Realms")
                    .startTimestamp(startTime)
                    .details(detailsText)
                    .smallImageKey("small")
                    .smallImageText("Running on Melinoe!")
                    .largeImageKey("large")
                    .activityType(ActivityType.PLAYING)
                    .button(DiscordRichPresence.RPCButton.of("Play Telos", "https://discord.gg/telosmc"))
                    .button(DiscordRichPresence.RPCButton.of("Use Melinoe", "https://discord.gg/Nxhmxjt3kR"))
                    .build()

                rpc?.updatePresence(presence)
            }

            override fun disconnected(errorCode: ErrorCode, message: String?) {
                Melinoe.logger.debug("Discord RPC: Disconnect ({} - {})", errorCode, message)
            }

            override fun errored(errorCode: ErrorCode, message: String?) {
                Melinoe.logger.error("Discord RPC: $errorCode - $message")
            }
        }

        try {
            rpc?.init(APPLICATION_ID, handler, false)
        } catch (e: UnsupportedOsType) {
            Melinoe.logger.error("Discord RPC: Unsupported OS")
        }

        initialized = true
    }

    private fun updatePresence() {
        val onTelos = ServerUtils.isOnTelos() && mc.player != null

        if (onTelos) {
            hasConnectedBefore = true
        }

        if (!onTelos) {
            try {
                val detailsText = if (hasConnectedBefore) "Deciding whether to play..." else "Logging onto Telos"
                val presence = DiscordRichPresence.builder()
                    .name("Telos Realms")
                    .startTimestamp(startTime)
                    .details(detailsText)
                    .smallImageKey("small")
                    .smallImageText("Running on Melinoe!")
                    .largeImageKey("large")
                    .activityType(ActivityType.PLAYING)
                    .button(DiscordRichPresence.RPCButton.of("Play Telos", "https://discord.gg/telosmc"))
                    .button(DiscordRichPresence.RPCButton.of("Use Melinoe", "https://discord.gg/Nxhmxjt3kR"))
                    .build()

                rpc?.updatePresence(presence)
            } catch (e: Exception) {
                // Ignore silent RPC updates failures
            }
            return
        }

        try {
            val firstLineParts = mutableListOf<String>()
            val fightingBoss = LocalAPI.getCurrentCharacterFighting()
            val area = LocalAPI.getCurrentCharacterArea()

            if (showLocation) {
                val world = LocalAPI.getCurrentCharacterWorld()
                if (world.isNotBlank() && !world.equals("null", ignoreCase = true)) {
                    firstLineParts.add(world)
                }
            }

            if (showDungeon) {
                if (area.isNotBlank() && !area.equals("null", ignoreCase = true)) {
                    firstLineParts.add(area)
                }
            }

            if (showCustom && showDungeon && customText.isNotBlank()) {
                firstLineParts.add(customText)
            }

            val firstLine = if (firstLineParts.isEmpty()) "Exploring the realm..." else firstLineParts.joinToString(" | ")
            var secondLine = ""

            if (showDungeon) {
                secondLine = if (BossBarUtils.isInDungeon()) {
                    val bossName = DungeonData.findByKey(area)?.finalBoss?.label
                    bossName?.let { "Fighting $it" } ?: "In an unknown place"
                } else if (BossBarUtils.hasActiveBossBar()) {
                    // Use instantly accurate data for World Bosses if they have an active boss bar on screen
                    val activeBoss = BossBarUtils.getBossBarMap().values.firstOrNull { it.progress > 0.0f }?.name?.string
                    activeBoss?.let { "Fighting $it" } ?: "Fighting an unknown boss"
                } else if (fightingBoss.isNotBlank() && fightingBoss.lowercase() !in listOf("null", "none")) {
                    // Fallback to local API cache if we aren't picking up a direct boss bar
                    "Fighting $fightingBoss"
                } else {
                    when (area) {
                        "The Nexus" -> "Talking with Wumpus..."
                        "Beach" -> "Battling pirates..."
                        "Jungle" -> "Climbing the trees..."
                        "Swamp" -> "Finding mushrooms..."
                        "Desert" -> "Searching for water..."
                        "Crimson Forest" -> "Burning their feet..."
                        "Permafrost" -> "Freezing their nose off..."
                        "Necropolis" -> "Talking to spirits..."
                        "Shadowlands" -> "Scaring shadows..."
                        "Radiant Isles" -> "Feeling radiance..."
                        else -> ""
                    }
                }
            } else if (showCustom && !customText.isNullOrBlank()) {
                secondLine = customText
            }

            var characterText = "Running on Melinoe!"
            val characterType = LocalAPI.getCurrentCharacterType()

            if (showCharacter && !characterType.isNullOrBlank() && !characterType.equals("null", ignoreCase = true)) {
                val characterTypeName = when (characterType.lowercase()) {
                    "normal", "gnormal" -> "Normal"
                    "seasonal", "gseasonal" -> "Seasonal"
                    "hardcore_ironman", "ghardcore_ironman" -> "Hardcore Ironman"
                    else -> "Group Hardcore Ironman"
                }

                val rawClass = LocalAPI.getCurrentCharacterClass()
                val charClass = if (rawClass.isNullOrBlank() || rawClass.equals("null", ignoreCase = true)) "Unknown" else rawClass
                val level = LocalAPI.getCurrentCharacterLevel()

                characterText = "Playing on a Level $level $characterTypeName $charClass"
            }

            val smallImage = if (showCharacter && !characterType.isNullOrBlank() && !characterType.equals("null", ignoreCase = true)) characterType else "small"

            val presence = DiscordRichPresence.builder()
                .name("Telos Realms")
                .startTimestamp(startTime)
                .details(firstLine)
                .state(secondLine)
                .largeImageKey("large")
                .smallImageKey(smallImage)
                .smallImageText(characterText)
                .activityType(ActivityType.PLAYING)
                .button(DiscordRichPresence.RPCButton.of("Play Telos", "https://discord.gg/telosmc"))
                .button(DiscordRichPresence.RPCButton.of("Use Melinoe", "https://discord.gg/Nxhmxjt3kR"))
                .build()

            rpc?.updatePresence(presence)
        } catch (e: Exception) {
            // Ignore silent execution failures
        }
    }
}