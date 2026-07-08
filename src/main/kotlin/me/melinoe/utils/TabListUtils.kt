package me.melinoe.utils

import me.melinoe.Melinoe
import java.util.*
import java.util.regex.Pattern

/**
 * Tab list parsing utilities for extracting server information.
 * Optimized to parse tab list once and extract multiple values in a single pass.
 */
object TabListUtils {
    private val TPS_PATTERN = Pattern.compile("TPS:\\s*(\\d+)")
    private val FAME_PATTERN = Pattern.compile("Fame:\\s*([\\d,]+)")
    private val LEVEL_PATTERN = Pattern.compile("Class:\\s*(.+)")
    private val SERVER_PATTERN = Pattern.compile("Server:\\s*(.+)")
    private val LOOTBOOST_PATTERN = Pattern.compile("Loot Boost:\\s*\\+(\\d+)")
    private val SPEED_PATTERN = Pattern.compile("Speed:\\s*\\+(\\d+(?:\\.\\d+)?)")
    private val EVASION_PATTERN = Pattern.compile("Evasion:\\s*\\+(\\d+(?:\\.\\d+)?)")

    /**
     * Single source of truth for every stat that can appear in the tablist.
     */
    data class StatDefinition(
        val key: String,
        val label: String,
        val valuePattern: String = "\\d+(?:\\.\\d+)?"
    )

    val STAT_DEFINITIONS: List<StatDefinition> = listOf(
        StatDefinition("attack", "Attack"),
        StatDefinition("defense", "Defense"),
        StatDefinition("vitality", "Vitality"),
        StatDefinition("speed", "Speed"),
        StatDefinition("evasion", "Evasion"),
        StatDefinition("critical_chance", "Critical Chance"),
        StatDefinition("critical_damage", "Critical Damage")
    )

    // Stat patterns for value extraction, derived from STAT_DEFINITIONS.
    // Keyed the same way getStatValues() keys its result map.
    private val STAT_PATTERNS: List<Pair<String, Pattern>> = STAT_DEFINITIONS.map {
        it.key to Pattern.compile("${it.label}:\\s*\\+(${it.valuePattern})")
    }

    /**
     * Data class to hold parsed tab list information.
     */
    data class TabListData(
        val charInfo: String? = null,
        val server: String? = null,
        val tps: String? = null,
        val fame: String? = null,
        val lootboost: String? = null,
        val speed: String? = null,
        val evasion: String? = null
    )

    /**
     * Get the tab list as a list of strings.
     * This is the only method that accesses the network handler.
     * Only works on Telos.
     */
    private fun getTabList(): List<String>? {
        // Only parse tab list on Telos
        if (!ServerUtils.isOnTelos()) return null

        val networkHandler = Melinoe.mc.connection ?: return null
        val playerCollection = networkHandler.onlinePlayers

        if (playerCollection.isEmpty()) return null

        // Wrap in try-catch as defense-in-depth for edge cases on Telos
        return try {
            playerCollection
                .mapNotNull { it.tabListDisplayName?.string }
                .map { stripAllFormatting(it).trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            // Handle any concurrent modification or array bounds issues gracefully
            null
        }
    }
    
    /**
     * Parse all tab list data in a single pass.
     * This is the most efficient way to extract multiple values.
     */
    fun parseTabList(): TabListData {
        val tabList = getTabList() ?: return TabListData()

        var charInfo: String? = null
        var server: String? = null
        var tps: String? = null
        var fame: String? = null
        var lootboost: String? = null
        var speed: String? = null
        var evasion: String? = null

        for (line in tabList) {
            when {
                charInfo == null -> {
                    val matcher = LEVEL_PATTERN.matcher(line)
                    if (matcher.find()) {
                        charInfo = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                server == null -> {
                    val matcher = SERVER_PATTERN.matcher(line)
                    if (matcher.find()) {
                        server = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                tps == null -> {
                    val matcher = TPS_PATTERN.matcher(line)
                    if (matcher.find()) {
                        tps = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                fame == null -> {
                    val matcher = FAME_PATTERN.matcher(line)
                    if (matcher.find()) {
                        fame = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                lootboost == null -> {
                    val matcher = LOOTBOOST_PATTERN.matcher(line)
                    if (matcher.find()) {
                        lootboost = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                speed == null -> {
                    val matcher = SPEED_PATTERN.matcher(line)
                    if (matcher.find()) {
                        speed = matcher.group(1).trim()
                        continue
                    }
                }
            }

            when {
                evasion == null -> {
                    val matcher = EVASION_PATTERN.matcher(line)
                    if (matcher.find()) {
                        evasion = matcher.group(1).trim()
                        continue
                    }
                }
            }

            // Early exit if we found everything
            if (charInfo != null && server != null && tps != null &&
                fame != null && lootboost != null && speed != null && evasion != null
            ) {
                break
            }
        }

        return TabListData(charInfo, server, tps, fame, lootboost, speed, evasion)
    }

    /**
     * Get a line from tab list that matches the given pattern.
     * Use parseTabList() instead for better performance when getting multiple values.
     */
    private fun getLineMatches(pattern: Pattern): String? {
        val tabList = getTabList() ?: return null

        for (line in tabList) {
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                return matcher.group(1).trim()
            }
        }
        return null
    }

    fun getTPS(): String? = getLineMatches(TPS_PATTERN)
    fun getServer(): String? = getLineMatches(SERVER_PATTERN)
    fun getCharInfo(): String? = getLineMatches(LEVEL_PATTERN)
    fun getFame(): String? = getLineMatches(FAME_PATTERN)
    fun getLootboost(): String? = getLineMatches(LOOTBOOST_PATTERN)
    fun getSpeed(): String? = getLineMatches(SPEED_PATTERN)
    fun getEvasion(): String? = getLineMatches(EVASION_PATTERN)

    /**
     * Get loot boost as an integer percentage.
     */
    fun getLootboostPercentage(): Int? {
        val lootboost = getLootboost() ?: return null
        return try {
            lootboost.replace("+", "").trim().toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Strip all formatting codes from a string.
     * Uses efficient noControlCodes extension.
     */
    fun stripAllFormatting(input: String): String {
        return input.noControlCodes
    }

    /**
     * Formula: (0.5 * Attack) / (50 + 0.2 * Attack) * 100%
     */
    fun calculateAttackPercentage(attack: Double): Double {
        return (0.5 * attack) / (50 + 0.2 * attack) * 100.0
    }

    /**
     * Formula: (1 - 100 / (Defense + 100)) * 100%
     */
    fun calculateDefensePercentage(defense: Double): Double {
        return (1.0 - 100.0 / (defense + 100.0)) * 100.0
    }

    /**
     * Formula: Speed * 0.8%
     */
    fun calculateSpeedPercentage(speed: Double): Double {
        return speed * 0.8
    }

    /**
     * Formula: (Evasion / (Evasion + 75)) * 100%
     */
    fun calculateEvasionPercentage(evasion: Double): Double {
        return (evasion / (evasion + 75.0)) * 100.0
    }

    /**
     * Each point of Vitality increases healing by +0.02 HP/s additively.
     */
    fun calculateVitalityHPPerSecond(vitality: Double): Double {
        return vitality * 0.02
    }

    /**
     * Formula: (0.5 * Crit) / (50 + 0.2 * Crit) * 100%
     */
    fun calculateCriticalChancePercentage(critChance: Double): Double {
        return (0.5 * critChance) / (50.0 + 0.2 * critChance) * 100.0
    }

    /**
     * Formula: 1 + (Critical Damage / 100)
     * 1:1 ratio
     */
    fun calculateCriticalDamageMultiplier(critDamage: Double): Double {
        return 1.0 + (critDamage / 100.0)
    }

    fun formatStatValue(key: String, value: Double): String = when (key) {
        "attack" -> String.format(Locale.ROOT, "(%.1f%%)", calculateAttackPercentage(value))
        "speed" -> String.format(Locale.ROOT, "(%.1f%%)", calculateSpeedPercentage(value))
        "defense" -> String.format(Locale.ROOT, "(%.1f%%)", calculateDefensePercentage(value))
        "vitality" -> String.format(Locale.ROOT, "(%.2f hp/s)", calculateVitalityHPPerSecond(value))
        "evasion" -> String.format(Locale.ROOT, "(%.1f%%)", calculateEvasionPercentage(value))
        "critical_chance" -> String.format(Locale.ROOT, "(%.1f%%)", calculateCriticalChancePercentage(value))
        "critical_damage" -> String.format(Locale.ROOT, "(%.2fx)", calculateCriticalDamageMultiplier(value))
        else -> ""
    }

    /**
     * Get all stat values from tablist as a map.
     * Keys: "attack", "speed", "defense", "vitality", "evasion", "critical_chance", "critical_damage"
     */
    fun getStatValues(): Map<String, Double> {
        val tabList = getTabList() ?: return emptyMap()

        val stats = mutableMapOf<String, Double>()
        for (line in tabList) {
            for ((key, pattern) in STAT_PATTERNS) {
                if (stats.containsKey(key)) continue
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    matcher.group(1).toDoubleOrNull()?.let { stats[key] = it }
                }
            }
            if (stats.size == STAT_PATTERNS.size) break
        }

        return stats
    }
}