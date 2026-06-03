package me.melinoe.utils.data

/**
 * Dungeon Data. Loaded at runtime by TelosData.
 */
data class DungeonData(
    val name: String,
    val areaName: String,
    val dungeonType: DungeonDifficulty,
    val finalBossKey: String
) {
    /** The dungeon's final boss. */
    val finalBoss: BossData? get() = BossData.byKey(finalBossKey)

    companion object {
        @Volatile
        private var nameMap: Map<String, DungeonData> = emptyMap()

        @Volatile
        private var areaNameMap: Map<String, DungeonData> = emptyMap()

        /** All dungeons, in file order. */
        val all: List<DungeonData> get() = nameMap.values.toList()

        /** Dungeon by its stable key. */
        fun byKey(name: String): DungeonData? = nameMap[name]

        /** Find a dungeon by its area name. */
        fun findByKey(areaName: String?): DungeonData? = if (areaName != null) areaNameMap[areaName] else null

        /** Replaces the registry. Called by TelosData on load. */
        internal fun setAll(dungeons: List<DungeonData>) {
            nameMap = dungeons.associateBy { it.name }
            areaNameMap = dungeons.associateBy { it.areaName }
        }
    }
}
