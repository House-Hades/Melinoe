package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import com.github.stivais.commodore.utils.SyntaxException
import me.melinoe.Melinoe
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.ClickGUI
import me.melinoe.clickgui.HudManager
import me.melinoe.features.ModuleManager
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.*
import me.melinoe.utils.handlers.schedule

val mainCommand = Commodore("melinoe", "m", "mel") {
    runs {
        schedule(0) { mc.setScreen(ClickGUI) }
    }

    literal("help").runs {
        val c = Message.Colors.COMMAND
        val t = Message.Colors.TEXT
        val m = Message.Colors.MUTED
        val p = "${Message.Colors.PREFIX}<bold>› </bold><reset>"
        val s = "$m- <reset>"

        Message.chat("""
            ${t}Command Help:
            $p$c/melinoe$m, $c/mel$m, $c/m $s${t}Opens the ClickGUI
            $p$c/melinoe edithud $s${t}Opens the HUD Manager
            $p$c/melinoe tps $s${t}Shows current TPS
            $p$c/melinoe ping $s${t}Shows current ping
            $p$c/melinoe sendcoords [message] $s${t}Sends your coordinates to chat
            $p$c/melinoe stats $s${t}Shows tracking data statistics
            $p$c/melinoe export $s${t}Exports tracking data
            $p$c/melinoe import \<data> $s${t}Imports tracking data
            $p$c/melinoe backup $s${t}Creates a backup
            $p$c/melinoe backups $s${t}Lists available backups
            $p$c/melinoe restore \<number> $s${t}Restores from backup
            $p$c/melinoe clear $s${t}Clears all tracking data
            $p$c/melinoe reset module \<moduleName> $s${t}Resets a module's settings
            $p$c/melinoe reset \<clickgui╏hud> $s${t}Resets ClickGUI or HUD positions
        """.trimIndent())
    }

    literal("edithud").runs {
        schedule(0) { mc.setScreen(HudManager) }
    }

    literal("tps").runs {
        Message.chat("${Message.Colors.SUCCESS}TPS: ${Message.Colors.TEXT}${ServerUtils.averageTps}")
    }

    literal("ping").runs {
        Message.chat("${Message.Colors.SUCCESS}Ping: ${Message.Colors.TEXT}${ServerUtils.averagePing}ms")
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
                Message.chat("${Message.Colors.SUCCESS}Settings for module ${Message.Colors.TEXT}${module.name}${Message.Colors.SUCCESS} have been reset to default values.")
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

                        // Display export string in chat (clickable fallback)
                        Message.chat("${Message.Colors.MUTED}${exportString.take(50)}...")
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
                val t = Message.Colors.TEXT
                val m = Message.Colors.MUTED

                backups.forEachIndexed { index, backup ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(backup.timestamp))
                    val size = "%.2f KB".format(backup.sizeBytes / 1024.0)

                    Message.chat("$t  ${index + 1}. $m$date$t - $m$size")
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

            val t = Message.Colors.TEXT
            val s = Message.Colors.SUCCESS

            Message.chat("""
                $t  Pity Counters: $s$pityCount
                $t  Lifetime Stats: $s$statsCount
                $t  Personal Bests: $s$pbCount
            """.trimIndent())
        } catch (e: Exception) {
            Message.error("Failed to get statistics: ${e.message}")
            Melinoe.logger.error("Failed to get statistics", e)
        }
    }

    literal("clear") {
        runs {
            val w = Message.Colors.WARNING
            val t = Message.Colors.TEXT
            val e = Message.Colors.ERROR
            val s = Message.Colors.SUCCESS
            val c = Message.Colors.COMMAND

            Message.chat("""
                $w<bold>⚠ Clear Data Warning ⚠</bold>
                ${t}This will permanently delete ALL tracking data!
                ${t}A backup will be created automatically.
                $e<bold>This action cannot be undone!</bold>
                ${s}Run $c/melinoe clear confirm$s to proceed.
            """.trimIndent())
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