package me.melinoe.utils.data

import net.minecraft.core.BlockPos

/**
 * Boss data enum containing all bosses in Telos.
 */
enum class BossData(
    val label: String,
    val spawnPosition: BlockPos?,
    val bossType: BossType,
    val items: Array<Item> = emptyArray()
) {
    // World Bosses
    ANUBIS("Anubis", BlockPos(458, 204, -467), BossType.WORLD),
    ASTAROTH("Astaroth", BlockPos(250, 217, 60), BossType.WORLD),
    CHUNGUS("Chungus", BlockPos(61, 256, -490), BossType.WORLD),
    FREDDY("Freddy", BlockPos(-136, 200, 653), BossType.WORLD),
    GLUMI("Glumi", BlockPos(339, 222, 552), BossType.WORLD),
    ILLARIUS("Illarius", BlockPos(478, 200, -45), BossType.WORLD),
    LOTIL("Lotil", BlockPos(-138, 214, 17), BossType.WORLD),
    OOZUL("Oozul", BlockPos(-424, 195, 91), BossType.WORLD),
    TIDOL("Tidol", BlockPos(-543, 190, 364), BossType.WORLD),
    VALUS("Valus", BlockPos(35, 210, 307), BossType.WORLD),
    HOLLOWBANE("Hollowbane", BlockPos(232, 150, 696), BossType.WORLD),
    CLAUS("Claus", BlockPos(10, 212, -121), BossType.WORLD),
    WARDEN("Warden", null, BossType.WORLD, arrayOf(
        Item.WARDENS_FACEGUARD, Item.WARDENS_GARMENT, Item.DAWNBRINGER
    )),
    HERALD("Herald", null, BossType.WORLD, arrayOf(
        Item.HERALDIC_HEELGUARDS, Item.LUMINOUS_FLAME, Item.HERALDS_ESSENCE
    )),
    REAPER("Reaper", null, BossType.WORLD, arrayOf(
        Item.SOULLESS_SHOES, Item.CHALICE_OF_ROT, Item.REAPERS_VEST
    )),
    DEFENDER("Defender", null, BossType.WORLD, arrayOf(
        Item.SHROUDED_SHIELD, Item.PHANTASMIC_GREAVES, Item.AFTERBURNER
    )),
    
    // Lowlands Dungeon Bosses
    EDDIE("Eddie", null, BossType.DUNGEON, arrayOf(
        Item.BLUNDERBOW, Item.LOST_TREASURE_SCRIPTURE, Item.SLIME_ARCHER, Item.GOLDEN_STALLION
    )),
    ZHUM("Zhum", null, BossType.DUNGEON, arrayOf(
        Item.ASTEROID_STAFF, Item.AYAHUASCA_FLASK, Item.WARTORN_SANDALS, Item.THORNWOOD_DRUID, Item.TIMBERBEAST
    )),
    DRAYRUK("Drayruk", null, BossType.DUNGEON, arrayOf(
        Item.TITANIUM_CLEAVER, Item.DRAYRUKS_BATTLE_GREAVES, Item.ARROWSTORM_TRAP, Item.SHAMANOID, Item.GOBBLEHOP
    )),
    MIRAJ("Miraj", null, BossType.DUNGEON, arrayOf(
        Item.SCEPTRE_OF_THORNS, Item.SANDSTONE_EMBLEM, Item.LOST_CLERIC, Item.GRUMBLEGLOB
    )),
    KHUFU("Khufu", null, BossType.DUNGEON, arrayOf(
        Item.KHUFUS_SKULL, Item.VEIL_OF_SHADOWS, Item.HOOD_OF_STEALTH, Item.KHUFY, Item.SHADOW_SCORPID
    )),
    CHOJI("Choji", null, BossType.DUNGEON, arrayOf(
        Item.KENDO_STICK, Item.CHOJIS_TUNIC, Item.YINYANG, Item.ZENPANDA
    )),
    FLORA("Flora", null, BossType.DUNGEON, arrayOf(
        Item.FLORA_BOOTS, Item.CROSS_GRASS_DAGGER, Item.OVERGROWN_ORB, Item.UNDEAD_ENT, Item.THORNSCALE
    )),
    
    // Center Dungeon Bosses
    MALFAS("Malfas", null, BossType.DUNGEON, arrayOf(
        Item.DEMONIC_BLADE, Item.DEMONIC_DAGGER, Item.MAGMA_INFUSED_SCRIPTURE, Item.BLAZEY, Item.MAGMA_RUNNER
    )),
    HEPTAVIUS("Heptavius", null, BossType.DUNGEON, arrayOf(
        Item.DOOM_KATANA, Item.DOOM_BOW, Item.HAT_OF_LOST_SOULS, Item.DARK_KNIGHT, Item.NECROFOX
    )),
    ARCTIC_COLOSSUS("Arctic Colossus", null, BossType.DUNGEON, arrayOf(
        Item.FROZEN_STAR, Item.FROSTSPARK_SKULL, Item.FROZEN_JEWEL, Item.ICE_PENGUIN, Item.FROSTFANG
    )),
    FROSTGAZE("Frostgaze", null, BossType.DUNGEON, arrayOf(
        Item.DWARVEN_KUNAI, Item.DWARVEN_EMBLEM, Item.DWARVEN_CLOAK, Item.MINI_DWARF, Item.GLACIAL_RUNNER
    )),
    MAGNUS("Magnus", null, BossType.DUNGEON, arrayOf(
        Item.LIGHTNING_BOW, Item.LIGHTNING_CHAPS, Item.LIGHTNING_SABATONS, Item.GOLDEN_MARTIAL, Item.MIDAS_CONSTRUCT
    )),
    PYRO("Pyro", null, BossType.DUNGEON, arrayOf(
        Item.ORB_OF_TORMENT, Item.BOMB_OF_TORMENT, Item.CHESTPLATE_OF_TORMENT, Item.PRYOC, Item.HYDROSCORCH
    )),
    THALOR("Thalor", null, BossType.DUNGEON, arrayOf(
        Item.SHIELD_OF_GLACIAL_REFLECTION, Item.FROST_LEGGINGS, Item.GLACIAL_BOMB, Item.THAILY, Item.WINTERBEAST
    )),
    ASHENCLAW("Ashenclaw", null, BossType.DUNGEON, arrayOf(
        Item.EMBERHEART_STAFF, Item.VENOMFANG_SERUM, Item.LIZARDSKIN_SPELLCOAT, Item.BLADE, Item.FIRECLAW
    )),
    CORVACK("Corvack", null, BossType.DUNGEON, arrayOf(
        Item.CORVUS_SCEPTRE, Item.CORVUS_TRAP, Item.CORVACKS_MASK, Item.DARK_ALCHEMIST, Item.SHADOWCLAW
    )),
    
    // Boss Dungeon Bosses
    OMNIPOTENT("Omnipotent", null, BossType.DUNGEON, arrayOf(
        Item.SCEPTRE_OF_GLOOM, Item.OMNIPOTENT_ROBE, Item.OMNIPOTENT_LEGGINGS, Item.BLACKHOLE_SINGULARITY,
        Item.MINI_ARCHMAGE, Item.ETHEREALMARE, Item.MAGE_RUNE
    )),
    PRISMARA("Prismara", null, BossType.DUNGEON, arrayOf(
        Item.CRYSTAL_TRAP, Item.CRYSTAL_SHIELD, Item.CRYSTAL_BOOTS, Item.AMETHYST_WORMPIERCER,
        Item.MINOBI, Item.MYCOHAWK, Item.CRYSTAL_RUNE
    )),
    THALASSAR("Thalassar", null, BossType.DUNGEON, arrayOf(
        Item.SPIRIT_DAGGER, Item.ETHEREAL_LONGBOW, Item.SPECTRAL_ARMOUR, Item.OCEANIC_TURRENT,
        Item.MINI_ILLUSIONIST, Item.SPECTRAL_STEED, Item.SPECTRAL_TIDES_RUNE
    )),
    GOLDEN_FREDDY("Golden Freddy", null, BossType.DUNGEON, arrayOf(
        Item.ENDOSKELETON_SCEPTRE, Item.CARL_TRAP, Item.MECHANICAL_FUR_SANDALS,
        Item.BARD, Item.MECHAMAJESTIC, Item.FAZZBEAR_RUNE
    )),
    CHRONOS("Chronos", null, BossType.DUNGEON, arrayOf(
        Item.CHRONOS_SABATONS, Item.CHRONOS_HEART, Item.CHRONOS_LEGGINGS, Item.ROBE_OF_CHRONOS,
        Item.NIGHT_RISER, Item.PHANTOMMARE, Item.TIME_RUNE
    )),
    KURVAROS("Kurvaros", null, BossType.DUNGEON, arrayOf(
        Item.MECHANICAL_CUTLASS, Item.BLACKHOLE_SANDALS, Item.VOLTAIC_BOMB, Item.CLOAK_OF_IONISED_DARKNESS,
        Item.BEAST_MASTER, Item.NECROWYRM, Item.GEARS_RUNE
    )),
    SILEX("Silex", null, BossType.DUNGEON, arrayOf(
        Item.SOULREND_RAVAGER, Item.STAFF_OF_UNHOLY_SACRIFICE, Item.CULTIST_HOOD, Item.SOULS_OF_THE_CULT,
        Item.LIL_MONK, Item.DUSK_MANTICULTIST, Item.CULT_RUNE
    )),
    LOA("Loa", null, BossType.DUNGEON, arrayOf(
        Item.BLOSSOM_BLADE, Item.DILAPIDATED_SKULL, Item.GREATWOOD_GREAVES, Item.BOOK_OF_LIFE,
        Item.ELDER_ENT, Item.WOODLAND_MANTIBANE, Item.NATURE_RUNE
    )),
    SHADOWFLARE("Shadowflare", null, BossType.DUNGEON, arrayOf(
        Item.AERIAL_STAFF, Item.ORB_OF_PURITY, Item.PHOENIX_PLATE, Item.RESURGENCE,
        Item.DRAGON_WARRIOR, Item.SUNFLARE, Item.SOL_RUNE
    )),
    DARK_CHAMPIONS("Dark Champion", null, BossType.DUNGEON),
    AETHERIS("Aetheris", null, BossType.DUNGEON, arrayOf(
        Item.LIGHTS_PULL, Item.DAGGER_OF_DISSONANCE, Item.HEAVENS_AUGMENTATION, Item.SUNS_BLESSING,
        Item.RADLE, Item.GLOWSCALE, Item.GLASS_SKY_RUNE
    )),
    MALTHAR("Malthar", null, BossType.DUNGEON, arrayOf(
        Item.CINDER_REACH, Item.CINDER_SKULL, Item.SCORCHSCALE_LEGGINGS,
        Item.TARTALINK, Item.EMBERAPTOR, Item.PENTACURSE_RUNE
    )),
    
    // Endgame Dungeon Bosses
    RAPHAEL("Raphael", BlockPos(-15, 243, 88), BossType.DUNGEON, arrayOf(
        Item.CALAMITY, Item.RETRIBUTION, Item.RAPTURE, Item.MARTYR, Item.REIKON, Item.MALICE,
        Item.WRATHGUARD, Item.BLOODSTRIDERS, Item.RUEFORGE, Item.SOURCESTONE,
        Item.EXALTLING, Item.CELESTIAL_SERPENT, Item.FALLEN_RUNE
    )),
    VOIDED_OMNIPOTENT("Voided Omnipotent", null, BossType.DUNGEON, arrayOf(
        Item.EVENT_HORIZON, Item.OBLIVIONS_REACH, Item.SHADOW_SILK, Item.FRACTURED_NIHILITY,
        Item.SHADOW_CHIRP, Item.DREADSPIRE_DRAKE, Item.ETERNAL_MAW_RUNE
    )),
    VALERION("Valerion", null, BossType.DUNGEON, arrayOf(
        Item.VALERIONS_PONIARD, Item.VALERIONS_BANE, Item.CUSTODIANS_VISOR
    )),
    NEBULA("Nebula", null, BossType.DUNGEON, arrayOf(
        Item.NEBULAS_BATTLEAXE, Item.DUSK_WEAVER, Item.ARCHONS_GLARE
    )),
    OPHANIM("Ophanim", null, BossType.DUNGEON, arrayOf(
        Item.FINAL_DESTINATION, Item.ORACLES_END, Item.CROWN_OF_ETHEREAL_RADIANCE,
        Item.LUMINE, Item.ASTRALFLARE, Item.OBSERVER_RUNE
    )),
    TRUE_OPHAN("True Ophan", null, BossType.DUNGEON, arrayOf(
        Item.H_VALERIONS_PONIARD, Item.H_VALERIONS_BANE, Item.H_CUSTODIANS_VISOR,
        Item.H_NEBULAS_BATTLEAXE, Item.H_DUSK_WEAVER, Item.H_ARCHONS_GLARE,
        Item.H_FINAL_DESTINATION, Item.H_ORACLES_END, Item.H_CROWN_OF_ETHEREAL_RADIANCE,
        Item.PENDANT_OF_SIN, Item.H_LUMINE, Item.H_ASTRALFLARE, Item.H_OBSERVER_RUNE
    )),
    SYLVARIS("Sylvaris", null, BossType.DUNGEON, arrayOf(
        Item.SCRIPTURE_OF_THE_TOTEM_MASTER, Item.NATURES_GIFT, Item.SPIRITBLOOM_GOWN,
        Item.MOSSLIGHT, Item.VINEGLEAM, Item.DREAD_RUNE
    )),
    ASMODEUS("Asmodeus", null, BossType.DUNGEON, arrayOf(
        Item.VISAGE_OF_THE_NIGHT, Item.BAPHOMETS_BOMB, Item.BLOOD_OF_THE_HERETICS
    )),
    SERAPHIM("Seraphim", null, BossType.DUNGEON, arrayOf(
        Item.FEATHERS_OF_THE_SERAPH, Item.SERAPHIC_SHIV, Item.EMPYREAN_EPITOME,
        Item.THANATOS, Item.SERAPHIX, Item.SERAPH_RUNE
    )),
    TRUE_SERAPH("True Seraph", null, BossType.DUNGEON, arrayOf(
        Item.H_VISAGE_OF_THE_NIGHT, Item.H_BAPHOMETS_BOMB, Item.H_BLOOD_OF_THE_HERETICS,
        Item.H_FEATHERS_OF_THE_SERAPH, Item.H_SERAPHIC_SHIV, Item.H_EMPYREAN_EPITOME,
        Item.HOLY_CROSS, Item.H_THANATOS, Item.H_SERAPHIX, Item.H_SERAPH_RUNE
    )),
    UNREST("Unrest", null, BossType.DUNGEON, arrayOf(
        Item.SONIC_DOOM, Item.ECHOLURKATOR, Item.SPINESTONE,
        Item.LEGION, Item.ECHORAPTOR, Item.ECHOES_RUNE
    ));

    companion object {
        private val bossDataMap: Map<String, BossData> = values().associateBy { it.label }

        /**
         * Find a boss by its label/name.
         */
        fun findByKey(name: String): BossData? = bossDataMap[name]

        /**
         * Find a boss by its enum name (case-insensitive).
         */
        fun fromString(name: String): BossData? = try {
            valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun toString(): String = label
}
