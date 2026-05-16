package me.melinoe.features.impl.visual

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.RenderEvent
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.createSoundSettings
import me.melinoe.utils.emoji.EmojiReplacer
import me.melinoe.utils.equalsOneOf
import me.melinoe.utils.playSoundAtPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object HealthIndicatorModule : Module(
    name = "Health Indicator",
    category = Category.VISUAL,
    description = "Displays warnings and sounds when health is low."
) {
    private data class HealthWarningState(
        var ticks: Int = 0,
        var startTime: Long = 0L,
        var triggered: Boolean = false,
        var lastSoundTime: Long = 0L
    )
    
    private val lowHpState = HealthWarningState()
    private val midHpState = HealthWarningState()
    private var wasInLowHp = false
    
    private var lastTickCount = 0
    
    // Grace Period State
    private var isInGracePeriod = false
    private var gracePeriodEndTime = 0L
    private var previousRealm = ""
    private var previousDimension = ""
    
    // Main Mode Settings
    private val warningMode = registerSetting(SelectorSetting("Warning Mode", "Continuous", listOf("Continuous", "Periodic"), "Continuous loops, Periodic plays once for a duration"))
    private val periodicDuration by NumberSetting("Periodic Duration", 1.0, 0.5, 3.0, 0.5, "Duration in seconds for periodic mode").withDependency { warningMode.selected == "Periodic" }
    
    // Low Health Warning
    val enableLowHealth by BooleanSetting("Enable Low HP Warning", true, "Enable low health warning")
    val lowHealthThreshold by NumberSetting("Low HP Threshold %", 35.0, 0.0, 100.0, 1.0, "Health percentage to trigger low HP warning").withDependency { enableLowHealth }
    
    private val lowHealthHud by HUDSetting(
        name = "Low HP Title",
        x = 400,
        y = 250,
        scale = 2f,
        toggleable = true,
        module = this,
        description = "Enable and position the low health title"
    ) { example ->
        val player = mc.player
        if (!example) {
            if (player == null || isInGracePeriod) return@HUDSetting 0 to 0
            val healthPercent = (player.health / player.maxHealth) * 100f
            if (healthPercent > lowHealthThreshold) return@HUDSetting 0 to 0
            
            // Check periodic mode
            if (warningMode.selected == "Periodic") {
                val currentTime = System.currentTimeMillis()
                val elapsed = (currentTime - lowHpState.startTime) / 1000.0
                if (elapsed > periodicDuration) return@HUDSetting 0 to 0
            }
        }
        
        val baseColor = lowHealthColor.rgba
        val displayColor = if (titleFlashing && isFlashPhase()) lightenColor(baseColor, 0.3f) else baseColor
        
        val component = Component.literal(lowHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        val displayComponent = EmojiReplacer.replaceIn(component)
        val textWidth = mc.font.width(displayComponent)
        text(mc.font, displayComponent, 0, 0, displayColor, true)
        textWidth to mc.font.lineHeight
    }.withDependency { enableLowHealth }
    
    // Medium Health Warning
    val enableMediumHealth by BooleanSetting("Enable Mid HP Warning", true, "Enable medium health warning")
    val mediumHealthThreshold by NumberSetting("Mid HP Threshold %", 50.0, 0.0, 100.0, 1.0, "Health percentage to trigger medium HP warning").withDependency { enableMediumHealth }
    
    private val mediumHealthHud by HUDSetting(
        name = "Mid HP Title",
        x = 400,
        y = 280,
        scale = 2f,
        toggleable = true,
        module = this,
        description = "Enable and position the medium health title"
    ) { example ->
        val player = mc.player
        if (!example) {
            if (player == null || isInGracePeriod) return@HUDSetting 0 to 0
            val healthPercent = (player.health / player.maxHealth) * 100f
            // Hide mid HP title if low HP title is active (priority)
            if (healthPercent > mediumHealthThreshold || (enableLowHealth && healthPercent <= lowHealthThreshold)) return@HUDSetting 0 to 0
            
            // Check periodic mode
            if (warningMode.selected == "Periodic") {
                val currentTime = System.currentTimeMillis()
                val elapsed = (currentTime - midHpState.startTime) / 1000.0
                if (elapsed > periodicDuration) return@HUDSetting 0 to 0
            }
        }
        
        val baseColor = mediumHealthColor.rgba
        val displayColor = if (titleFlashing && isFlashPhase()) lightenColor(baseColor, 0.3f) else baseColor
        
        val component = Component.literal(mediumHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        val displayComponent = EmojiReplacer.replaceIn(component)
        val textWidth = mc.font.width(displayComponent)
        text(mc.font, displayComponent, 0, 0, displayColor, true)
        textWidth to mc.font.lineHeight
    }.withDependency { enableMediumHealth }
    
    // Title Settings
    private val titleSettingsDropdown by DropdownSetting("Title Settings", false)
    private val lowHealthText by StringSetting("Low HP Text", "LOW HEALTH!", desc = "Text to display for low health").withDependency { titleSettingsDropdown }
    private val lowHealthColor by ColorSetting("Low HP Color", me.melinoe.utils.Color(0xFFFF0000.toInt()), desc = "Color of the low health title").withDependency { titleSettingsDropdown }
    private val mediumHealthText by StringSetting("Mid HP Text", "MEDIUM HEALTH", desc = "Text to display for medium health").withDependency { titleSettingsDropdown }
    private val mediumHealthColor by ColorSetting("Mid HP Color", me.melinoe.utils.Color(0xFFFFAA00.toInt()), desc = "Color of the medium health title").withDependency { titleSettingsDropdown }
    private val titleFlashing by BooleanSetting("Text Flashing", true, "Flash the warning text").withDependency { titleSettingsDropdown }
    
    // Sound Settings
    private val soundSettingsDropdown by DropdownSetting("Sound Settings", false)
    
    private val lowHealthSoundSettings = createSoundSettings("Low HP Sound", "entity.experience_orb.pickup", { soundSettingsDropdown }, "Play Low HP Sound")
    
    private val mediumHealthSoundSettings = createSoundSettings("Mid HP Sound", "block.note_block.pling", { soundSettingsDropdown }, "Play Mid HP Sound")
    
    init {
        on<WorldLoadEvent> {
            lowHpState.apply { ticks = 0; startTime = 0L; triggered = false; lastSoundTime = 0L }
            midHpState.apply { ticks = 0; startTime = 0L; triggered = false; lastSoundTime = 0L }
            wasInLowHp = false
            handleWorldChange()
        }
        
        on<RenderEvent.Last> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            val maxHealth = player.maxHealth
            val healthPercentage = (player.health / maxHealth) * 100f
            
            // Check if grace period has expired
            if (isInGracePeriod) {
                if (System.currentTimeMillis() >= gracePeriodEndTime) {
                    isInGracePeriod = false
                } else {
                    return@on // Still in grace period
                }
            }
            
            // Continuous Loop
            if (player.tickCount != lastTickCount) {
                lastTickCount = player.tickCount
                
                val inLowHp = enableLowHealth && healthPercentage <= lowHealthThreshold
                val inMidHp = enableMediumHealth && healthPercentage <= mediumHealthThreshold && !inLowHp
                
                val currentTime = System.currentTimeMillis()
                val soundInterval = 250L // Match text flashing speed (250ms)
                
                // Low Health Logic
                handleHealthWarning(lowHpState, inLowHp, lowHealthSoundSettings, currentTime, soundInterval)
                if (inLowHp) {
                    wasInLowHp = true
                } else if (!inMidHp) {
                    // Only reset wasInLowHp if we're above medium threshold
                    wasInLowHp = false
                }
                
                // Medium Health Logic
                handleHealthWarning(midHpState, inMidHp, mediumHealthSoundSettings, currentTime, soundInterval, allowTrigger = !wasInLowHp)
                if (!inMidHp && healthPercentage > mediumHealthThreshold) {
                    // Reset wasInLowHp when we go above medium threshold
                    wasInLowHp = false
                }
            }
        }
    }
    
    private fun handleWorldChange() {
        val currentRealm = LocalAPI.getCurrentCharacterWorld()
        val level = mc.level
        val currentDimension = level?.dimension()?.identifier()?.path ?: ""
        
        // Skip if empty
        if (currentRealm.isEmpty() || currentDimension.isEmpty()) {
            previousRealm = ""
            previousDimension = ""
            return
        }
        
        // First load
        if (previousRealm.isEmpty()) {
            previousRealm = currentRealm
            previousDimension = currentDimension
            if (currentDimension == "realm") {
                startGracePeriod()
            }
            return
        }
        
        // No change
        if (currentRealm == previousRealm && currentDimension == previousDimension) {
            return
        }
        
        // Check for realm entry transitions
        val wasInHub = previousDimension.equalsOneOf("hub", "daily")
        val isInRealm = currentDimension == "realm"
        
        // Entering realm from hub/daily
        if (isInRealm && wasInHub) {
            startGracePeriod()
        }
        // Switching between different realms
        else if (previousDimension == "realm" && isInRealm && currentRealm != previousRealm) {
            startGracePeriod()
        }
        
        previousRealm = currentRealm
        previousDimension = currentDimension
    }
    
    private fun startGracePeriod() {
        isInGracePeriod = true
        gracePeriodEndTime = System.currentTimeMillis() + 2000L // 2 second grace period
    }
    
    private fun playWarningSound(soundData: Triple<String, Float, Float>) {
        val (soundId, volume, pitch) = soundData
        mc.execute {
            try {
                val soundEvent = SoundEvent.createVariableRangeEvent(Identifier.parse(soundId))
                playSoundAtPlayer(soundEvent, volume, pitch)
            } catch (e: Exception) {
                playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value(), volume, pitch)
            }
        }
    }
    
    private fun isFlashPhase(): Boolean {
        // Flashes every 250 milliseconds
        return (System.currentTimeMillis() / 250) % 2 == 0L
    }
    
    private fun lightenColor(color: Int, amount: Float): Int {
        val clampedAmount = amount.coerceIn(0f, 1f)
        val a = (color shr 24 and 0xFF)
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        val newR = (r + (255 - r) * clampedAmount).toInt().coerceIn(0, 255)
        val newG = (g + (255 - g) * clampedAmount).toInt().coerceIn(0, 255)
        val newB = (b + (255 - b) * clampedAmount).toInt().coerceIn(0, 255)
        return (a shl 24) or (newR shl 16) or (newG shl 8) or newB
    }
    
    private fun handleHealthWarning(
        state: HealthWarningState,
        isActive: Boolean,
        soundSettings: () -> Triple<String, Float, Float>,
        currentTime: Long,
        soundInterval: Long,
        allowTrigger: Boolean = true
    ) {
        if (isActive) {
            if (warningMode.selected == "Continuous") {
                // Continuous mode - loop sounds at flash rate
                if (currentTime - state.lastSoundTime >= soundInterval) {
                    playWarningSound(soundSettings())
                    state.lastSoundTime = currentTime
                }
                state.ticks++
            } else {
                // Periodic mode - play sounds during duration at flash rate
                if (!state.triggered && allowTrigger) {
                    state.startTime = currentTime
                    playWarningSound(soundSettings())
                    state.lastSoundTime = currentTime
                    state.triggered = true
                } else if (state.triggered && allowTrigger) {
                    val elapsed = (currentTime - state.startTime) / 1000.0
                    if (elapsed <= periodicDuration && currentTime - state.lastSoundTime >= soundInterval) {
                        playWarningSound(soundSettings())
                        state.lastSoundTime = currentTime
                    }
                }
            }
        } else {
            state.ticks = 0
            state.triggered = false
            state.lastSoundTime = 0L
        }
    }
    
    override fun onEnable() {
        super.onEnable()
        Melinoe.logger.info("Health Indicator enabled")
    }
    
    override fun onDisable() {
        super.onDisable()
        lowHpState.apply { ticks = 0; startTime = 0L; triggered = false; lastSoundTime = 0L }
        midHpState.apply { ticks = 0; startTime = 0L; triggered = false; lastSoundTime = 0L }
        wasInLowHp = false
        isInGracePeriod = false
        Melinoe.logger.info("Health Indicator disabled")
    }
}