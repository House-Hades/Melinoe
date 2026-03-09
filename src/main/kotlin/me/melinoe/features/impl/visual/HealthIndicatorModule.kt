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
import me.melinoe.utils.equalsOneOf
import me.melinoe.utils.playSoundAtPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object HealthIndicatorModule : Module(
    name = "Health Indicator",
    category = Category.VISUAL,
    description = "Displays warnings and sounds when health is low."
) {
    private var lastTickCount = 0
    private var lowHpTicks = 0
    private var midHpTicks = 0
    
    // Grace Period State
    private var worldJoinTime = 0L
    private var previousRealm = ""
    private var previousDimension = ""
    private var isInGracePeriod = false
    private var lastMaxHealth = 0f
    
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
        }
        
        val baseColor = 0xFFFF0000.toInt()
        val displayColor = if (titleFlashing && isFlashPhase()) lightenColor(baseColor, 0.3f) else baseColor
        
        val component = Component.literal(lowHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        val textWidth = mc.font.width(component)
        drawString(mc.font, component, 0, 0, displayColor, true)
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
        }
        
        val baseColor = 0xFFFFAA00.toInt()
        val displayColor = if (titleFlashing && isFlashPhase()) lightenColor(baseColor, 0.3f) else baseColor
        
        val component = Component.literal(mediumHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        val textWidth = mc.font.width(component)
        drawString(mc.font, component, 0, 0, displayColor, true)
        textWidth to mc.font.lineHeight
    }.withDependency { enableMediumHealth }
    
    // Title Settings
    private val titleSettingsDropdown by DropdownSetting("Title Settings", false)
    private val lowHealthText by StringSetting("Low HP Text", "LOW HEALTH!", desc = "Text to display for low health").withDependency { titleSettingsDropdown }
    private val mediumHealthText by StringSetting("Mid HP Text", "MEDIUM HEALTH", desc = "Text to display for medium health").withDependency { titleSettingsDropdown }
    private val titleFlashing by BooleanSetting("Text Flashing", true, "Flash the warning text").withDependency { titleSettingsDropdown }
    
    // Sound Settings
    private val soundSettingsDropdown by DropdownSetting("Sound Settings", false)
    
    private val lowHealthSoundSettings = createSoundSettings("Low HP Sound", "entity.experience_orb.pickup", { soundSettingsDropdown }, "Play Low HP Sound")
    private val lowHealthSoundCooldown by NumberSetting("Low HP Sound Cooldown", 5.0, 1.0, 40.0, 1.0, "Cooldown for the low HP sound").withDependency { soundSettingsDropdown }
    
    private val mediumHealthSoundSettings = createSoundSettings("Mid HP Sound", "block.note_block.pling", { soundSettingsDropdown }, "Play Mid HP Sound")
    private val mediumHealthSoundCooldown by NumberSetting("Mid HP Sound Cooldown", 10.0, 1.0, 40.0, 1.0, "Cooldown for the medium HP sound").withDependency { soundSettingsDropdown }
    
    init {
        on<WorldLoadEvent> {
            lowHpTicks = 0
            midHpTicks = 0
            handleWorldChange()
        }
        
        on<RenderEvent.Last> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            val maxHealth = player.maxHealth
            val healthPercentage = (player.health / maxHealth) * 100f
            
            // Grace Period on World Entering
            updateGracePeriod(maxHealth)
            if (isInGracePeriod) return@on
            
            // Continuous Loop
            if (player.tickCount != lastTickCount) {
                lastTickCount = player.tickCount
                
                val inLowHp = enableLowHealth && healthPercentage <= lowHealthThreshold
                val inMidHp = enableMediumHealth && healthPercentage <= mediumHealthThreshold && !inLowHp
                
                if (inLowHp) {
                    if (lowHpTicks % lowHealthSoundCooldown.toInt() == 0) playWarningSound(lowHealthSoundSettings())
                    lowHpTicks++
                } else {
                    lowHpTicks = 0
                }
                
                if (inMidHp) {
                    if (midHpTicks % mediumHealthSoundCooldown.toInt() == 0) playWarningSound(mediumHealthSoundSettings())
                    midHpTicks++
                } else {
                    midHpTicks = 0
                }
            }
        }
    }
    
    // Grace Period
    private fun updateGracePeriod(currentMaxHealth: Float) {
        val currentTime = System.currentTimeMillis()
        val timeBasedGrace = currentTime - worldJoinTime < 2000L // 2 seconds grace period
        val healthJustChanged = currentMaxHealth != lastMaxHealth
        
        if (timeBasedGrace || healthJustChanged) {
            lastMaxHealth = currentMaxHealth
            if (currentMaxHealth != 20f) isInGracePeriod = false
        } else {
            lastMaxHealth = currentMaxHealth
        }
    }
    
    private fun handleWorldChange() {
        val currentRealm = LocalAPI.getCurrentCharacterWorld()
        val level = mc.level
        val currentDimension = level?.dimension()?.location()?.path ?: ""
        
        if (currentRealm.isEmpty() || currentDimension.isEmpty()) return
        
        if (previousRealm.isEmpty() || previousDimension.isEmpty() ||
            currentDimension.equalsOneOf("hub", "daily") ||
            (previousDimension.equalsOneOf("hub", "daily") && currentDimension == "realm") ||
            (previousDimension == "realm" && currentDimension == "realm" && currentRealm != previousRealm)) {
            
            worldJoinTime = System.currentTimeMillis()
            isInGracePeriod = true
            lastMaxHealth = 0f
        }
        
        previousRealm = currentRealm
        previousDimension = currentDimension
    }
    
    private fun playWarningSound(soundData: Triple<String, Float, Float>) {
        val (soundId, volume, pitch) = soundData
        mc.execute {
            try {
                val soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(soundId))
                playSoundAtPlayer(soundEvent, volume, pitch)
            } catch (e: Exception) {
                playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value(), volume, pitch)
            }
        }
    }
    
    private fun isFlashPhase(): Boolean {
        // Flashes every 300 milliseconds
        return (System.currentTimeMillis() / 300) % 2 == 0L
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
    
    override fun onEnable() {
        super.onEnable()
        Melinoe.logger.info("Health Indicator enabled")
    }
    
    override fun onDisable() {
        super.onDisable()
        lowHpTicks = 0
        midHpTicks = 0
        isInGracePeriod = false
        Melinoe.logger.info("Health Indicator disabled")
    }
}