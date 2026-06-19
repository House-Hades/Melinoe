package me.melinoe.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

/**
 * Base command class.
 */
abstract class Command(
    val name: String,
    val description: String = "",
    val aliases: Array<String> = emptyArray()
) {

    /**
     * Register this command with the command dispatcher.
     */
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val command = ClientCommands.literal(name)
            .executes { execute(it) }
            .build()

        dispatcher.root.addChild(command)

        // Register aliases
        aliases.forEach { alias ->
            val aliasCommand = ClientCommands.literal(alias)
                .executes { execute(it) }
                .build()
            dispatcher.root.addChild(aliasCommand)
        }
    }

    /**
     * Execute the command.
     */
    abstract fun execute(context: CommandContext<FabricClientCommandSource>): Int

    /**
     * Send a message to the player.
     */
    protected fun sendMessage(message: String) {
        // TODO: Implement message sending
        println("[melinoe] $message")
    }
}