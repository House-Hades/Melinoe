package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.parsers.CommandParsable
import com.github.stivais.commodore.utils.GreedyString
import com.github.stivais.commodore.utils.SyntaxException
import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.ClickGUI
import me.melinoe.clickgui.HudManager
import me.melinoe.features.ModuleManager
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.*
import me.melinoe.utils.ChatStyle
import me.melinoe.utils.handlers.schedule
import net.minecraft.network.chat.Component

val mainCommand = Commodore("melinoe", "m", "mel") {
    runs {
        schedule(0) { mc.setScreen(ClickGUI) }
    }

    literal("help").runs {
        val helpMessage = (Component.empty() as net.minecraft.network.chat.MutableComponent)
            .append(ChatStyle.regular("Command Help:\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe"))
            .append(ChatStyle.muted(", "))
            .append(ChatStyle.command("/mel"))
            .append(ChatStyle.muted(", "))
            .append(ChatStyle.command("/m "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Opens the ClickGUI\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe edithud "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Opens the HUD Manager\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe tps "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Shows current TPS\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe ping "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Shows current ping\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe sendcoords [message] "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Sends your coordinates to chat\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe stats "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Shows tracking data statistics\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe export "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Exports tracking data\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe import <data> "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Imports tracking data\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe backup "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Creates a backup\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe backups "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Lists available backups\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe restore <number> "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Restores from backup\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe clear "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Clears all tracking data\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe reset module <moduleName> "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Resets a module's settings\n"))
            .append(ChatStyle.prefix())
            .append(ChatStyle.command("/melinoe reset "))
            .append(ChatStyle.command("<clickgui╏hud> "))
            .append(ChatStyle.separator())
            .append(ChatStyle.regular("Resets ClickGUI or HUD positions"))
        
        Message.chat(helpMessage)
    }

    literal("edithud").runs {
        schedule(0) { mc.setScreen(HudManager) }
    }

    literal("tps").runs {
        val message = (ChatStyle.success("TPS: ") as net.minecraft.network.chat.MutableComponent)
            .append(ChatStyle.regular("${ServerUtils.averageTps}"))
        Message.chat(message)
    }

    literal("ping").runs {
        val message = (ChatStyle.success("Ping: ") as net.minecraft.network.chat.MutableComponent)
            .append(ChatStyle.regular("${ServerUtils.averagePing}ms"))
        Message.chat(message)
    }

    literal("sendcoords").runs { message: GreedyString? ->
        sendChatMessage(getPositionString() + if (message == null) "" else " ${message.string}")
    }

    literal("reset") {
        literal("module").executable {
            param("moduleName") {
                // keys for modules are already lowercase
                suggests { ModuleManager.modules.keys.map { it.replace(" ", "_") } }
            }

            runs { moduleName: String ->
                val module = ModuleManager.modules[moduleName.replace("_", " ")]
                    ?: throw SyntaxException("Module not found.")

                module.settings.forEach { (_, setting) -> setting.reset() }
                val message = (ChatStyle.success("Settings for module ") as net.minecraft.network.chat.MutableComponent)
                    .append(ChatStyle.regular(module.name))
                    .append(ChatStyle.success(" have been reset to default values."))
                Message.chat(message)
            }
        }

        literal("clickgui").runs {
            ClickGUIModule.resetPositions()
            Message.success("Reset click gui positions.")
        }
        literal("hud").runs {
            HudManager.resetHUDS()
            Message.success("Reset HUD positions.")
        }
    }
    

    literal("export") {
        runs {
            // Export with compression (default)
            Message.info("Exporting tracking data...")
            
            try {
                val exportString = me.melinoe.utils.data.persistence.DataConfig.exportData(compressed = true)
                
                if (exportString != null) {
                    // Copy to clipboard
                    try {
                        mc.keyboardHandler.clipboard = exportString
                        Message.success("Data exported and copied to clipboard!")
                        Message.info("You can share this string to transfer your data to another account.")
                    } catch (e: Exception) {
                        Message.success("Data exported successfully!")
                        Message.info("Export string (click to copy):")
                        
                        // Display export string in chat (clickable)
                        val exportMsg = (ChatStyle.regular("") as net.minecraft.network.chat.MutableComponent)
                            .append(ChatStyle.muted(exportString.take(50) + "..."))
                        Message.chat(exportMsg)
                    }
                } else {
                    Message.error("Failed to export data. Check logs for details.")
                }
            } catch (e: Exception) {
                Message.error("Export failed: ${e.message}")
                Melinoe.logger.error("Export failed", e)
            }
        }
        
        literal("uncompressed").runs {
            // Export without compression
            Message.info("Exporting tracking data (uncompressed)...")
            
            try {
                val exportString = me.melinoe.utils.data.persistence.DataConfig.exportData(compressed = false)
                
                if (exportString != null) {
                    // Copy to clipboard
                    try {
                        mc.keyboardHandler.clipboard = exportString
                        Message.success("Data exported (uncompressed) and copied to clipboard!")
                    } catch (e: Exception) {
                        Message.success("Data exported successfully!")
                        Message.info("Export string is in your clipboard.")
                    }
                } else {
                    Message.error("Failed to export data. Check logs for details.")
                }
            } catch (e: Exception) {
                Message.error("Export failed: ${e.message}")
                Melinoe.logger.error("Export failed", e)
            }
        }
    }
    
    literal("import") {
        runs {
            Message.info("To import data, use: /melinoe import <data>")
            Message.info("Add 'merge' to merge with existing data: /melinoe import merge <data>")
        }
        
        literal("merge").executable {
            param("data") {
                // No suggestions for import data
            }
            
            runs { data: GreedyString ->
                Message.info("Importing tracking data (merge mode)...")
                
                try {
                    val success = me.melinoe.utils.data.persistence.DataConfig.importData(data.string, merge = true)
                    
                    if (success) {
                        Message.success("Data imported and merged successfully!")
                        Message.info("Your existing data has been preserved and new data has been added.")
                    } else {
                        Message.error("Failed to import data. Check logs for details.")
                    }
                } catch (e: Exception) {
                    Message.error("Import failed: ${e.message}")
                    Melinoe.logger.error("Import failed", e)
                }
            }
        }
        
        executable {
            param("data") {
                // No suggestions for import data
            }
            
            runs { data: GreedyString ->
                Message.info("Importing tracking data (replace mode)...")
                Message.warning("This will replace all existing data!")
                
                try {
                    val success = me.melinoe.utils.data.persistence.DataConfig.importData(data.string, merge = false)
                    
                    if (success) {
                        Message.success("Data imported successfully!")
                        Message.warning("All previous data has been replaced.")
                    } else {
                        Message.error("Failed to import data. Check logs for details.")
                    }
                } catch (e: Exception) {
                    Message.error("Import failed: ${e.message}")
                    Melinoe.logger.error("Import failed", e)
                }
            }
        }
    }
    
    literal("backup") {
        runs {
            Message.info("Creating backup...")
            
            try {
                val success = me.melinoe.utils.data.persistence.DataConfig.createBackup()
                
                if (success) {
                    Message.success("Backup created successfully!")
                } else {
                    Message.error("Failed to create backup. Check logs for details.")
                }
            } catch (e: Exception) {
                Message.error("Backup failed: ${e.message}")
                Melinoe.logger.error("Backup failed", e)
            }
        }
    }
    
    literal("backups").runs {
        Message.info("Listing available backups...")
        
        try {
            val backups = me.melinoe.utils.data.persistence.DataConfig.listBackups()
            
            if (backups.isEmpty()) {
                Message.info("No backups found.")
            } else {
                Message.success("Found ${backups.size} backup(s):")
                backups.forEachIndexed { index, backup ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(backup.timestamp))
                    val size = "%.2f KB".format(backup.sizeBytes / 1024.0)
                    
                    val backupMsg = (ChatStyle.regular("  ${index + 1}. ") as net.minecraft.network.chat.MutableComponent)
                        .append(ChatStyle.muted("$date"))
                        .append(ChatStyle.regular(" - "))
                        .append(ChatStyle.muted(size))
                    Message.chat(backupMsg)
                }
                Message.info("Use /melinoe restore <number> to restore a backup")
            }
        } catch (e: Exception) {
            Message.error("Failed to list backups: ${e.message}")
            Melinoe.logger.error("Failed to list backups", e)
        }
    }
    
    literal("restore") {
        runs {
            Message.info("To restore a backup, use: /melinoe restore <number>")
            Message.info("Use /melinoe backups to see available backups")
        }
        
        executable {
            param("backupNumber") {
                suggests { listOf("1", "2", "3", "4", "5") }
            }
            
            runs { backupNumber: String ->
                val index = backupNumber.toIntOrNull()?.minus(1)
                
                if (index == null || index < 0) {
                    Message.error("Invalid backup number. Use /melinoe backups to see available backups.")
                    return@runs
                }
                
                Message.info("Restoring backup #${index + 1}...")
                Message.warning("This will replace all current data!")
                
                try {
                    val success = me.melinoe.utils.data.persistence.DataConfig.restoreFromBackup(index)
                    
                    if (success) {
                        Message.success("Backup restored successfully!")
                        Message.info("All data has been restored from backup #${index + 1}")
                    } else {
                        Message.error("Failed to restore backup. Backup #${index + 1} may not exist.")
                        Message.info("Use /melinoe backups to see available backups")
                    }
                } catch (e: Exception) {
                    Message.error("Restore failed: ${e.message}")
                    Melinoe.logger.error("Restore failed", e)
                }
            }
        }
    }
    
    literal("stats").runs {
        Message.info("Tracking Data Statistics:")
        
        try {
            // Get counts from DataConfig
            val pityCount = me.melinoe.utils.data.persistence.DataConfig.getAllPityCounters().size
            val statsCount = me.melinoe.utils.data.persistence.DataConfig.getAllLifetimeStats().size
            val pbCount = me.melinoe.utils.data.persistence.DataConfig.getAllPersonalBests().size
            
            val statsMsg = (ChatStyle.regular("  Pity Counters: ") as net.minecraft.network.chat.MutableComponent)
                .append(ChatStyle.success("$pityCount"))
                .append(ChatStyle.regular("\n  Lifetime Stats: "))
                .append(ChatStyle.success("$statsCount"))
                .append(ChatStyle.regular("\n  Personal Bests: "))
                .append(ChatStyle.success("$pbCount"))
            Message.chat(statsMsg)
        } catch (e: Exception) {
            Message.error("Failed to get statistics: ${e.message}")
            Melinoe.logger.error("Failed to get statistics", e)
        }
    }
    
    literal("clear") {
        runs {
            val confirmMsg = (ChatStyle.warning("⚠ Clear Data Warning ⚠\n") as net.minecraft.network.chat.MutableComponent)
                .append(ChatStyle.regular("This will permanently delete ALL tracking data!\n"))
                .append(ChatStyle.regular("A backup will be created automatically.\n"))
                .append(ChatStyle.error("This action cannot be undone!\n"))
                .append(ChatStyle.success("Run "))
                .append(ChatStyle.command("/melinoe clear confirm"))
                .append(ChatStyle.success(" to proceed."))
            Message.chat(confirmMsg)
        }
        
        literal("confirm").runs {
            Message.warning("Clearing all tracking data...")
            
            try {
                // Create backup before clearing
                Message.info("Creating backup...")
                val backupSuccess = me.melinoe.utils.data.persistence.DataConfig.createBackup()
                if (!backupSuccess) {
                    Message.error("Failed to create backup. Clear operation aborted.")
                    return@runs
                }
                Message.success("Backup created successfully.")
                
                // Clear all data
                me.melinoe.utils.data.persistence.DataConfig.clearAllData()
                
                Message.success("All tracking data has been cleared!")
                Message.info("You can restore from backup using /melinoe restore")
            } catch (e: Exception) {
                Message.error("Failed to clear data: ${e.message}")
                Melinoe.logger.error("Failed to clear data", e)
            }
        }
    }
}
