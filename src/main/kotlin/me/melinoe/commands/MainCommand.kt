package me.melinoe.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.SyntaxException
import me.melinoe.Melinoe.mc
import me.melinoe.clickgui.ClickGUI
import me.melinoe.clickgui.HudManager
import me.melinoe.features.ModuleManager
import me.melinoe.features.impl.ClickGUIModule
import me.melinoe.utils.*
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.persistence.DataConfig
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
            $p$c/melinoe pity \<dungeon/boss> $s${t}Shows pity for a dungeon or boss
            $p$c/melinoe reset module \<moduleName> $s${t}Resets a module's settings
            $p$c/melinoe reset \<clickgui╏hud> $s${t}Resets ClickGUI or HUD positions
            $p$c/profile$m, $c/pv \<username> $s${t}Opens a player's profile
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
    
    literal("pity").executable {
        param("target") {
            suggests {
                runCatching {
                    DungeonData.all.map { it.name.lowercase() } + BossData.all.map { it.name.lowercase() }
                }.getOrDefault(emptyList())
            }
        }
        
        runs { target: String ->
            val dungeon = DungeonData.byKey(target.uppercase())
            val boss = if (dungeon == null) BossData.byKey(target.uppercase()) else null
            
            if (dungeon == null && boss == null) {
                return@runs
            }
            
            val title = dungeon?.areaName ?: boss?.label ?: "Unknown"
            var items = dungeon?.finalBoss?.items?.toList() ?: boss?.items?.toList() ?: emptyList()
            
            if (dungeon?.name == "RUSTBORN_KINGDOM") {
                items = BossData.itemsOf("VALERION", "NEBULA", "OPHANIM")
            } else if (dungeon?.name == "CELESTIALS_PROVINCE") {
                items = BossData.itemsOf("ASMODEUS", "SERAPHIM")
            }
            
            val message = buildString {
                append("<gradient:#B8FFE1:#7CFFB2:#2E8F78>Pity Checker</gradient><#555555>:</#555555> <#AAAAAA>$title\n\n")
                
                items.forEach { item ->
                    val coloredName = run {
                        val name = item.displayName
                        when (item.rarity.name) {
                            "IRRADIATED" -> "<#15cd15>$name"
                            "GILDED"     -> "<#df5320>$name"
                            "ROYAL"      -> "<#aa00aa>$name"
                            "BLOODSHOT"  -> "<#aa0000>$name"
                            "VOIDBOUND"  -> "<#8d15f0>$name"
                            "UNHOLY"     -> "<#bfbfbf>$name"
                            "COMPANION"  -> "<#ffaa00>$name"
                            "RUNE"       -> "<#616161>$name"
                            else         -> "<#AAAAAA>UNKNOWN</#AAAAAA>"
                        }
                    }
                    
                    val pity = DataConfig.getPityCounter(item.name)
                    val texture = item.texturePath
                    
                    append("<#FFFFFF><sprite:\"minecraft:blocks\":\"$texture\"></#FFFFFF> $coloredName<#555555>:</#555555> <#AAAAAA>$pity\n")
                }
            }
            
            Message.chat(message)
        }
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
}