package me.melinoe.utils.data

import net.minecraft.core.BlockPos

/**
 * Boss Data. Loaded at runtime by TelosData.
 */
data class BossData(
    val name: String,
    val label: String,
    val spawnPosition: BlockPos?,
    val bossType: BossType,
    val bossBarHash: Int? = null,
    val itemKeys: List<String> = emptyList()
) {
    /** The items this boss can drop. */
    val items: List<Item> get() = itemKeys.mapNotNull { Item.byKey(it) }

    override fun toString(): String = label

    companion object {
        @Volatile
        private var nameMap: Map<String, BossData> = emptyMap()

        @Volatile
        private var labelMap: Map<String, BossData> = emptyMap()

        // item key -> boss
        @Volatile
        private var bossItemMap: Map<String, BossData> = emptyMap()

        // boss-bar name hash -> boss
        @Volatile
        private var hashMap: Map<Int, BossData> = emptyMap()

        /** All bosses, in file order. */
        val all: List<BossData> get() = nameMap.values.toList()

        /** Boss by its stable key. */
        fun byKey(name: String): BossData? = nameMap[name]

        /** Find a boss by its label/name. */
        fun findByKey(name: String): BossData? = labelMap[name]

        /** Find a boss by one of its items. */
        fun findByItem(item: Item): BossData? = bossItemMap[item.name]

        /** Find a boss by the hash of its boss-bar name. */
        fun findByHash(hash: Int): BossData? = hashMap[hash]

        /** Find a boss by its key, ignoring case. */
        fun fromString(name: String): BossData? = nameMap[name.uppercase()]

        /** Combined drops of several bosses, used for split dungeons. */
        fun itemsOf(vararg names: String): List<Item> = names.flatMap { byKey(it)?.items.orEmpty() }

        /** Replaces the registry. Called by TelosData on load. */
        internal fun setAll(bosses: List<BossData>) {
            nameMap = bosses.associateBy { it.name }
            labelMap = bosses.associateBy { it.label }
            bossItemMap = bosses.flatMap { boss -> boss.itemKeys.map { it to boss } }.toMap()
            hashMap = bosses.mapNotNull { boss -> boss.bossBarHash?.let { it to boss } }.toMap()
        }
    }
}
