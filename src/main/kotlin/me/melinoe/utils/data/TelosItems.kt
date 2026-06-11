package me.melinoe.utils.data

/**
 * Turns a Telos item key from the player API (e.g. "heavy-chestplate-ut-onyx", "staff-ut-aerial")
 * into a model path, texture path, display name and rarity colour for rendering.
 */
object TelosItems {
    
    data class Resolved(
        val key: String,
        val modelPath: String,
        /** Texture identifier including extension, e.g. "telos:material/.../ut-onyx.png". */
        val textureResource: String,
        val displayName: String,
        val rarityColor: Int,
        val rarity: Item.Rarity?
    )
    
    private val ARMOUR_WEIGHTS = setOf("heavy", "light", "magical")
    private val ARMOUR_SLOTS = setOf("helmet", "chestplate", "leggings", "boots")
    private val WEAPON_TYPES = setOf("sword", "staff", "bow", "katana", "dagger", "sceptre")
    private val ABILITY_TYPES = setOf(
        "scripture", "poison", "trap", "emblem", "skull", "cloak",
        "orb", "star", "jewel", "kunai", "bomb", "shield"
    )
    
    // Resolved icons are cached here
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Resolved>()
    
    /** Resolves a raw key (companion suffix like "/6" is stripped) to display + icon metadata. */
    fun resolve(rawKey: String): Resolved = cache.getOrPut(rawKey) {
        val key = rawKey.substringBefore('/')
        val model = modelPath(key)
        val item = lookupItem(model)
        Resolved(
            key = key,
            modelPath = model,
            textureResource = "$model.png",
            displayName = item?.displayName ?: prettify(key),
            rarityColor = rarityColor(item?.rarity),
            rarity = item?.rarity
        )
    }
    
    /** Maps an item key to its `telos:material/...` model identifier. */
    fun modelPath(key: String): String {
        val t = key.split("-")
        return when {
            t.size >= 3 && t[0] in ARMOUR_WEIGHTS && t[1] in ARMOUR_SLOTS ->
                "telos:material/armour/${t[0]}/${t[1]}/${t.drop(2).joinToString("-")}"
            
            t.size >= 2 && t[0] in WEAPON_TYPES ->
                "telos:material/weapon/${t[0]}/${t.drop(1).joinToString("-")}"
            
            t.size >= 2 && t[0] in ABILITY_TYPES ->
                "telos:material/ability/${t[0]}/${t.drop(1).joinToString("-")}"
            
            else -> "telos:material/${key.replace("-", "/")}"
        }
    }
    
    private fun lookupItem(model: String): Item? {
        Item.getByTexturePath(model)?.let { return it }
        // Tradeable ("ex-") variants share names with their untradeable ("ut-") counterparts.
        if (model.contains("/ex-")) return Item.getByTexturePath(model.replace("/ex-", "/ut-"))
        return null
    }
    
    /** Rarity colours, matching the chat pity checker palette. */
    fun rarityColor(r: Item.Rarity?): Int = when (r) {
        Item.Rarity.IRRADIATED -> 0xFF15CD15.toInt()
        Item.Rarity.GILDED -> 0xFFDF5320.toInt()
        Item.Rarity.ROYAL -> 0xFFAA00AA.toInt()
        Item.Rarity.BLOODSHOT -> 0xFFAA0000.toInt()
        Item.Rarity.VOIDBOUND -> 0xFF8D15F0.toInt()
        Item.Rarity.UNHOLY -> 0xFFBFBFBF.toInt()
        Item.Rarity.COMPANION -> 0xFFFFAA00.toInt()
        Item.Rarity.RUNE -> 0xFF8A7B5A.toInt()
        null -> 0xFF5A5A62.toInt()
    }
    
    /** Readable fallback for keys not present in the [Item] registry. */
    private fun prettify(key: String): String =
        key.split('-', '_')
            .filter { it.isNotEmpty() && it != "ut" && it != "ex" }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            .ifEmpty { key }
}