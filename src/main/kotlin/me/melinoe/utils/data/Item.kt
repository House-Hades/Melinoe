package me.melinoe.utils.data

/**
 * Item enum representing all droppable items in Telos.
 * Each item has a rarity, display name, texture path, and max pity value.
 */
enum class Item(
    val rarity: Rarity,
    val displayName: String,
    val texturePath: String,
    val maxPity: Int
) {
    // Lowlands Dungeons (IRRADIATED rarity)
    // Skull Cavern (Eddie)
    BLUNDERBOW(Rarity.IRRADIATED, "Blunderbow", "telos:material/weapon/bow/ut-blunder", 120),
    LOST_TREASURE_SCRIPTURE(Rarity.IRRADIATED, "Lost Treasure Scripture", "telos:material/ability/scripture/ut-treasure", 120),
    SLIME_ARCHER(Rarity.COMPANION, "Slime Archer", "telos:material/pet/skull_cavern", 120),
    GOLDEN_STALLION(Rarity.COMPANION, "Golden Stallion", "telos:material/mount/skull_cavern", 120),
    
    // Thornwood Wargrove (Zhum)
    ASTEROID_STAFF(Rarity.IRRADIATED, "Asteroid Staff", "telos:material/weapon/staff/ut-asteroid", 120),
    AYAHUASCA_FLASK(Rarity.IRRADIATED, "Ayahuasca Flask", "telos:material/ability/poison/ut-flask", 120),
    WARTORN_SANDALS(Rarity.IRRADIATED, "Wartorn Sandals", "telos:material/armour/light/boots/ut-wartorn", 120),
    THORNWOOD_DRUID(Rarity.COMPANION, "Thornwood Druid", "telos:material/pet/thornwood_wargrove", 120),
    TIMBERBEAST(Rarity.COMPANION, "Timberbeast", "telos:material/mount/thornwood_wargrove", 120),
    
    // Goblin Lair (Drayruk)
    TITANIUM_CLEAVER(Rarity.IRRADIATED, "Titanium Cleaver", "telos:material/weapon/sword/ut-cleaver", 120),
    DRAYRUKS_BATTLE_GREAVES(Rarity.IRRADIATED, "Drayruk's Battle Greaves", "telos:material/armour/heavy/leggings/ut-drayruk", 120),
    ARROWSTORM_TRAP(Rarity.IRRADIATED, "Arrowstorm Trap", "telos:material/ability/trap/ut-arrowstorm", 120),
    SHAMANOID(Rarity.COMPANION, "Shamanoid", "telos:material/pet/goblin_lair", 120),
    GOBBLEHOP(Rarity.COMPANION, "Gobblehop", "telos:material/mount/goblin_lair", 120),
    
    // Desert Temple (Miraj)
    SCEPTRE_OF_THORNS(Rarity.IRRADIATED, "Sceptre of Thorns", "telos:material/weapon/sceptre/ut-thorns", 120),
    SANDSTONE_EMBLEM(Rarity.IRRADIATED, "Sandstone Emblem", "telos:material/ability/emblem/ut-sandstone", 120),
    LOST_CLERIC(Rarity.COMPANION, "Lost Cleric", "telos:material/pet/desert_temple", 120),
    GRUMBLEGLOB(Rarity.COMPANION, "Grumbleglob", "telos:material/mount/desert_temple", 120),
    
    // Tomb of Shadows (Khufu)
    KHUFUS_SKULL(Rarity.IRRADIATED, "Khufu's Skull", "telos:material/ability/skull/ut-khufu", 120),
    VEIL_OF_SHADOWS(Rarity.IRRADIATED, "Veil of Shadows", "telos:material/ability/cloak/ut-shadows", 120),
    HOOD_OF_STEALTH(Rarity.IRRADIATED, "Hood of Stealth", "telos:material/armour/magical/helmet/ut-stealth", 120),
    KHUFY(Rarity.COMPANION, "Khufy", "telos:material/pet/tomb_of_shadows", 120),
    SHADOW_SCORPID(Rarity.COMPANION, "Shadow Scorpid", "telos:material/mount/tomb_of_shadows", 120),
    
    // Sakura Shrine (Choji)
    KENDO_STICK(Rarity.IRRADIATED, "Kendo Stick", "telos:material/weapon/katana/ut-kendo", 120),
    CHOJIS_TUNIC(Rarity.IRRADIATED, "Choji's Tunic", "telos:material/armour/light/chestplate/ut-tunic", 120),
    YINYANG(Rarity.COMPANION, "YinYang", "telos:material/pet/sakura_shrine", 120),
    ZENPANDA(Rarity.COMPANION, "Zenpanda", "telos:material/mount/sakura_shrine", 120),
    
    // Secluded Woodland (Flora)
    FLORA_BOOTS(Rarity.IRRADIATED, "Flora Boots", "telos:material/armour/magical/boots/ut-flora", 120),
    CROSS_GRASS_DAGGER(Rarity.IRRADIATED, "Cross Grass Dagger", "telos:material/weapon/dagger/ut-grass", 120),
    OVERGROWN_ORB(Rarity.IRRADIATED, "Overgrown Orb", "telos:material/ability/orb/ut-overgrown", 120),
    UNDEAD_ENT(Rarity.COMPANION, "Undead Ent", "telos:material/pet/secluded_woodland", 120),
    THORNSCALE(Rarity.COMPANION, "Thornscale", "telos:material/mount/secluded_woodland", 120),
    
    // Center Dungeons (GILDED rarity)
    // Abyss of Demons (Malfas)
    DEMONIC_BLADE(Rarity.GILDED, "Demonic Blade", "telos:material/weapon/sword/ut-demonic", 120),
    DEMONIC_DAGGER(Rarity.GILDED, "Demonic Dagger", "telos:material/weapon/dagger/ut-demonic", 120),
    MAGMA_INFUSED_SCRIPTURE(Rarity.GILDED, "Magma Infused Scripture", "telos:material/ability/scripture/ut-magma", 120),
    BLAZEY(Rarity.COMPANION, "Blazey", "telos:material/pet/abyss_of_demons", 120),
    MAGMA_RUNNER(Rarity.COMPANION, "Magma Runner", "telos:material/mount/abyss_of_demons", 120),
    
    // Undead Lair (Heptavius)
    DOOM_KATANA(Rarity.GILDED, "Doom Katana", "telos:material/weapon/katana/ut-doom", 120),
    DOOM_BOW(Rarity.GILDED, "Doom Bow", "telos:material/weapon/bow/ut-doom", 120),
    HAT_OF_LOST_SOULS(Rarity.GILDED, "Hat of Lost Souls", "telos:material/armour/light/helmet/ut-soul", 120),
    DARK_KNIGHT(Rarity.COMPANION, "Dark Knight", "telos:material/pet/undead_lair", 120),
    NECROFOX(Rarity.COMPANION, "Necrofox", "telos:material/mount/undead_lair", 120),
    
    // Ice Cave (Arctic Colossus)
    FROZEN_STAR(Rarity.GILDED, "Frozen Star", "telos:material/ability/star/ut-frozen", 120),
    FROSTSPARK_SKULL(Rarity.GILDED, "Frostspark Skull", "telos:material/ability/skull/ut-frostspark", 120),
    FROZEN_JEWEL(Rarity.GILDED, "Frozen Jewel", "telos:material/ability/jewel/ut-frozen", 120),
    ICE_PENGUIN(Rarity.COMPANION, "Ice Penguin", "telos:material/pet/ice_cave", 120),
    FROSTFANG(Rarity.COMPANION, "Frostfang", "telos:material/mount/ice_cave", 120),
    
    // Dwarven Frostkeep (Frostgaze)
    DWARVEN_KUNAI(Rarity.GILDED, "Dwarven Kunai", "telos:material/ability/kunai/ut-dwarven", 120),
    DWARVEN_EMBLEM(Rarity.GILDED, "Dwarven Emblem", "telos:material/ability/emblem/ut-dwarven", 120),
    DWARVEN_CLOAK(Rarity.GILDED, "Dwarven Cloak", "telos:material/ability/cloak/ut-dwarven", 120),
    MINI_DWARF(Rarity.COMPANION, "Mini Dwarf", "telos:material/pet/dwarven_frostkeep", 120),
    GLACIAL_RUNNER(Rarity.COMPANION, "Glacial Runner", "telos:material/mount/dwarven_frostkeep", 120),
    
    // Treasure Cave (Magnus)
    LIGHTNING_BOW(Rarity.GILDED, "Lightning Bow", "telos:material/weapon/bow/ut-lightning", 120),
    LIGHTNING_CHAPS(Rarity.GILDED, "Lightning Chaps", "telos:material/armour/light/leggings/ut-lightning", 120),
    LIGHTNING_SABATONS(Rarity.GILDED, "Lightning Sabatons", "telos:material/armour/heavy/boots/ut-lightning", 120),
    GOLDEN_MARTIAL(Rarity.COMPANION, "Golden Martial", "telos:material/pet/treasure_cave", 120),
    MIDAS_CONSTRUCT(Rarity.COMPANION, "Midas Construct", "telos:material/mount/treasure_cave", 120),
    
    // Depths of Purgatory (Pyro)
    ORB_OF_TORMENT(Rarity.GILDED, "Orb of Torment", "telos:material/ability/orb/ut-torment", 120),
    BOMB_OF_TORMENT(Rarity.GILDED, "Bomb of Torment", "telos:material/ability/bomb/ut-torment", 120),
    CHESTPLATE_OF_TORMENT(Rarity.GILDED, "Chestplate of Torment", "telos:material/armour/heavy/chestplate/ut-torment", 120),
    PRYOC(Rarity.COMPANION, "Pryoc", "telos:material/pet/purgatory", 120),
    HYDROSCORCH(Rarity.COMPANION, "Hydroscorch", "telos:material/mount/purgatory", 120),
    
    // Frozen Ruins (Thalor)
    SHIELD_OF_GLACIAL_REFLECTION(Rarity.GILDED, "Shield of Glacial Reflection", "telos:material/ability/shield/ut-reflection", 120),
    FROST_LEGGINGS(Rarity.GILDED, "Frost Leggings", "telos:material/armour/magical/leggings/ut-frost", 120),
    GLACIAL_BOMB(Rarity.GILDED, "Glacial Bomb", "telos:material/ability/bomb/ut-glacial", 120),
    THAILY(Rarity.COMPANION, "Thaily", "telos:material/pet/frozen_ruins", 120),
    WINTERBEAST(Rarity.COMPANION, "Winterbeast", "telos:material/mount/frozen_ruins", 120),
    
    // Kobold's Den (Ashenclaw)
    EMBERHEART_STAFF(Rarity.GILDED, "Emberheart Staff", "telos:material/weapon/staff/ut-emberheart", 120),
    VENOMFANG_SERUM(Rarity.GILDED, "Venomfang Serum", "telos:material/ability/poison/ut-venomfang", 120),
    LIZARDSKIN_SPELLCOAT(Rarity.GILDED, "Lizardskin Spellcoat", "telos:material/armour/magical/chestplate/ut-lizardskin", 120),
    BLADE(Rarity.COMPANION, "Blade", "telos:material/pet/kobolds_den", 120),
    FIRECLAW(Rarity.COMPANION, "Fireclaw", "telos:material/mount/kobolds_den", 120),
    
    // Corvus Crypt (Corvack)
    CORVUS_SCEPTRE(Rarity.GILDED, "Corvus Sceptre", "telos:material/weapon/sceptre/ut-corvus", 120),
    CORVUS_TRAP(Rarity.GILDED, "Corvus Trap", "telos:material/ability/trap/ut-corvus", 120),
    CORVACKS_MASK(Rarity.GILDED, "Corvack's Mask", "telos:material/armour/heavy/helmet/ut-corvack", 120),
    DARK_ALCHEMIST(Rarity.COMPANION, "Dark Alchemist", "telos:material/pet/corvus_crypt", 120),
    SHADOWCLAW(Rarity.COMPANION, "Shadowclaw", "telos:material/mount/corvus_crypt", 120),
    
    // World Bosses (ROYAL rarity)
    CARROT_ON_A_STICK(Rarity.ROYAL, "Carrot on a Stick", "telos:material/weapon/katana/ut-carrot", 120),
    SPRING_SEASONED_SCRIPTURE(Rarity.ROYAL, "Spring Seasoned Scripture", "telos:material/ability/scripture/ut-spring", 120),
    BOW_OF_THE_FOREST(Rarity.ROYAL, "Bow of the Forest", "telos:material/weapon/bow/ut-forest", 120),
    CURSED_BRIGADINE(Rarity.ROYAL, "Cursed Brigadine", "telos:material/armour/light/chestplate/ut-brigadine", 120),
    ORB_OF_CONFLICT(Rarity.ROYAL, "Orb of Conflict", "telos:material/ability/orb/ut-conflict", 120),
    KUNAI_OF_CONFLICT(Rarity.ROYAL, "Kunai of Conflict", "telos:material/ability/kunai/ut-conflict", 120),
    CRYSTAL_POISON(Rarity.ROYAL, "Crystal Poison", "telos:material/ability/poison/ut-crystal", 120),
    CRYSTAL_KUNAI(Rarity.ROYAL, "Crystal Kunai", "telos:material/ability/kunai/ut-crystal", 120),
    SHIELD_OF_OGMUR(Rarity.ROYAL, "Shield of Ogmur", "telos:material/ability/shield/ut-ogmur", 120),
    CLOAK_OF_THE_WARLORD(Rarity.ROYAL, "Cloak of the Warlord", "telos:material/ability/cloak/ut-warlord", 120),
    EMBLEM_OF_THE_JUGGERNAUT(Rarity.ROYAL, "Emblem of the Juggernaut", "telos:material/ability/emblem/ut-juggernaut", 120),
    GREAVES_OF_THE_JUGGERNAUT(Rarity.ROYAL, "Greaves of the Juggernaut", "telos:material/armour/heavy/leggings/ut-crayfish", 120),
    CLOAK_OF_BLOODY_SURPRISES(Rarity.ROYAL, "Cloak of Bloody Surprises", "telos:material/ability/cloak/ut-bloody", 120),
    SPOOKY_SANDALS(Rarity.ROYAL, "Spooky Sandals", "telos:material/armour/light/boots/ut-spooky", 120),
    DIMENSIONAL_STAR(Rarity.ROYAL, "Dimensional Star", "telos:material/ability/star/ut-dimensional", 120),
    DIRK_OF_CHRONOS(Rarity.ROYAL, "Dirk of Chronos", "telos:material/weapon/dagger/ut-chronos", 120),
    EXOSKELETON_HOOD(Rarity.ROYAL, "Exoskeleton Hood", "telos:material/armour/magical/helmet/ut-exoskeleton", 120),
    FREDDYS_MICROPHONE(Rarity.ROYAL, "Freddy's Microphone", "telos:material/weapon/sword/ut-microphone", 120),
    ANUBIS_STAFF(Rarity.ROYAL, "Anubis Staff", "telos:material/weapon/staff/ut-anubis", 120),
    JEWEL_OF_THE_NILE(Rarity.ROYAL, "Jewel of the Nile", "telos:material/ability/jewel/ut-nile", 120),

    // Omnipotent's Citadel (Omnipotent)
    SCEPTRE_OF_GLOOM(Rarity.ROYAL, "Sceptre of Gloom", "telos:material/weapon/sceptre/ut-gloom", 120),
    OMNIPOTENT_ROBE(Rarity.ROYAL, "Omnipotent Robe", "telos:material/armour/magical/chestplate/ut-omnipotent", 120),
    OMNIPOTENT_LEGGINGS(Rarity.ROYAL, "Omnipotent Leggings", "telos:material/armour/magical/leggings/ut-omnipotent", 120),
    BLACKHOLE_SINGULARITY(Rarity.BLOODSHOT, "Blackhole Singularity", "telos:material/ability/trap/ut-blackhole", 120),
    MINI_ARCHMAGE(Rarity.COMPANION, "Mini Archmage", "telos:material/pet/omnipotents_citadel", 120),
    ETHEREALMARE(Rarity.COMPANION, "Etherealmare", "telos:material/mount/omnipotents_citadel", 120),
    MAGE_RUNE(Rarity.RUNE, "Rune (Mage)", "telos:material/rune/omnipotents_citadel", 120),

    // Fungal Cavern (Prismara)
    CRYSTAL_TRAP(Rarity.ROYAL, "Crystal Trap", "telos:material/ability/trap/ut-crystal", 120),
    CRYSTAL_SHIELD(Rarity.ROYAL, "Crystal Shield", "telos:material/ability/shield/ut-crystal", 120),
    CRYSTAL_BOOTS(Rarity.ROYAL, "Crystal Boots", "telos:material/armour/magical/boots/ut-fungal", 120),
    AMETHYST_WORMPIERCER(Rarity.BLOODSHOT, "Amethyst Wormpiercer", "telos:material/weapon/bow/ut-amethyst", 120),
    MINOBI(Rarity.COMPANION, "Minobi", "telos:material/pet/fungal_cavern", 120),
    MYCOHAWK(Rarity.COMPANION, "Mycohawk", "telos:material/mount/fungal_cavern", 120),
    CRYSTAL_RUNE(Rarity.RUNE, "Rune (Crystal)", "telos:material/rune/fungal_cavern", 120),

    // Corsair's Conductorium (Thalassar)
    SPIRIT_DAGGER(Rarity.ROYAL, "Spirit Dagger", "telos:material/weapon/dagger/ut-spirit", 120),
    ETHEREAL_LONGBOW(Rarity.ROYAL, "Ethereal Longbow", "telos:material/weapon/bow/ut-ethereal", 120),
    SPECTRAL_ARMOUR(Rarity.ROYAL, "Spectral Armour", "telos:material/armour/light/chestplate/ut-spectral", 120),
    OCEANIC_TURRENT(Rarity.BLOODSHOT, "Oceanic Turrent", "telos:material/ability/shield/ut-torrent", 120),
    MINI_ILLUSIONIST(Rarity.COMPANION, "Mini Illusionist", "telos:material/pet/corsairs_conductorium", 120),
    SPECTRAL_STEED(Rarity.COMPANION, "Spectral Steed", "telos:material/mount/corsairs_conductorium", 120),
    SPECTRAL_TIDES_RUNE(Rarity.RUNE, "Rune (Spectral Tides)", "telos:material/rune/corsairs_conductorium", 120),

    // Freddy's Pizzeria (Golden Freddy)
    ENDOSKELETON_SCEPTRE(Rarity.ROYAL, "Endoskeleton Sceptre", "telos:material/weapon/sceptre/ut-endoskeleton", 120),
    CARL_TRAP(Rarity.ROYAL, "Carl Trap", "telos:material/ability/trap/ut-cupcake", 120),
    MECHANICAL_FUR_SANDALS(Rarity.ROYAL, "Mechanical Fur Sandals", "telos:material/armour/light/boots/ut-mechanical", 120),
    BARD(Rarity.COMPANION, "Bard", "telos:material/pet/freddys_pizzeria", 120),
    MECHAMAJESTIC(Rarity.COMPANION, "Mechamajestic", "telos:material/mount/freddys_pizzeria", 120),
    FAZZBEAR_RUNE(Rarity.RUNE, "Rune (Fazzbear)", "telos:material/rune/freddys_pizzeria", 120),

    // Chronos (Chronos)
    CHRONOS_SABATONS(Rarity.ROYAL, "Chronos Sabatons", "telos:material/armour/heavy/boots/ut-chronos", 120),
    CHRONOS_HEART(Rarity.ROYAL, "Chronos Heart", "telos:material/ability/skull/ut-heart", 120),
    CHRONOS_LEGGINGS(Rarity.ROYAL, "Chronos Leggings", "telos:material/armour/magical/leggings/ut-chronos", 120),
    ROBE_OF_CHRONOS(Rarity.BLOODSHOT, "Robe of Chronos", "telos:material/armour/magical/chestplate/ut-chronos", 120),
    NIGHT_RISER(Rarity.COMPANION, "Night Riser", "telos:material/pet/chronos", 120),
    PHANTOMMARE(Rarity.COMPANION, "Phantommare", "telos:material/mount/chronos", 120),
    TIME_RUNE(Rarity.RUNE, "Rune (Time)", "telos:material/rune/chronos", 120),

    // Anubis Lair (Kurvaros)
    MECHANICAL_CUTLASS(Rarity.ROYAL, "Mechanical Cutlass", "telos:material/weapon/sword/ut-cutlass", 120),
    BLACKHOLE_SANDALS(Rarity.ROYAL, "Blackhole Sandals", "telos:material/armour/light/boots/ut-blackhole", 120),
    VOLTAIC_BOMB(Rarity.ROYAL, "Voltaic Bomb", "telos:material/ability/bomb/ut-voltaic", 120),
    CLOAK_OF_IONISED_DARKNESS(Rarity.BLOODSHOT, "Cloak of Ionised Darkness", "telos:material/ability/cloak/ut-darkness", 120),
    BEAST_MASTER(Rarity.COMPANION, "Beast Master", "telos:material/pet/anubis_lair", 120),
    NECROWYRM(Rarity.COMPANION, "Necrowyrm", "telos:material/mount/anubis_lair", 120),
    GEARS_RUNE(Rarity.RUNE, "Rune (Gears)", "telos:material/rune/anubis_lair", 120),

    // Cultist's Hideout (Silex)
    SOULREND_RAVAGER(Rarity.ROYAL, "Soulrend Ravager", "telos:material/weapon/katana/ut-soulrend", 120),
    STAFF_OF_UNHOLY_SACRIFICE(Rarity.ROYAL, "Staff of Unholy Sacrifice", "telos:material/weapon/staff/ut-unholy", 120),
    CULTIST_HOOD(Rarity.ROYAL, "Cultist Hood", "telos:material/armour/magical/helmet/ut-cultist", 120),
    SOULS_OF_THE_CULT(Rarity.BLOODSHOT, "Souls of the Cult", "telos:material/ability/jewel/ut-souls", 120),
    LIL_MONK(Rarity.COMPANION, "Lil' Monk", "telos:material/pet/cultists_hideout", 120),
    DUSK_MANTICULTIST(Rarity.COMPANION, "Dusk Manticultist", "telos:material/mount/cultists_hideout", 120),
    CULT_RUNE(Rarity.RUNE, "Rune (Cult)", "telos:material/rune/cultists_hideout", 120),

    // Illarius' Hideout (Loa)
    BLOSSOM_BLADE(Rarity.ROYAL, "Blossom Blade", "telos:material/weapon/katana/ut-blossom", 120),
    DILAPIDATED_SKULL(Rarity.ROYAL, "Dilapidated Skull", "telos:material/ability/skull/ut-dilapidated", 120),
    GREATWOOD_GREAVES(Rarity.ROYAL, "Greatwood Greaves", "telos:material/armour/heavy/leggings/ut-greatwood", 120),
    BOOK_OF_LIFE(Rarity.BLOODSHOT, "Book of Life", "telos:material/ability/scripture/ut-life", 120),
    ELDER_ENT(Rarity.COMPANION, "Elder Ent", "telos:material/pet/illarius_hideout", 120),
    WOODLAND_MANTIBANE(Rarity.COMPANION, "Woodland Mantibane", "telos:material/mount/illarius_hideout", 120),
    NATURE_RUNE(Rarity.RUNE, "Rune (Nature)", "telos:material/rune/illarius_hideout", 120),

    // The Aviary (Shadowflare)
    AERIAL_STAFF(Rarity.ROYAL, "Aerial Staff", "telos:material/weapon/staff/ut-aerial", 120),
    ORB_OF_PURITY(Rarity.ROYAL, "Orb of Purity", "telos:material/ability/orb/ut-purity", 120),
    PHOENIX_PLATE(Rarity.ROYAL, "Phoenix Plate", "telos:material/armour/heavy/chestplate/ut-phoenix", 120),
    RESURGENCE(Rarity.BLOODSHOT, "Resurgence", "telos:material/ability/star/ut-resurgence", 120),
    DRAGON_WARRIOR(Rarity.COMPANION, "Dragon Warrior", "telos:material/pet/aviary", 120),
    SUNFLARE(Rarity.COMPANION, "Sunflare", "telos:material/mount/aviary", 120),
    SOL_RUNE(Rarity.RUNE, "Rune (Sol)", "telos:material/rune/aviary", 120),

    // Aurora Sanctum (Aetheris)
    LIGHTS_PULL(Rarity.ROYAL, "Light's Pull", "telos:material/ability/bomb/ut-light", 120),
    DAGGER_OF_DISSONANCE(Rarity.ROYAL, "Dagger of Dissonance", "telos:material/weapon/dagger/ut-dissonance", 120),
    HEAVENS_AUGMENTATION(Rarity.ROYAL, "Heaven's Augmentation", "telos:material/ability/emblem/ut-heaven", 120),
    SUNS_BLESSING(Rarity.BLOODSHOT, "Sun's Blessing", "telos:material/ability/skull/ut-blessing", 120),
    RADLE(Rarity.COMPANION, "Radle", "telos:material/pet/aurora_sanctum", 120),
    GLOWSCALE(Rarity.COMPANION, "Glowscale", "telos:material/mount/aurora_sanctum", 120),
    GLASS_SKY_RUNE(Rarity.RUNE, "Rune (Glass Sky)", "telos:material/rune/aurora_sanctum", 120),

    // Tartarus (Malthar)
    CINDER_REACH(Rarity.ROYAL, "Cinder Reach", "telos:material/weapon/bow/ut-cinder", 120),
    CINDER_SKULL(Rarity.ROYAL, "Cinder Skull", "telos:material/ability/skull/ut-cinder", 120),
    SCORCHSCALE_LEGGINGS(Rarity.ROYAL, "Scorchscale Leggings", "telos:material/armour/light/leggings/ut-scorchscale", 120),
    TARTALINK(Rarity.COMPANION, "Tartalink", "telos:material/pet/tartarus", 120),
    EMBERAPTOR(Rarity.COMPANION, "Emberaptor", "telos:material/mount/tartarus", 120),
    PENTACURSE_RUNE(Rarity.RUNE, "Rune (Pentacurse)", "telos:material/rune/tartarus", 120),

    // Raphael's Chamber (Raphael)
    CALAMITY(Rarity.BLOODSHOT, "Calamity", "telos:material/weapon/sceptre/ut-onyx", 75),
    RETRIBUTION(Rarity.BLOODSHOT, "Retribution", "telos:material/weapon/sword/ut-onyx", 75),
    RAPTURE(Rarity.BLOODSHOT, "Rapture", "telos:material/weapon/bow/ut-onyx", 75),
    MARTYR(Rarity.BLOODSHOT, "Martyr", "telos:material/weapon/staff/ut-onyx", 75),
    REIKON(Rarity.BLOODSHOT, "Reikon", "telos:material/weapon/katana/ut-onyx", 75),
    MALICE(Rarity.BLOODSHOT, "Malice", "telos:material/weapon/dagger/ut-onyx", 75),
    WRATHGUARD(Rarity.BLOODSHOT, "Wrathguard", "telos:material/armour/heavy/chestplate/ut-onyx", 75),
    BLOODSTRIDERS(Rarity.BLOODSHOT, "Bloodstriders", "telos:material/armour/light/leggings/ut-onyx", 75),
    RUEFORGE(Rarity.BLOODSHOT, "Rueforge", "telos:material/armour/magical/boots/ut-onyx", 75),
    SOURCESTONE(Rarity.BLOODSHOT, "Crimson Sourcestone", "telos:material/fragment/onyx", 66),
    EXALTLING(Rarity.COMPANION, "Exaltling", "telos:material/pet/onyx", 120),
    CELESTIAL_SERPENT(Rarity.COMPANION, "Celestial Serpent", "telos:material/mount/onyx", 120),
    FALLEN_RUNE(Rarity.RUNE, "Rune (Fallen)", "telos:material/rune/onyx", 120),

    // Tenebris (Voided Omnipotent)
    EVENT_HORIZON(Rarity.BLOODSHOT, "Event Horizon", "telos:material/ability/orb/ut-horizon", 120),
    OBLIVIONS_REACH(Rarity.BLOODSHOT, "Oblivion's Reach", "telos:material/ability/emblem/ut-oblivion", 120),
    SHADOW_SILK(Rarity.BLOODSHOT, "Shadow Silk", "telos:material/armour/magical/leggings/ut-shadow", 120),
    FRACTURED_NIHILITY(Rarity.VOIDBOUND, "Fractured Nihility", "telos:material/fragment/nihility", 120),
    SHADOW_CHIRP(Rarity.COMPANION, "Shadow Chirp", "telos:material/pet/tenebris", 120),
    DREADSPIRE_DRAKE(Rarity.COMPANION, "Dreadspire Drake", "telos:material/mount/tenebris", 120),
    ETERNAL_MAW_RUNE(Rarity.RUNE, "Rune (Eternal Maw)", "telos:material/rune/tenebris", 120),

    // Rustborn Kingdom (Valerion, Nebula, Ophanim)
    VALERIONS_PONIARD(Rarity.BLOODSHOT, "Valerion's Poniard", "telos:material/weapon/dagger/ut-poniard", 120),
    VALERIONS_BANE(Rarity.BLOODSHOT, "Valerion's Bane", "telos:material/weapon/katana/ut-bane", 120),
    CUSTODIANS_VISOR(Rarity.BLOODSHOT, "Custodian's Visor", "telos:material/armour/light/helmet/ut-sentinel", 120),
    NEBULAS_BATTLEAXE(Rarity.BLOODSHOT, "Nebula's Battleaxe", "telos:material/weapon/sword/ut-battleaxe", 120),
    DUSK_WEAVER(Rarity.BLOODSHOT, "Dusk Weaver", "telos:material/weapon/sceptre/ut-dusk", 120),
    ARCHONS_GLARE(Rarity.BLOODSHOT, "Archon's Glare", "telos:material/armour/magical/helmet/ut-mage", 120),
    FINAL_DESTINATION(Rarity.BLOODSHOT, "Final Destination", "telos:material/weapon/staff/ut-destination", 120),
    ORACLES_END(Rarity.BLOODSHOT, "Oracle's End", "telos:material/weapon/bow/ut-oracle", 120),
    CROWN_OF_ETHEREAL_RADIANCE(Rarity.BLOODSHOT, "Crown of Ethereal Radiance", "telos:material/armour/heavy/helmet/ut-crown", 120),
    LUMINE(Rarity.COMPANION, "Lumine", "telos:material/pet/shatters", 120),
    ASTRALFLARE(Rarity.COMPANION, "Astralflare", "telos:material/mount/shatters", 120),
    OBSERVER_RUNE(Rarity.RUNE, "Rune (Observer)", "telos:material/rune/shatters", 120),

    // Dawn of Creation (True Ophan) - Hardmode versions
    PENDANT_OF_SIN(Rarity.UNHOLY, "The Pendant of Sin", "telos:material/fragment/cross2", 120),

    // Shadowlands World Bosses
    WARDENS_FACEGUARD(Rarity.ROYAL, "Warden's Faceguard", "telos:material/armour/heavy/helmet/ut-faceguard", 120),
    WARDENS_GARMENT(Rarity.ROYAL, "Warden's Garment", "telos:material/armour/magical/chestplate/ut-garment", 120),
    DAWNBRINGER(Rarity.BLOODSHOT, "Dawnbringer", "telos:material/weapon/katana/ut-dawnbringer", 120),
    HERALDIC_HEELGUARDS(Rarity.ROYAL, "Heraldic Heelguards", "telos:material/armour/heavy/boots/ut-heelguards", 120),
    LUMINOUS_FLAME(Rarity.ROYAL, "Luminous Flame", "telos:material/weapon/sceptre/ut-luminous", 120),
    HERALDS_ESSENCE(Rarity.BLOODSHOT, "Herald's Essence", "telos:material/ability/orb/ut-essence", 120),
    SOULLESS_SHOES(Rarity.ROYAL, "Soulless Shoes", "telos:material/armour/magical/boots/ut-soulless", 120),
    CHALICE_OF_ROT(Rarity.ROYAL, "Chalice of Rot", "telos:material/ability/poison/ut-rot", 120),
    REAPERS_VEST(Rarity.BLOODSHOT, "Reaper's Vest", "telos:material/armour/light/chestplate/ut-reaper", 120),
    SHROUDED_SHIELD(Rarity.BLOODSHOT, "Shrouded Shield", "telos:material/ability/shield/ut-shrouded", 120),
    PHANTASMIC_GREAVES(Rarity.BLOODSHOT, "Phantasmic Greaves", "telos:material/armour/heavy/leggings/ut-phantasmic", 120),
    AFTERBURNER(Rarity.BLOODSHOT, "Afterburner", "telos:material/ability/cloak/ut-fire", 120),

    // Dreadwood Thicket (Sylvaris)
    SCRIPTURE_OF_THE_TOTEM_MASTER(Rarity.BLOODSHOT, "Scripture of the Totem Master", "telos:material/ability/scripture/ut-totem", 120),
    NATURES_GIFT(Rarity.BLOODSHOT, "Nature's Gift", "telos:material/armour/light/boots/ut-nature", 120),
    SPIRITBLOOM_GOWN(Rarity.BLOODSHOT, "Spiritbloom Gown", "telos:material/armour/magical/chestplate/ut-spiritbloom", 120),
    MOSSLIGHT(Rarity.COMPANION, "Mosslight", "telos:material/pet/dreadwood_thicket", 120),
    VINEGLEAM(Rarity.COMPANION, "Vinegleam", "telos:material/mount/dreadwood_thicket", 120),
    DREAD_RUNE(Rarity.RUNE, "Rune (Dread)", "telos:material/rune/dreadwood_thicket", 120),

    // Resounding Ruins (Unrest)
    SONIC_DOOM(Rarity.BLOODSHOT, "Sonic Doom", "telos:material/weapon/sword/ut-doom", 120),
    ECHOLURKATOR(Rarity.BLOODSHOT, "Echolurkator", "telos:material/weapon/staff/ut-echo", 120),
    SPINESTONE(Rarity.BLOODSHOT, "Spinestone", "telos:material/ability/jewel/ut-spinestone", 120),
    LEGION(Rarity.COMPANION, "Legion", "telos:material/pet/resounding_ruins", 120),
    ECHORAPTOR(Rarity.COMPANION, "Echoraptor", "telos:material/mount/resounding_ruins", 120),
    ECHOES_RUNE(Rarity.RUNE, "Rune (Echoes)", "telos:material/rune/resounding_ruins", 120),

    // Celestial's Province (Asmodeus, Seraphim)
    VISAGE_OF_THE_NIGHT(Rarity.BLOODSHOT, "Visage of the Night", "telos:material/ability/emblem/ut-visage", 120),
    BAPHOMETS_BOMB(Rarity.BLOODSHOT, "Baphomet's Bomb", "telos:material/ability/bomb/ut-baphomet", 120),
    BLOOD_OF_THE_HERETICS(Rarity.BLOODSHOT, "Blood of the Heretics", "telos:material/ability/poison/ut-heretics", 120),
    FEATHERS_OF_THE_SERAPH(Rarity.BLOODSHOT, "Feathers of the Seraph", "telos:material/ability/kunai/ut-feathers", 120),
    SERAPHIC_SHIV(Rarity.BLOODSHOT, "Seraphic Shiv", "telos:material/weapon/dagger/ut-seraphic", 120),
    EMPYREAN_EPITOME(Rarity.BLOODSHOT, "Empyrean Epitome", "telos:material/ability/skull/ut-epitome", 120),
    THANATOS(Rarity.COMPANION, "Thanatos", "telos:material/pet/celestials_province", 120),
    SERAPHIX(Rarity.COMPANION, "Seraphix", "telos:material/mount/celestials_province", 120),
    SERAPH_RUNE(Rarity.RUNE, "Rune (Seraph)", "telos:material/rune/celestials_province", 120),

    // Seraph's Domain (True Seraph) - Hardmode versions
    HOLY_CROSS(Rarity.UNHOLY, "The Holy Cross", "telos:material/fragment/cross", 120);

    /**
     * Item rarity enum.
     */
    enum class Rarity {
        IRRADIATED,
        GILDED,
        ROYAL,
        BLOODSHOT,
        VOIDBOUND,
        UNHOLY,
        COMPANION,
        RUNE
    }

    companion object {
        private val texturePathMap: Map<String, Item> = values().associateBy { it.texturePath }

        /**
         * Find an item by its texture path.
         */
        fun getByTexturePath(texturePath: String): Item? = texturePathMap[texturePath]
    }
}
