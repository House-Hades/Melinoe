package me.melinoe.utils

import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Utility for creating Telos custom items (bosses, pouches, totems)
 * Kotlin version matching the new Minecraft API
 */
object TelosItemUtils {
    
    // ==================== BOSS RESOURCE LOCATIONS ====================
    
    val BOSS_ANUBIS = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/anubis")
    val BOSS_ASTAROTH = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/astaroth")
    val BOSS_CHUNGUS = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/chungus")
    val BOSS_FREDDY = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/freddy")
    val BOSS_GLUMI = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/glumi")
    val BOSS_ILLARIUS = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/illarius")
    val BOSS_LOTIL = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/lotil")
    val BOSS_OOZUL = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/oozul")
    val BOSS_TIDOL = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/tidol")
    val BOSS_VALUS = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/valus")
    val BOSS_HOLLOWBANE = ResourceLocation.fromNamespaceAndPath("telos", "material/boss/hollowbane")
    val BOSS_RAPHAEL = ResourceLocation.fromNamespaceAndPath("telos", "material/pet/onyx")
    
    // ==================== POUCH/TOTEM RESOURCE LOCATIONS ====================
    
    val POUCH_ROYAL = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/royal")
    val POUCH_BLOODSHOT = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/bloodshot")
    val POUCH_COMPANION = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/companion")
    val POUCH_UNHOLY = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/unholy")
    val POUCH_VOIDBOUND = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/voidbound")
    val POUCH_HALLOWEEN = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/halloween")
    val POUCH_VALENTINE = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/valentine")
    val POUCH_CHRISTMAS = ResourceLocation.fromNamespaceAndPath("telos", "material/pouch/christmas")
    
    // ==================== STRING KEY MAPPINGS ====================
    
    private val keyToResourceLocation = mapOf(
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
        "raphael" to BOSS_RAPHAEL,
        
        // Pouches/Totems
        "royal" to POUCH_ROYAL,
        "bloodshot" to POUCH_BLOODSHOT,
        "companion" to POUCH_COMPANION,
        "unholy" to POUCH_UNHOLY,
        "voidbound" to POUCH_VOIDBOUND,
        "halloween" to POUCH_HALLOWEEN,
        "valentine" to POUCH_VALENTINE,
        "christmas" to POUCH_CHRISTMAS
    )
    
    // ==================== LOOKUP METHODS ====================
    
    /**
     * Get a ResourceLocation from a string key (case-insensitive)
     */
    fun getResourceLocation(key: String): ResourceLocation? {
        return keyToResourceLocation[key.lowercase()]
    }
    
    // ==================== ITEMSTACK CREATION ====================
    
    /**
     * Create an ItemStack with a custom model identifier.
     * Uses CARROT_ON_A_STICK as the base item (standard for Telos custom models).
     */
    fun createItemStack(resourceLocation: ResourceLocation): ItemStack {
        val item = ItemStack(Items.CARROT_ON_A_STICK)
        item.set(DataComponents.ITEM_MODEL, resourceLocation)
        return item
    }
    
    /**
     * Create an ItemStack from a string key
     */
    fun createItemStack(key: String): ItemStack? {
        return getResourceLocation(key)?.let { createItemStack(it) }
    }
    
    /**
     * Check if a string key is registered
     */
    fun isRegistered(key: String): Boolean {
        return keyToResourceLocation.containsKey(key.lowercase())
    }
}
