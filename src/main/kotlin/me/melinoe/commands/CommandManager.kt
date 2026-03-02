package me.melinoe.commands

import me.melinoe.Melinoe
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/**
 * Command manager.
 */
object CommandManager {

    private val commands = mutableListOf<Command>()

    init {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            commands.forEach { command ->
                try {
                    command.register(dispatcher)
                    Melinoe.logger.info("Registered command: ${command.name}")
                } catch (e: Exception) {
                    Melinoe.logger.error("Failed to register command: ${command.name}", e)
                }
            }
        }
    }

    /**
     * Register a command.
     */
    fun registerCommand(command: Command) {
        commands.add(command)
    }

    /**
     * Register multiple commands.
     */
    fun registerCommands(vararg commands: Command) {
        commands.forEach { registerCommand(it) }
    }

    /**
     * Get all registered commands.
     */
    fun getCommands(): List<Command> = commands.toList()

    /**
     * Find a command by name or alias.
     */
    fun getCommand(name: String): Command? {
        return commands.find { command ->
            command.name.equals(name, ignoreCase = true) ||
            command.aliases.any { it.equals(name, ignoreCase = true) }
        }
    }
}