package me.melinoe.features.impl.visual.healthbar

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.*
import me.melinoe.events.RenderEvent
import me.melinoe.events.WorldLoadEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.createSoundSettings
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Unified Health Bar Module - Combines 3D world-space and HUD projection rendering.
 */
object HealthBarModule : Module(
    name = "Health Bar",
    category = Category.VISUAL,
    description = "Displays a health bar above the player with multiple rendering modes."
) {
    // Components
    private val healthState = HealthState()
    private val warnings = Warnings(mc)
    private val renderer3D = Renderer3D(mc)
    private val rendererHUD = RendererHUD(mc)
    
    // Render Mode
    private val renderMode by SelectorSetting(
        "Render Mode",
        "HUD Projection",
        arrayListOf("3D World", "HUD Projection"),
        desc = "Choose between 3D world-space or HUD-based rendering"
    )
    
    // Display Settings
    private val healthDisplay by SelectorSetting(
        "Health",
        "Numerical",
        arrayListOf("Numerical", "Decimals", "Percent", "None"),
        desc = "How to display health"
    )
    private val showMaxHealth by BooleanSetting(
        "Show Max Health",
        false,
        desc = "Show current/max health (e.g., 20 / 40)"
    )
    private val showBarOnly by BooleanSetting(
        "Text Only",
        false,
        desc = "Only show health text without the bar"
    )
    private val textPosition by SelectorSetting(
        "Text Position",
        "Center",
        arrayListOf("Left", "Center", "Right", "Above", "Off"),
        desc = "Position of health text"
    )
    
    // Animation Settings
    private val smoothHealth by BooleanSetting(
        "Smooth Health",
        true,
        desc = "Smoothly animate health changes"
    )
    private val damageFlash by BooleanSetting(
        "Damage Flash",
        true,
        desc = "Flash red when taking damage"
    )
    
    // Appearance Settings
    private val textScale by NumberSetting(
        "Text Scale",
        0.02,
        0.01,
        0.2,
        0.01,
        desc = "Scale of the health text (3D mode)"
    ).withDependency { renderMode == 0 }
    
    private val textScaleHUD by NumberSetting(
        "Text Scale (HUD)",
        1.0,
        0.5,
        3.0,
        0.1,
        desc = "Scale of the health text (HUD mode)"
    ).withDependency { renderMode == 1 }
    
    private val textColor by ColorSetting(
        "Text Color",
        Color(0xFFFFFFFF.toInt()),
        desc = "Color of the health text"
    )
    private val textOutline by BooleanSetting(
        "Text Outline",
        true,
        desc = "Enable black outline on health text"
    )
    
    private val barWidth by NumberSetting(
        "Bar Width",
        1.5,
        0.1,
        5.0,
        0.1,
        desc = "Width of the health bar (3D mode)"
    ).withDependency { renderMode == 0 }
    
    private val barWidthHUD by NumberSetting(
        "Bar Width (HUD)",
        80.0,
        20.0,
        100.0,
        5.0,
        desc = "Width of the health bar in pixels (HUD mode)"
    ).withDependency { renderMode == 1 }
    
    private val barHeight by NumberSetting(
        "Bar Height",
        0.3,
        0.01,
        1.0,
        0.01,
        desc = "Height of the health bar (3D mode)"
    ).withDependency { renderMode == 0 }
    
    private val barHeightHUD by NumberSetting(
        "Bar Height (HUD)",
        15.0,
        4.0,
        20.0,
        1.0,
        desc = "Height of the health bar in pixels (HUD mode)"
    ).withDependency { renderMode == 1 }
    
    private val yPosition by NumberSetting(
        "Y Position",
        -0.5,
        -3.0,
        3.0,
        0.1,
        desc = "Vertical position offset"
    )
    
    private val showInFirstPerson by BooleanSetting(
        "Show in First Person",
        false,
        desc = "Display health bar in first person view (HUD mode only)"
    ).withDependency { renderMode == 1 }
    
    // Health Warnings
    private val healthWarningsDropdown by DropdownSetting("Health Warnings", false)
    
    // Low Health Warning
    private val enableLowHealth by BooleanSetting(
        "Enable Low HP Warning",
        true,
        desc = "Enable low health warning"
    ).withDependency { healthWarningsDropdown }
    
    private val lowHealthThreshold by NumberSetting(
        "Low HP Threshold %",
        35.0,
        0.0,
        100.0,
        1.0,
        desc = "Health percentage to trigger low HP warning"
    ).withDependency { healthWarningsDropdown && enableLowHealth }
    
    private val lowHealthHud by HUDSetting(
        name = "Low HP Title",
        x = 400,
        y = 250,
        scale = 2f,
        toggleable = true,
        description = "Enable and position the low health title",
        module = this
    ) { example ->
        val currentTime = System.currentTimeMillis()
        if (currentTime > warnings.lowHealthTitleEndTime && !example) return@HUDSetting 0 to 0
        
        val baseColor = 0xFFFF0000.toInt()
        val displayColor = if (titleFlashing) {
            warnings.getFlashingColor(baseColor, warnings.lowHealthTitleEndTime, currentTime, lowHealthSoundRepeat.toInt())
        } else {
            baseColor
        }
        
        val component = Component.literal(lowHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        
        val textRenderer = mc.font
        val textWidth = textRenderer.width(component)
        val textHeight = textRenderer.lineHeight
        drawString(textRenderer, component, 0, 0, displayColor, true)
        textWidth to textHeight
    }.withDependency { healthWarningsDropdown && enableLowHealth }
    
    // Medium Health Warning
    private val enableMediumHealth by BooleanSetting(
        "Enable Mid HP Warning",
        true,
        desc = "Enable medium health warning"
    ).withDependency { healthWarningsDropdown }
    
    private val mediumHealthThreshold by NumberSetting(
        "Mid HP Threshold %",
        50.0,
        0.0,
        100.0,
        1.0,
        desc = "Health percentage to trigger mid HP warning"
    ).withDependency { healthWarningsDropdown && enableMediumHealth }
    
    private val mediumHealthHud by HUDSetting(
        name = "Mid HP Title",
        x = 400,
        y = 280,
        scale = 2f,
        toggleable = true,
        description = "Enable and position the medium health title",
        module = this
    ) { example ->
        val currentTime = System.currentTimeMillis()
        val lowHealthActive = currentTime <= warnings.lowHealthTitleEndTime
        if ((currentTime > warnings.mediumHealthTitleEndTime || lowHealthActive) && !example) return@HUDSetting 0 to 0
        
        val baseColor = 0xFFFFAA00.toInt()
        val displayColor = if (titleFlashing) {
            warnings.getFlashingColor(baseColor, warnings.mediumHealthTitleEndTime, currentTime, mediumHealthSoundRepeat.toInt())
        } else {
            baseColor
        }
        
        val component = Component.literal(mediumHealthText).withStyle(Style.EMPTY.withColor(displayColor))
        
        val textRenderer = mc.font
        val textWidth = textRenderer.width(component)
        val textHeight = textRenderer.lineHeight
        drawString(textRenderer, component, 0, 0, displayColor, true)
        textWidth to textHeight
    }.withDependency { healthWarningsDropdown && enableMediumHealth }
    
    // Title Settings
    private val titleSettingsDropdown by DropdownSetting("Title Settings", false).withDependency { healthWarningsDropdown }
    private val lowHealthText by StringSetting(
        "Low HP Text",
        "LOW HEALTH!",
        desc = "Text to display for low health"
    ).withDependency { titleSettingsDropdown }
    private val mediumHealthText by StringSetting(
        "Mid HP Text",
        "MEDIUM HEALTH",
        desc = "Text to display for medium health"
    ).withDependency { titleSettingsDropdown }
    private val titleFlashing by BooleanSetting(
        "Text Flashing",
        true,
        desc = "Flash the warning text"
    ).withDependency { titleSettingsDropdown }
    
    // Sound Settings
    private val soundSettingsDropdown by DropdownSetting("Sound Settings", false).withDependency { healthWarningsDropdown }
    private val lowHealthSoundSettings = createSoundSettings(
        "Low HP Sound",
        "entity.experience_orb.pickup",
        { soundSettingsDropdown },
        buttonName = "Play Low HP Sound"
    )
    private val lowHealthSoundRepeat by NumberSetting(
        "Low HP Sound Repeat",
        5.0,
        1.0,
        20.0,
        1.0,
        desc = "Number of times to repeat low HP sound"
    ).withDependency { soundSettingsDropdown }
    private val mediumHealthSoundSettings = createSoundSettings(
        "Mid HP Sound",
        "block.note_block.pling",
        { soundSettingsDropdown },
        buttonName = "Play Mid HP Sound"
    )
    private val mediumHealthSoundRepeat by NumberSetting(
        "Mid HP Sound Repeat",
        5.0,
        1.0,
        20.0,
        1.0,
        desc = "Number of times to repeat mid HP sound"
    ).withDependency { soundSettingsDropdown }

    init {
        on<WorldLoadEvent> {
            warnings.handleWorldChange()
        }
        
        on<RenderEvent.Last> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            
            // Check camera mode
            val isFirstPerson = mc.options.cameraType.isFirstPerson
            if (isFirstPerson && (renderMode == 0 || !showInFirstPerson)) return@on
            
            // Get current health
            val currentHealth = player.health
            val maxHealth = player.maxHealth
            
            // Initialize and update health state
            healthState.initialize(currentHealth)
            healthState.checkDamage(currentHealth, damageFlash)
            healthState.updateHealth(currentHealth, smoothHealth)
            
            val healthPercentage = healthState.displayedHealth / maxHealth
            
            // Check health warnings
            val updatedHealthPercent = warnings.checkHealthWarnings(
                healthPercentage,
                healthState.lastHealthPercentage,
                maxHealth,
                lowHealthThreshold,
                mediumHealthThreshold,
                enableLowHealth,
                enableMediumHealth,
                lowHealthHud.enabled,
                mediumHealthHud.enabled,
                lowHealthSoundSettings,
                mediumHealthSoundSettings,
                lowHealthSoundRepeat.toInt(),
                mediumHealthSoundRepeat.toInt()
            )
            healthState.lastHealthPercentage = updatedHealthPercent
            
            // Get health bar color
            val healthBarColor = healthState.getHealthBarColor(
                healthPercentage,
                lowHealthThreshold,
                mediumHealthThreshold,
                damageFlash
            )
            
            // Format health text
            val healthText = formatHealthText(player, healthPercentage)
            
            // Render based on mode
            when (renderMode) {
                0 -> render3D(context, player, healthPercentage, healthBarColor, healthText)
                1 -> renderHUD(player, healthPercentage, healthBarColor, healthText)
            }
        }
    }
    
    /**
     * Render in 3D world space
     */
    private fun render3D(
        context: WorldRenderContext,
        player: net.minecraft.world.entity.player.Player,
        healthPercentage: Float,
        healthBarColor: Int,
        healthText: String
    ) {
        val matrices = context.matrices() ?: return
        val camera = context.gameRenderer().mainCamera ?: return
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val bufferSource = context.consumers() as? net.minecraft.client.renderer.MultiBufferSource.BufferSource ?: return
        
        renderer3D.render(
            matrices,
            camera,
            player,
            tickDelta,
            healthPercentage,
            healthBarColor,
            barWidth,
            barHeight,
            yPosition,
            showBarOnly,
            healthDisplay,
            textPosition,
            healthText,
            textScale,
            textColor.rgba,
            textOutline,
            bufferSource
        )
    }
    
    /**
     * Calculate screen position for HUD rendering
     */
    private fun renderHUD(
        player: net.minecraft.world.entity.player.Player,
        healthPercentage: Float,
        healthBarColor: Int,
        healthText: String
    ) {
        val camera = mc.gameRenderer.mainCamera
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        
        rendererHUD.calculateScreenPosition(
            player,
            camera,
            tickDelta,
            healthPercentage,
            yPosition,
            showInFirstPerson
        )
    }
    
    /**
     * Called by ModuleManager during HUD rendering phase (HUD mode only)
     */
    fun renderHud(guiGraphics: GuiGraphics) {
        if (!enabled || renderMode != 1) return
        
        val player = mc.player ?: return
        
        // Check first-person mode
        val isFirstPerson = mc.options.cameraType.isFirstPerson
        if (isFirstPerson && !showInFirstPerson) return
        
        val healthPercentage = healthState.displayedHealth / player.maxHealth
        val healthBarColor = healthState.getHealthBarColor(
            healthPercentage,
            lowHealthThreshold,
            mediumHealthThreshold,
            damageFlash
        )
        val healthText = formatHealthText(player, healthPercentage)
        
        rendererHUD.renderHud(
            guiGraphics,
            healthBarColor,
            barWidthHUD,
            barHeightHUD,
            showBarOnly,
            healthDisplay,
            textPosition,
            healthText,
            textScaleHUD,
            textColor.rgba,
            textOutline
        )
    }
    
    /**
     * Format health text based on display mode
     */
    private fun formatHealthText(player: net.minecraft.world.entity.player.Player, healthPercentage: Float): String {
        val baseText = when (healthDisplay) {
            0 -> player.health.toInt().toString()
            1 -> "%.1f".format(player.health).replace(',', '.')
            2 -> "%.0f%%".format(healthPercentage * 100f)
            else -> ""
        }
        
        return if (showMaxHealth && baseText.isNotEmpty()) {
            val maxText = when (healthDisplay) {
                0 -> player.maxHealth.toInt().toString()
                1 -> "%.1f".format(player.maxHealth).replace(',', '.')
                2 -> "100%"
                else -> ""
            }
            "$baseText / $maxText"
        } else {
            baseText
        }
    }
    
    override fun onEnable() {
        super.onEnable()
        Melinoe.logger.info("Health Bar enabled - mode: ${if (renderMode == 0) "3D World" else "HUD Projection"}")
    }
    
    override fun onDisable() {
        super.onDisable()
        healthState.reset()
        warnings.reset()
        rendererHUD.clearCache()
        Melinoe.logger.info("Health Bar disabled")
    }
}
