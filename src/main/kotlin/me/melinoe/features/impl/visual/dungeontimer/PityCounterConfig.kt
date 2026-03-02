package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey

/**
 * Configuration for pity counter displays in dungeon completion messages.
 * This replaces the massive when-expression with a data-driven approach.
 */
object PityCounterConfig {
    
    /**
     * Represents a single pity counter display.
     */
    data class PityDisplay(
        val icon: String,
        val color: String,
        val label: String,
        val trackingKey: String
    )
    
    // Common pity displays
    private val BLOODSHOT = PityDisplay(Constants.ICON_BLOODSHOT, "§4", "Bloodshot", "")
    
    /**
     * Maps dungeons to their pity counter displays.
     * For multi-stage dungeons, use boss-specific mappings.
     */
    private val dungeonPityConfig = mapOf(
        // Endgame dungeons with both bloodshot and special drops
        DungeonData.SERAPHS_DOMAIN to listOf(
            BLOODSHOT.copy(trackingKey = "True Seraph Bloodshot"),
            PityDisplay("𕑦", "§f", "Holy Cross", "True Seraph")
        ),
        DungeonData.DAWN_OF_CREATION to listOf(
            BLOODSHOT.copy(trackingKey = "True Ophan Bloodshot"),
            PityDisplay("𕑦", "§f", "Pendant of Sin", "True Ophan")
        ),
        DungeonData.TENEBRIS to listOf(
            BLOODSHOT.copy(trackingKey = "Voided Omnipotent"),
            PityDisplay("𖈵", "§d", "Nihility", "Nihility")
        ),
        
        // Endgame dungeons with bloodshot only
        DungeonData.RAPHS_CHAMBER to listOf(
            BLOODSHOT.copy(trackingKey = "Raphael")
        ),
        DungeonData.RESOUNDING_RUINS to listOf(
            BLOODSHOT.copy(trackingKey = "Unrest")
        ),
        DungeonData.DREADWOOD_THICKET to listOf(
            BLOODSHOT.copy(trackingKey = "Sylvaris")
        ),
        
        // Boss dungeons with bloodshot drops
        DungeonData.ANUBIS_LAIR to listOf(
            BLOODSHOT.copy(trackingKey = "Kurvaros")
        ),
        DungeonData.THE_AVIARY to listOf(
            BLOODSHOT.copy(trackingKey = "Shadowflare")
        ),
        DungeonData.FUNGAL_CAVERN to listOf(
            BLOODSHOT.copy(trackingKey = "Prismara")
        ),
        DungeonData.CORSAIRS_CONDUCTORIUM to listOf(
            BLOODSHOT.copy(trackingKey = "Thalassar")
        ),
        DungeonData.OMNIPOTENTS_CITADEL to listOf(
            BLOODSHOT.copy(trackingKey = "Omnipotent")
        ),
        DungeonData.CULTISTS_HIDEOUT to listOf(
            BLOODSHOT.copy(trackingKey = "Silex")
        ),
        DungeonData.CHRONOS to listOf(
            BLOODSHOT.copy(trackingKey = "Chronos")
        ),
        DungeonData.ILLARIUS_HIDEOUT to listOf(
            BLOODSHOT.copy(trackingKey = "Loa")
        )
    )
    
    /**
     * Boss-specific pity displays for multi-stage dungeons.
     */
    private val bossPityConfig = mapOf(
        // Rustborn Kingdom bosses
        "Valerion" to listOf(BLOODSHOT.copy(trackingKey = "Valerion")),
        "Nebula" to listOf(BLOODSHOT.copy(trackingKey = "Nebula")),
        "Ophanim" to listOf(BLOODSHOT.copy(trackingKey = "Ophanim")),
        
        // Celestials Province bosses
        "Asmodeus" to listOf(BLOODSHOT.copy(trackingKey = "Asmodeus")),
        "Seraphim" to listOf(BLOODSHOT.copy(trackingKey = "Seraphim"))
    )
    
    /**
     * Builds the pity counter line for a dungeon/boss combination.
     * Returns empty string if no pity counters are configured.
     */
    fun buildPityLine(dungeon: DungeonData, boss: BossData): String {
        // Check for boss-specific pity first (for multi-stage dungeons)
        val displays = bossPityConfig[boss.label] ?: dungeonPityConfig[dungeon] ?: return ""
        
        if (displays.isEmpty()) return ""
        
        return displays.joinToString(" §8| §r") { display ->
            val pity = TypeSafeDataAccess.get(TrackingKey.PityCounter(display.trackingKey)) ?: 0
            "${display.icon} ${display.color}${display.label} §7($pity)"
        }
    }
}
