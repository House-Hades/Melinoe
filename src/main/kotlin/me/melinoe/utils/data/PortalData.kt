package me.melinoe.utils.data

/**
 * Dungeon Portal Data. Loaded at runtime by TelosData.
 */
data class PortalData(
    val name: String,
    val label: String
) {
    companion object {
        @Volatile
        private var nameMap: Map<String, PortalData> = emptyMap()

        /** All portals, in file order. */
        val all: List<PortalData> get() = nameMap.values.toList()

        /** Portal by its stable key. */
        fun byKey(name: String): PortalData? = nameMap[name]

        /** Replaces the registry. Called by TelosData on load. */
        internal fun setAll(portals: List<PortalData>) {
            nameMap = portals.associateBy { it.name }
        }
    }
}
