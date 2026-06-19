package me.melinoe.utils.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.melinoe.Melinoe

/**
 * Item-trait definitions from the Telos API.
 *
 * Definitions are fetched once per session; lookups fall back to deriving the tier and a readable
 * name from the key alone so traits still show if the fetch fails.
 */
object TraitData {

    /** A run of text in a single colour, from a parsed MiniMessage description. */
    data class Run(val text: String, val color: Int)

    data class Trait(val key: String, val tier: Int, val slot: Int, val name: String, val description: String? = null) {
        /** The description parsed into coloured runs, falling back to the plain name. */
        fun runs(): List<Run> =
            description?.let { parseRuns(it) }?.takeIf { it.isNotEmpty() }
                ?: listOf(Run(name, 0xFFFFFFFF.toInt()))
    }

    /** Tier letters by level, lowest to highest. */
    private val TIERS = arrayOf("D", "C", "B", "A", "S")

    /** Tier colours (0xAARRGGBB), matching the server's trait description palette. */
    private val TIER_COLORS = intArrayOf(
        0xFFB4ADB5.toInt(), // D
        0xFFB9E353.toInt(), // C
        0xFF5EBCE5.toInt(), // B
        0xFFFC0505.toInt(), // A
        0xFFBCECEA.toInt()  // S
    )

    /** Minecraft named text colours used by the trait descriptions. */
    private val NAMED: Map<String, Int> = mapOf(
        "black" to 0x000000, "dark_blue" to 0x0000AA, "dark_green" to 0x00AA00,
        "dark_aqua" to 0x00AAAA, "dark_red" to 0xAA0000, "dark_purple" to 0xAA00AA,
        "gold" to 0xFFAA00, "gray" to 0xAAAAAA, "grey" to 0xAAAAAA,
        "dark_gray" to 0x555555, "dark_grey" to 0x555555, "blue" to 0x5555FF,
        "green" to 0x55FF55, "aqua" to 0x55FFFF, "red" to 0xFF5555,
        "light_purple" to 0xFF55FF, "yellow" to 0xFFFF55, "white" to 0xFFFFFF
    )

    @Volatile private var defs: Map<String, Trait> = emptyMap()

    /** Whether the remote definitions have been loaded this session. */
    @Volatile var loaded = false
        private set

    private val gson = Gson()

    fun tierLetter(level: Int): String = TIERS.getOrElse(level) { "?" }
    fun tierColor(level: Int): Int = TIER_COLORS.getOrElse(level) { 0xFF5A5A62.toInt() }

    /** Looks up a trait by key, deriving it from the key when no definition is loaded. */
    fun get(key: String): Trait? = defs[key] ?: derive(key)

    /** Parses the raw definitions map and swaps it in. Returns true if any entry was valid. */
    fun load(json: String): Boolean = try {
        val type = object : TypeToken<Map<String, RawTrait>>() {}.type
        val raw: Map<String, RawTrait> = gson.fromJson(json, type) ?: emptyMap()
        val parsed = raw.mapNotNull { (k, v) ->
            val base = derive(k) ?: return@mapNotNull null
            k to base.copy(tier = v.level.coerceIn(0, 4), slot = v.slot, description = v.description)
        }.toMap()
        if (parsed.isNotEmpty()) {
            defs = parsed
            loaded = true
        }
        parsed.isNotEmpty()
    } catch (e: Exception) {
        Melinoe.logger.warn("[TraitData] Failed to parse trait definitions: ${e.message}")
        false
    }

    /**
     * Derives a trait from its key alone: "weapon/slot1/cooldown_damage2" -> slot 1, tier C (the
     * trailing 1..5 maps to level 0..4), name "Cooldown Damage".
     */
    private fun derive(key: String): Trait? {
        val parts = key.split('/')
        if (parts.size < 3) return null
        val slot = parts[1].removePrefix("slot").toIntOrNull()?.minus(1) ?: 0
        val last = parts.last()
        val rankDigit = last.takeLastWhile { it.isDigit() }.toIntOrNull()
        val tier = (rankDigit ?: 1).coerceIn(1, 5) - 1
        val name = last.dropLastWhile { it.isDigit() }
            .split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        return Trait(key, tier, slot, name.ifEmpty { last })
    }

    /**
     * Parses a MiniMessage description into coloured [Run]s. Colour tags (`<#RRGGBB>` and named
     * colours like `<yellow>`) are tracked on a stack; styling tags (`<!italic>`, etc.) are
     * ignored. Anything outside a colour tag renders white.
     */
    private fun parseRuns(desc: String): List<Run> {
        val runs = ArrayList<Run>()
        val stack = ArrayList<Int>()
        val sb = StringBuilder()
        fun current(): Int = (stack.lastOrNull() ?: 0xFFFFFF) or 0xFF000000.toInt()
        fun flush() { if (sb.isNotEmpty()) { runs.add(Run(sb.toString(), current())); sb.setLength(0) } }
        var i = 0
        while (i < desc.length) {
            val ch = desc[i]
            if (ch == '<') {
                val end = desc.indexOf('>', i)
                if (end < 0) { sb.append(ch); i++; continue }
                flush()
                val tag = desc.substring(i + 1, end)
                if (tag.startsWith("/")) {
                    if (colorOf(tag.substring(1)) != null && stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                } else {
                    colorOf(tag)?.let { stack.add(it) }
                }
                i = end + 1
            } else {
                sb.append(ch)
                i++
            }
        }
        flush()
        return runs
    }

    /** The RGB for a colour tag (hex `#RRGGBB` or a Minecraft colour name), or null for non-colours. */
    private fun colorOf(tag: String): Int? {
        val t = tag.lowercase()
        if (t.startsWith("#") && t.length == 7) return t.substring(1).toIntOrNull(16)
        return NAMED[t]
    }

    private data class RawTrait(val level: Int = 0, val slot: Int = 0, val description: String? = null)
}