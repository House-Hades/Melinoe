package me.melinoe.utils

import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Utility for creating Telos custom items (bosses, pouches, totems)
 * Kotlin version matching the new Minecraft API
 */
object TelosItemUtils {
    
    // ==================== BOSS RESOURCE LOCATIONS ====================
    
    val BOSS_ANUBIS = Identifier.fromNamespaceAndPath("telos", "material/boss/anubis")
    val BOSS_ASTAROTH = Identifier.fromNamespaceAndPath("telos", "material/boss/astaroth")
    val BOSS_CHUNGUS = Identifier.fromNamespaceAndPath("telos", "material/boss/chungus")
    val BOSS_FREDDY = Identifier.fromNamespaceAndPath("telos", "material/boss/freddy")
    val BOSS_GLUMI = Identifier.fromNamespaceAndPath("telos", "material/boss/glumi")
    val BOSS_ILLARIUS = Identifier.fromNamespaceAndPath("telos", "material/boss/illarius")
    val BOSS_LOTIL = Identifier.fromNamespaceAndPath("telos", "material/boss/lotil")
    val BOSS_OOZUL = Identifier.fromNamespaceAndPath("telos", "material/boss/oozul")
    val BOSS_TIDOL = Identifier.fromNamespaceAndPath("telos", "material/boss/tidol")
    val BOSS_VALUS = Identifier.fromNamespaceAndPath("telos", "material/boss/valus")
    val BOSS_HOLLOWBANE = Identifier.fromNamespaceAndPath("telos", "material/boss/hollowbane")
    val BOSS_CLAUS = Identifier.fromNamespaceAndPath("telos", "material/boss/claus")
    val BOSS_RAPHAEL = Identifier.fromNamespaceAndPath("telos", "material/pet/onyx")
    val BOSS_DEFENDER = Identifier.fromNamespaceAndPath("telos", "material/boss/defender")
    val BOSS_COG_SENTINEL = Identifier.fromNamespaceAndPath("telos", "material/boss/cog_sentinel")
    val BOSS_COG_STABILIZER = Identifier.fromNamespaceAndPath("melinoe", "boss/cog_stabilizer")

    val BOSS_REAPER = Identifier.fromNamespaceAndPath("telos", "material/boss/reaper")
    val BOSS_HERALD = Identifier.fromNamespaceAndPath("telos", "material/boss/herald")
    val BOSS_WARDEN = Identifier.fromNamespaceAndPath("telos", "material/boss/warden")
    
    // ==================== POUCH/TOTEM RESOURCE LOCATIONS ====================
    
    val POUCH_ROYAL = Identifier.fromNamespaceAndPath("telos", "mob/pouch/royal_totem")
    val POUCH_BLOODSHOT = Identifier.fromNamespaceAndPath("telos", "mob/pouch/bloodshot_totem")
    val POUCH_COMPANION = Identifier.fromNamespaceAndPath("telos", "mob/pouch/companion_totem")
    val POUCH_UNHOLY = Identifier.fromNamespaceAndPath("telos", "mob/pouch/unholy_totem")
    val POUCH_VOIDBOUND = Identifier.fromNamespaceAndPath("telos", "mob/pouch/voidbound_totem")
    val POUCH_HALLOWEEN = Identifier.fromNamespaceAndPath("telos", "mob/pouch/halloween_totem")
    val POUCH_VALENTINE = Identifier.fromNamespaceAndPath("telos", "mob/pouch/valentine_totem")
    val POUCH_CHRISTMAS = Identifier.fromNamespaceAndPath("telos", "mob/pouch/christmas_totem")
    val POUCH_SHINY = Identifier.fromNamespaceAndPath("telos", "mob/pouch/shiny_totem")
    
    // ==================== STRING KEY MAPPINGS ====================
    
    private val keyToIdentifier = mapOf(
        // Bosses
        "anubis" to BOSS_ANUBIS,
        "astaroth" to BOSS_ASTAROTH,
        "chungus" to BOSS_CHUNGUS,
        "freddy" to BOSS_FREDDY,
        "glumi" to BOSS_GLUMI,
        "illarius" to BOSS_ILLARIUS,
        "lotil" to BOSS_LOTIL,
        "oozul" to BOSS_OOZUL,
        "tidol" to BOSS_TIDOL,
        "valus" to BOSS_VALUS,
        "hollowbane" to BOSS_HOLLOWBANE,
        "claus" to BOSS_CLAUS,
        "raphael" to BOSS_RAPHAEL,
        "defender" to BOSS_DEFENDER,
        "cog_sentinel" to BOSS_COG_SENTINEL,
        "cog_stabilizer" to BOSS_COG_STABILIZER,

        "reaper" to BOSS_REAPER,
        "herald" to BOSS_HERALD,
        "warden" to BOSS_WARDEN,

        // Pouches/Totems
        "royal" to POUCH_ROYAL,
        "bloodshot" to POUCH_BLOODSHOT,
        "companion" to POUCH_COMPANION,
        "unholy" to POUCH_UNHOLY,
        "voidbound" to POUCH_VOIDBOUND,
        "halloween" to POUCH_HALLOWEEN,
        "valentine" to POUCH_VALENTINE,
        "christmas" to POUCH_CHRISTMAS,
        "shiny" to POUCH_SHINY
    )
    
    // ==================== LOOKUP METHODS ====================
    
    /**
     * Get a Identifier from a string key (case-insensitive)
     */
    fun getIdentifier(key: String): Identifier? {
        return keyToIdentifier[key.lowercase()]
    }
    
    // ==================== ITEMSTACK CREATION ====================
    
    /**
     * Create an ItemStack with a custom model identifier.
     * Uses STICK as the base item (standard for Telos custom models).
     */
    fun createItemStack(Identifier: Identifier): ItemStack {
        val item = ItemStack(Items.STICK)
        item.set(DataComponents.ITEM_MODEL, Identifier)
        return item
    }
    
    /**
     * Create an ItemStack from a string key
     */
    fun createItemStack(key: String): ItemStack? {
        return getIdentifier(key)?.let { createItemStack(it) }
    }
    
    /**
     * Check if a string key is registered
     */
    fun isRegistered(key: String): Boolean {
        return keyToIdentifier.containsKey(key.lowercase())
    }
}
