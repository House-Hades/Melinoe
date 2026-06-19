package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe

object ClassData {

    /** Soul-point cap for a single class. */
    @Volatile
    var maxSoulPointsPerClass = 3_000_000L
        private set

    /** Soul-point cap across all classes (used for the total progress bar). */
    @Volatile
    var maxSoulPointsTotal = 18_000_000L
        private set

    /** Fixed class order so each archetype always occupies the same grid slot. */
    @Volatile
    var classOrder: List<String> = listOf("KNIGHT", "ASSASSIN", "NECROMANCER", "SAMURAI", "HUNTRESS", "ARCANIST")
        private set

    private val gson = Gson()

    /** Parses the class data set. Returns true if it had a non-empty class order. */
    fun load(json: String): Boolean = try {
        val raw = gson.fromJson(json, RawClasses::class.java)
        val order = raw?.classOrder?.filter { it.isNotBlank() }.orEmpty()
        if (order.isEmpty()) {
            false
        } else {
            maxSoulPointsPerClass = raw?.maxSoulPointsPerClass ?: 3_000_000L
            maxSoulPointsTotal = raw?.maxSoulPointsTotal ?: 18_000_000L
            classOrder = order.map { it.uppercase() }
            true
        }
    } catch (e: Exception) {
        Melinoe.logger.warn("[ClassData] Failed to parse classes: ${e.message}")
        false
    }

    // === RAW JSON MODELS ===
    private data class RawClasses(
        val maxSoulPointsPerClass: Long?,
        val maxSoulPointsTotal: Long?,
        val classOrder: List<String>?
    )
}
