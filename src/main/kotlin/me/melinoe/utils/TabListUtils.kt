package me.melinoe.utils

import me.melinoe.Melinoe
import java.util.regex.Pattern
import java.util.ConcurrentModificationException

/**
 * Tab list parsing utilities for extracting server information.
 * Optimized to parse tab list once and extract multiple values in a single pass.
 */
object TabListUtils {
    private val TPS_PATTERN = Pattern.compile("^TPS:\\s(\\d+)$")
    private val FAME_PATTERN = Pattern.compile("^Fame:\\s(\\d+)$")
    private val LEVEL_PATTERN = Pattern.compile("^Class:(.+)$")
    private val SERVER_PATTERN = Pattern.compile("^Server:\\s(.+)$")
    private val LOOTBOOST_PATTERN = Pattern.compile("^Loot Boost:\\s\\+(\\d+)$")
    private val SPEED_PATTERN = Pattern.compile("^Speed:\\s\\+(\\d+(?:\\.\\d+)?)$")
    private val EVASION_PATTERN = Pattern.compile("^Evasion:\\s\\+(\\d+(?:\\.\\d+)?)$")
    
    // Regex for non-ASCII character removal
    private val NON_ASCII_REGEX = Regex("[^\\p{ASCII}]")

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
     * Only works on Telos server.
     */
    private fun getTabList(): List<String>? {
        // Only parse tab list on Telos server
        if (!ServerUtils.isOnTelos()) return null
        
        val networkHandler = Melinoe.mc.connection ?: return null
        val playerCollection = networkHandler.onlinePlayers

        if (playerCollection.isEmpty()) return null

        // Wrap in try-catch as defense-in-depth for edge cases on Telos
        return try {
            playerCollection
                .mapNotNull { it.tabListDisplayName?.string }
                .map { it.noControlCodes.trim() }
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
        
        // Parse all lines in a single pass
        for (line in tabList) {
            when {
                charInfo == null -> {
                    val matcher = LEVEL_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        charInfo = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                server == null -> {
                    val matcher = SERVER_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        server = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                tps == null -> {
                    val matcher = TPS_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        tps = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                fame == null -> {
                    val matcher = FAME_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        fame = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                lootboost == null -> {
                    val matcher = LOOTBOOST_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        lootboost = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                speed == null -> {
                    val matcher = SPEED_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        speed = matcher.group(1)
                        continue
                    }
                }
            }
            
            when {
                evasion == null -> {
                    val matcher = EVASION_PATTERN.matcher(line)
                    if (matcher.matches()) {
                        evasion = matcher.group(1)
                        continue
                    }
                }
            }
            
            // Early exit if we found everything
            if (charInfo != null && server != null && tps != null && 
                fame != null && lootboost != null && speed != null && evasion != null) {
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
            if (matcher.matches()) {
                return matcher.group(1)
            }
        }
        return null
    }

    /**
     * Get TPS from tab list.
     */
    fun getTPS(): String? = getLineMatches(TPS_PATTERN)

    /**
     * Get server name from tab list.
     */
    fun getServer(): String? = getLineMatches(SERVER_PATTERN)

    /**
     * Get character info (class) from tab list.
     */
    fun getCharInfo(): String? = getLineMatches(LEVEL_PATTERN)

    /**
     * Get fame from tab list.
     */
    fun getFame(): String? = getLineMatches(FAME_PATTERN)

    /**
     * Get loot boost from tab list.
     */
    fun getLootboost(): String? = getLineMatches(LOOTBOOST_PATTERN)

    /**
     * Get speed from tab list.
     */
    fun getSpeed(): String? = getLineMatches(SPEED_PATTERN)

    /**
     * Get evasion from tab list.
     */
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
     * Uses efficient noControlCodes extension and regex for non-ASCII.
     */
    fun stripAllFormatting(input: String): String {
        return input.noControlCodes.replace(NON_ASCII_REGEX, "")
    }
}
