package me.melinoe.utils.data

/**
 * A droppable Telos item. Loaded at runtime by TelosData.
 */
data class Item(
    val name: String,
    val rarity: Rarity,
    val displayName: String,
    val texturePath: String,
    val maxPity: Int
) {
    /** Item rarity */
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

    /** Returns the constant name. */
    override fun toString(): String = name

    companion object {
        @Volatile
        private var nameMap: Map<String, Item> = emptyMap()

        @Volatile
        private var texturePathMap: Map<String, Item> = emptyMap()

        /** All items, in file order. */
        val all: List<Item> get() = nameMap.values.toList()

        /** Item by its stable key. */
        fun byKey(name: String): Item? = nameMap[name]

        /** Item by texture path. */
        fun getByTexturePath(texturePath: String): Item? = texturePathMap[texturePath]

        /** Replaces the registry. Called by TelosData on load. */
        internal fun setAll(items: List<Item>) {
            nameMap = items.associateBy { it.name }
            texturePathMap = items.associateBy { it.texturePath }
        }
    }
}
