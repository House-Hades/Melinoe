package me.melinoe.utils.data

import com.google.gson.Gson
import me.melinoe.Melinoe

/**
 * Trait descriptions from the Telos data set (`traits.json`), keyed by trait name
 *
 * Each description contains a %s per stat; the levels array holds five values per placeholder
 * (one for each trait level)
 */
object TraitDetailsData {

    data class Trait(val name: String, val description: String, val levels: List<Double>)

    @Volatile
    private var byName: Map<String, Trait> = emptyMap()

    private val gson = Gson()

    /** Looks up a trait by its display name, case-insensitively */
    fun get(name: String): Trait? = byName[name.lowercase()]

    /** Parses the traits data set and swaps it in. Returns true if any entry was valid */
    fun load(json: String): Boolean = try {
        val raw = gson.fromJson(json, RawTraits::class.java)
        val parsed = raw?.traits.orEmpty().mapNotNull { entry ->
            if (entry.name == null || entry.description == null) {
                Melinoe.logger.warn("[TraitDetailsData] Skipping malformed trait: ${entry.name}")
                null
            } else {
                entry.name.lowercase() to Trait(entry.name, entry.description, entry.levels.orEmpty())
            }
        }.toMap()
        if (parsed.isNotEmpty()) byName = parsed
        parsed.isNotEmpty()
    } catch (e: Exception) {
        Melinoe.logger.warn("[TraitDetailsData] Failed to parse traits: ${e.message}")
        false
    }

    // === RAW JSON MODELS ===
    private data class RawTraits(val traits: List<RawTrait>?)
    private data class RawTrait(val name: String?, val description: String?, val levels: List<Double>?)
}
