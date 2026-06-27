package me.melinoe.utils.data

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

    // Resolved icons are cached here
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Resolved>()

    /** Clears the resolve cache; called when [ItemDefinitions] loads so names refresh. */
    fun clearCache() = cache.clear()

    /**
     * Resolves a raw key to display + icon metadata. Display name and rarity come from the remote
     * [ItemDefinitions] when available, then the bundled [Item] registry, then a prettified key.
     */
    fun resolve(key: String): Resolved = cache.getOrPut(key) {
        val model = modelPath(key)
        val def = ItemDefinitions.get(key)
        val item = lookupItem(model)
        val rarity = def?.rarity ?: item?.rarity
        Resolved(
            key = key,
            modelPath = model,
            textureResource = "$model.png",
            displayName = def?.name ?: item?.displayName ?: prettify(key),
            rarityColor = rarityColor(rarity),
            rarity = rarity
        )
    }

    /** Maps an item key to its `telos:material/...` model identifier. */
    fun modelPath(key: String): String = "telos:material/$key"
    
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
        Item.Rarity.SHINY -> 0xFF00FFFF.toInt()
        null -> 0xFF5A5A62.toInt()
    }
    
    /** Readable fallback for keys not present in the [Item] registry (uses the last path segment). */
    private fun prettify(key: String): String =
        key.substringAfterLast('/')
            .split('-', '_')
            .filter { it.isNotEmpty() && it != "ut" && it != "ex" }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            .ifEmpty { key }
}