package me.melinoe.utils.data

/**
 * Dungeon data enum containing all dungeons in Telos.
 */
enum class DungeonData(
    val areaName: String,
    val dungeonType: DungeonDifficulty,
    val finalBoss: BossData
) {
    // Lowlands Dungeons
    SKULL_CAVERN("Skull Cavern", DungeonDifficulty.LOWLANDS, BossData.EDDIE),
    THORNWOOD_WARGROVE("Thornwood Wargrove", DungeonDifficulty.LOWLANDS, BossData.ZHUM),
    GOBLIN_LAIR("Goblin Lair", DungeonDifficulty.LOWLANDS, BossData.DRAYRUK),
    DESERT_TEMPLE("Desert Temple", DungeonDifficulty.LOWLANDS, BossData.MIRAJ),
    TOMB_OF_SHADOWS("Tomb of Shadows", DungeonDifficulty.LOWLANDS, BossData.KHUFU),
    SAKURA_SHRINE("Sakura Shrine", DungeonDifficulty.LOWLANDS, BossData.CHOJI),
    SECLUDED_WOODLAND("Secluded Woodland", DungeonDifficulty.LOWLANDS, BossData.FLORA),

    // Center Dungeons
    ABYSS_OF_DEMONS("Abyss of Demons", DungeonDifficulty.CENTER, BossData.MALFAS),
    UNDEAD_LAIR("Undead Lair", DungeonDifficulty.CENTER, BossData.HEPTAVIUS),
    ICE_CAVE("Ice Cave", DungeonDifficulty.CENTER, BossData.ARCTIC_COLOSSUS),
    DWARVEN_FROSTKEEP("Dwarven Frostkeep", DungeonDifficulty.CENTER, BossData.FROSTGAZE),
    TREASURE_CAVE("Treasure Cave", DungeonDifficulty.CENTER, BossData.MAGNUS),
    DEPTHS_OF_PURGATORY("Depths of Purgatory", DungeonDifficulty.CENTER, BossData.PYRO),
    FROZEN_RUINS("Frozen Ruins", DungeonDifficulty.CENTER, BossData.THALOR),
    KOBOLDS_DEN("Kobold's Den", DungeonDifficulty.CENTER, BossData.ASHENCLAW),
    CORVUS_CRYPT("Corvus Crypt", DungeonDifficulty.CENTER, BossData.CORVACK),

    // Boss Dungeons
    OMNIPOTENTS_CITADEL("Omnipotent's Citadel", DungeonDifficulty.BOSS, BossData.OMNIPOTENT),
    FUNGAL_CAVERN("Fungal Cavern", DungeonDifficulty.BOSS, BossData.PRISMARA),
    CORSAIRS_CONDUCTORIUM("Corsair's Conductorium", DungeonDifficulty.BOSS, BossData.THALASSAR),
    FREDDYS_PIZZERIA("Freddy's Pizzeria", DungeonDifficulty.BOSS, BossData.GOLDEN_FREDDY),
    CHRONOS("Chronos", DungeonDifficulty.BOSS, BossData.CHRONOS),
    ANUBIS_LAIR("Anubis Lair", DungeonDifficulty.BOSS, BossData.KURVAROS),
    CULTISTS_HIDEOUT("Cultist's Hideout", DungeonDifficulty.BOSS, BossData.SILEX),
    ILLARIUS_HIDEOUT("Illarius' Hideout", DungeonDifficulty.BOSS, BossData.LOA),
    THE_AVIARY("The Aviary", DungeonDifficulty.BOSS, BossData.SHADOWFLARE),
    RAPHS_CASTLE("Raphael's Castle", DungeonDifficulty.BOSS, BossData.DARK_CHAMPIONS),
    AURORA_SANCTUM("Aurora Sanctum", DungeonDifficulty.BOSS, BossData.AETHERIS),
    TARTARUS("Tartarus", DungeonDifficulty.BOSS, BossData.MALTHAR),

    // Endgame Dungeons
    DAWN_OF_CREATION("Dawn of Creation", DungeonDifficulty.ENDGAME, BossData.TRUE_OPHAN),
    RAPHS_CHAMBER("Raphael's Chamber", DungeonDifficulty.ENDGAME, BossData.RAPHAEL),
    TENEBRIS("Tenebris", DungeonDifficulty.ENDGAME, BossData.VOIDED_OMNIPOTENT),
    RUSTBORN_KINGDOM("Rustborn Kingdom", DungeonDifficulty.ENDGAME, BossData.OPHANIM),
    DREADWOOD_THICKET("Dreadwood Thicket", DungeonDifficulty.ENDGAME, BossData.SYLVARIS),
    CELESTIALS_PROVINCE("Celestial's Province", DungeonDifficulty.ENDGAME, BossData.SERAPHIM),
    SERAPHS_DOMAIN("Seraph's Domain", DungeonDifficulty.ENDGAME, BossData.TRUE_SERAPH),
    RESOUNDING_RUINS("Resounding Ruins", DungeonDifficulty.ENDGAME, BossData.UNREST);

    companion object {
        private val areaNameMap: Map<String, DungeonData> = values().associateBy { it.areaName }

        /**
         * Find a dungeon by its area name.
         */
        fun findByKey(areaName: String?): DungeonData? = if (areaName != null) areaNameMap[areaName] else null
    }
}
