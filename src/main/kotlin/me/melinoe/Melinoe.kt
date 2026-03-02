package me.melinoe

import me.melinoe.commands.devCommand
import me.melinoe.commands.mainCommand
import me.melinoe.events.EventDispatcher
import me.melinoe.events.core.EventBus
import me.melinoe.features.ModuleManager
import me.melinoe.utils.IrisCompat
import me.melinoe.utils.LocalAPI
import me.melinoe.utils.ServerUtils
import me.melinoe.utils.data.persistence.DataConfig
import me.melinoe.utils.handlers.BossDefeatHandler
import me.melinoe.utils.handlers.TickTasks
import me.melinoe.utils.render.ItemStateRenderer
import me.melinoe.utils.render.RenderBatchManager
import me.melinoe.utils.ui.rendering.NVGPIPRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

/**
 * Main entry point for Melinoe.
 */
object Melinoe : ClientModInitializer {

    val logger: Logger = LogManager.getLogger("Melinoe")

    @JvmStatic
    val mc: Minecraft = Minecraft.getInstance()

    /**
     * Main config file location.
     * @see me.melinoe.config.ModuleConfig
     */
    val configFile: File = File(mc.gameDirectory, "config/melinoe/").apply {
        try {
            if (isFile) delete() // Delete old bugged files that prevent creating the directory
            if (!exists()) mkdirs()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
    }

    const val MOD_ID = "melinoe"

    val version: Version by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().metadata.version }

    override fun onInitializeClient() {
        // Initialize tracking data integration FIRST (before EventBus subscriptions)
        DataConfig.initialize()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(
                mainCommand,
                devCommand
            ).forEach { commodore -> commodore.register(dispatcher) }
        }

        listOf(
            this, TickTasks, ServerUtils, EventDispatcher,
            BossDefeatHandler, RenderBatchManager, LocalAPI,
            IrisCompat, ModuleManager
        ).forEach { EventBus.subscribe(it) }

        SpecialGuiElementRegistry.register { context ->
            NVGPIPRenderer(context.vertexConsumers())
        }

        SpecialGuiElementRegistry.register { context ->
            ItemStateRenderer(context.vertexConsumers())
        }

        // Initialize LocalAPI AFTER subscribing to EventBus
        LocalAPI.initialize()
    }

    /**
     * Shutdown Melinoe.
     * Call this when your mod is shutting down.
     */
    fun shutdown() {
        logger.info("Shutting down Melinoe...")
        
        try {
            // Save module configurations
            ModuleManager.saveConfigurations()
            
            // Shutdown DataConfig (handles async saves and creates final backup)
            me.melinoe.utils.data.persistence.DataConfig.shutdown()
            
            logger.info("Melinoe shutdown complete")
        } catch (e: Exception) {
            logger.error("Error during Melinoe shutdown: ${e.message}", e)
        }
    }
    

}