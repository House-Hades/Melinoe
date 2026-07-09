package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe
import net.minecraft.core.BlockPos

/**
 * Miniboss Data. Loaded at runtime by TelosData. Unlike bosses, a miniboss can spawn at multiple
 * locations, so it carries a list of spawn positions
 */
data class MinibossData(
    val name: String,
    val label: String,
    val region: String,
    val positions: List<BlockPos>,
    val texture: String?,
    val note: String?
) {
    companion object {
        @Volatile
        var all: List<MinibossData> = emptyList()
            private set

        private val gson = Gson()

        /** Parses the payload and swaps the registry. Returns false when no entry is valid. */
        fun load(json: String): Boolean {
            val parsed = gson.fromJson(json, RawMinibosses::class.java)?.minibosses.orEmpty().mapNotNull { raw ->
                val positions = raw.positions.orEmpty().map { BlockPos(it.x, it.y, it.z) }
                if (raw.name == null || raw.label == null || positions.isEmpty()) {
                    Melinoe.logger.warn("[TelosData] Skipping malformed miniboss: ${raw.name}")
                    null
                } else {
                    MinibossData(raw.name, raw.label, raw.region ?: "main", positions, raw.texture, raw.note)
                }
            }
            if (parsed.isNotEmpty()) all = parsed
            return parsed.isNotEmpty()
        }

        private data class RawMinibosses(val minibosses: List<RawMiniboss>?)
        private data class RawMiniboss(
            val name: String?,
            val label: String?,
            val region: String?,
            val positions: List<RawPos>?,
            val texture: String?,
            val note: String?
        )

        private data class RawPos(val x: Int = 0, val y: Int = 0, val z: Int = 0)
    }
}
