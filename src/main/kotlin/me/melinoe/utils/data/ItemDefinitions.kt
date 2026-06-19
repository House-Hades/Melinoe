package me.melinoe.utils.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.melinoe.Melinoe

/**
 * Fetched and cached by [me.melinoe.network.ProfileFetcher]; lookups return null for unknown keys
 * and callers fall back to the local registry / a prettified key.
 */
object ItemDefinitions {

    data class Def(val name: String?, val rarity: Item.Rarity?)

    @Volatile private var defs: Map<String, Def> = emptyMap()

    /** Whether the remote definitions have been loaded this session. */
    @Volatile var loaded = false
        private set

    private val gson = Gson()

    fun get(key: String): Def? = defs[key]

    /** Parses the raw definitions map and swaps it in. Returns true if any entry was valid. */
    fun load(json: String): Boolean = try {
        val type = object : TypeToken<Map<String, RawItem>>() {}.type
        val raw: Map<String, RawItem> = gson.fromJson(json, type) ?: emptyMap()
        val parsed = raw.mapNotNull { (k, v) ->
            val name = v.name ?: return@mapNotNull null
            val kind = v.components?.tier?.tierType?.kind
            val rarity = kind?.let { runCatching { Item.Rarity.valueOf(it) }.getOrNull() }
            k to Def(name, rarity)
        }.toMap()
        if (parsed.isNotEmpty()) {
            defs = parsed
            loaded = true
            TelosItems.clearCache()
        }
        parsed.isNotEmpty()
    } catch (e: Exception) {
        Melinoe.logger.warn("[ItemDefinitions] Failed to parse item definitions: ${e.message}")
        false
    }

    // === RAW JSON MODELS === (only the fields we read; everything else is ignored)
    private data class RawItem(val name: String? = null, val components: RawComponents? = null)
    private data class RawComponents(val tier: RawTier? = null)
    private data class RawTier(val tierType: RawTierType? = null)
    private data class RawTierType(val kind: String? = null)
}
