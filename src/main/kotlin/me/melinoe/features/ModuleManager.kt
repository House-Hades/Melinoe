package me.melinoe.features

import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.impl.HUDSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.config.ModuleConfig
import me.melinoe.events.InputEvent
import me.melinoe.events.core.on
import me.melinoe.features.impl.combat.*
import me.melinoe.features.impl.misc.*
import me.melinoe.features.impl.tracking.*
import me.melinoe.features.impl.tracking.bosstracker.TrackerModule
import me.melinoe.features.impl.visual.*
import me.melinoe.features.impl.visual.dungeontimer.TimerModule
import me.melinoe.features.impl.visual.healthbar.HealthBarModule
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.io.File

/**
 * Handles module registration, loading, and saving.
 */
object ModuleManager {

    /**
     * Map containing all modules in Melinoe,
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = hashMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    /**
     * List of all configurations handled by Melinoe.
     */
    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: Identifier = Identifier.fromNamespaceAndPath(Melinoe.MOD_ID, "melinoe_hud")

    /**
     * Registers modules to the [ModuleManager] and initializes them.
     *
     * @param config the config the [Module] is saved to,
     * it is recommended that each unique mod that uses this has its own config
     */
    fun registerModules(config: ModuleConfig, vararg modules: Module) {
        for (module in modules) {
            if (module.isDevModule && !FabricLoader.getInstance().isDevelopmentEnvironment) continue

            val lowercase = module.name.lowercase()
            config.modules[lowercase] = module
            this.modules[lowercase] = module
            this.modulesByCategory.getOrPut(module.category) { arrayListOf() }.add(module)

            // Subscribe module to EventBus so its event listeners work
            me.melinoe.events.core.EventBus.subscribe(module)

            module.key?.let { keybind ->
                val setting = KeybindSetting("Keybind", keybind, "Toggles this module.")
                setting.onPress = module::onKeybind
                module.registerSetting(setting)
            }

            for ((_, setting) in module.settings) {
                when (setting) {
                    is KeybindSetting -> keybindSettingsCache.add(setting)
                    is HUDSetting -> hudSettingsCache.add(setting)
                }
            }
        }
        configs.add(config)
        config.load()
    }

    /**
     * Loads all configs from disk.
     */
    fun loadConfigurations() {
        for (config in configs) {
            config.load()
        }
    }

    /**
     * Saves all configs to disk.
     */
    fun saveConfigurations() {
        for (config in configs) {
            config.save()
        }
    }

    fun render(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val mc = Melinoe.mc
        if (mc.level == null || mc.player == null || mc.screen == me.melinoe.clickgui.HudManager || mc.options.hideGui) return
        
        // Render weapon & ability cooldown HUD
        WeaponCooldownModule.renderHud(context)
        AbilityCooldownModule.renderHud(context)
        
        // Render Health Bar HUD (unified module)
        HealthBarModule.renderHud(context)
        
        context.pose().pushMatrix()
        val sf = mc.window.guiScale
        context.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(context, false)
        }
        context.pose().popMatrix()
    }

    /**
     * Get a module by name.
     */
    fun getModule(name: String): Module? = modules[name.lowercase()]

    /**
     * Get all modules in a category.
     */
    fun getModulesByCategory(category: Category): List<Module> =
        modulesByCategory[category] ?: emptyList()

    /**
     * Enable a module by name.
     */
    fun enableModule(name: String) {
        getModule(name)?.let { if (!it.enabled) it.toggle() }
    }

    /**
     * Disable a module by name.
     */
    fun disableModule(name: String) {
        getModule(name)?.let { if (it.enabled) it.toggle() }
    }

    /**
     * Toggle a module by name.
     */
    fun toggleModule(name: String) {
        getModule(name)?.toggle()
    }

    init {
        // Register all modules
        registerModules(
            config = ModuleConfig(file = File(Melinoe.configFile, "melinoe-config.json")),
            // Combat
            AutoClickerModule,
            WeaponRangeModule,
            AbilityRangeModule,
            ArmorCooldownsModule,
            WeaponCooldownModule,
            AbilityCooldownModule,
            AssassinStacksModule,

            // Visual
            FullbrightModule,
            PerformanceHUDModule,
            TimerModule,
            HealthBarModule,
            HealthIndicatorModule,
            PlayerSizeModule,
            CameraModule,
            HitboxModule,
            HideArmorModule,
            ArmorHUDModule,
            
            // Tracking
            LifetimeStatsModule,
            PityCounterModule,
            TrackerModule,
            SessionManagerModule,

            // Misc
            DiscordRPCModule,
            KeybindsModule,
            HideHeldTooltipsModule,
            ChatModule,

            // Utility
            me.melinoe.features.impl.combat.AutoSprintModule,
            me.melinoe.features.impl.ClickGUIModule
        )

        // Register input event handler for keybinds
        on<InputEvent> {
            for (setting in keybindSettingsCache) {
                if (setting.value == key) {
                    setting.onPress?.invoke()
                }
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, HUD_LAYER, ModuleManager::render)
    }
}