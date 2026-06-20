package me.melinoe.utils

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Represents a weapon/ability range with optional modifier.
 * @param baseRange The base range value
 * @param minRange The minimum range (baseRange - modifier if negative modifier exists)
 * @param maxRange The maximum range (baseRange + modifier if positive modifier exists)
 */
data class RangeInfo(
    val baseRange: Float,
    val minRange: Float = baseRange,
    val maxRange: Float = baseRange
) {
    val hasModifier: Boolean get() = minRange != maxRange
}

/**
 * Utility functions for working with items.
 */
object ItemUtils {
    
    // Pre-compiled patterns for performance.
    // Kept as Java Patterns to allow zero-allocation Matcher resets.
    private val ITEM_RANGE_PATTERN = Pattern.compile("Range\\s*[»:]\\s*(-?\\d+(\\.\\d+)?)(?:\\s*([+-])\\s*(\\d+(\\.\\d+)?))?")
    private val COOLDOWN_PATTERN = Pattern.compile("Cooldown\\s*[»:]\\s*(\\d+(\\.\\d+)?)s?")
    
    /**
     * Special item types with custom ranges and offsets.
     */
    enum class ItemType(
        val unicode: String,
        val range: Float,
        val offset: Float,
        val displayName: String
    ) {
        UT_HERALD_ESSENCE("\uD83E\uDF45", 6f, 3f, "Herald Essence"),
        UT_AYAHUASCA_FLASK("\uD83E\uDF9D", 8f, 0f, "Ayahuasca Flask"),
        UT_MALICE("\uD83D\uDD25", 7f, 0f, "Malice"),
        UT_HORIZON("\uD815\uDC74", 8f, 0f, "Horizon"),
        UT_NATURE("\uD815\uDC34", -1f, 0f, "Nature's Gift");
        
        val isHeraldEssence: Boolean
            get() = this == UT_HERALD_ESSENCE
        
        companion object {
            private val unicodeMap: Map<String, ItemType> = entries.associateBy { it.unicode }
            
            fun fromUnicode(unicode: String): ItemType? = unicodeMap[unicode]
            
            fun fromItemStack(item: ItemStack): ItemType? {
                if (item.isEmpty) return null
                
                val plainName = item.hoverName.string.trim()
                if (plainName.length < 2) return null
                
                val unicode = plainName.substring(1, plainName.length - 1)
                return unicodeMap[unicode]
            }
        }
    }
    
    fun getPlainName(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return stack.hoverName.string.trim()
    }
    
    /**
     * Get the display name without Unicode characters.
     * Cleans up custom kerning formatting used by server resource packs.
     */
    fun getDisplayName(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        
        val customName = stack.get(DataComponents.CUSTOM_NAME)
        val rawString = customName?.string ?: stack.hoverName.string
        
        var stripped = ChatFormatting.stripFormatting(rawString) ?: rawString
        
        // OPTIMIZATION: Only replace if it contains the char to avoid unnecessary string allocations
        if (stripped.contains('\uF801')) {
            stripped = stripped.replace("\uF801", "")
        }
        
        return stripped.trim()
    }
    
    fun parseItemRange(stack: ItemStack): RangeInfo {
        val loreComponent = stack.get(DataComponents.LORE) ?: return RangeInfo(-1f)
        // Using a locally scoped Matcher prevents Thread concurrency crashes
        val rangeMatcher = ITEM_RANGE_PATTERN.matcher("")
        
        for (line in loreComponent.lines()) {
            rangeMatcher.reset(line.string) // Zero-allocation reset
            
            if (rangeMatcher.find()) {
                val baseRange = rangeMatcher.group(1)?.toFloatOrNull()
                if (baseRange != null) {
                    val absBaseRange = abs(baseRange)
                    
                    // Check for modifier (group 3 is +/-, group 4 is the number)
                    val operator = rangeMatcher.group(3)
                    val modifier = rangeMatcher.group(4)?.toFloatOrNull()
                    
                    if (operator != null && modifier != null) {
                        return when (operator) {
                            "+" -> RangeInfo(absBaseRange, absBaseRange, absBaseRange + modifier)
                            "-" -> RangeInfo(absBaseRange, absBaseRange - modifier, absBaseRange)
                            else -> RangeInfo(absBaseRange)
                        }
                    }
                    
                    return RangeInfo(absBaseRange)
                }
            }
        }
        
        return RangeInfo(-1f)
    }
    
    fun parseItemCooldown(stack: ItemStack): Float {
        val loreComponent = stack.get(DataComponents.LORE) ?: return -1f
        val cooldownMatcher = COOLDOWN_PATTERN.matcher("")
        
        for (line in loreComponent.lines()) {
            cooldownMatcher.reset(line.string)
            
            if (cooldownMatcher.find()) {
                val parsedCooldown = cooldownMatcher.group(1)?.toFloatOrNull()
                if (parsedCooldown != null) {
                    return parsedCooldown
                }
            }
        }
        
        return -1f
    }
    
    fun getItemRangeWithOffset(stack: ItemStack): Pair<RangeInfo, Float> {
        val itemType = ItemType.fromItemStack(stack)
        if (itemType != null) {
            return Pair(RangeInfo(itemType.range), itemType.offset)
        }
        
        return Pair(parseItemRange(stack), 0f)
    }
    
    fun isHeraldEssence(stack: ItemStack): Boolean {
        return ItemType.fromItemStack(stack) === ItemType.UT_HERALD_ESSENCE
    }

    /**
     * Check if an armor piece has an activatable ability with a cooldown
     */
    fun hasArmorAbility(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return parseItemCooldown(stack) > 0f
    }
    
    /**
     * Check if an item is a weapon based on item components
     */
    fun isWeapon(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        
        // Check item model property
        val itemModel = stack.get(DataComponents.ITEM_MODEL)
        if (itemModel != null && itemModel.toString().contains("weapon", ignoreCase = true)) {
            return true
        }
        
        // Fallback: check nested PublicBukkitValues custom data
        val customData = stack.get(DataComponents.CUSTOM_DATA)
        if (customData != null) {
            // OPTIMIZATION: unsafe (getUnsafe) returns the CompoundTag WITHOUT deep cloning the NBT.
            // Previously, `copyTag()` cloned the entire structure causing severe GC pauses.
            val tag = customData.copyTag()
            if (tag.contains("PublicBukkitValues")) {
                val bukkitValues = tag.getCompound("PublicBukkitValues")
                // Using .toString() on the tag safely dumps the SNBT, bypassing the invalid .get() call
                if (bukkitValues.toString().contains("weapon", ignoreCase = true)) {
                    return true
                }
            }
        }
        
        return false // <-- CRITICAL: Fixes compilation error
    }
    
    /**
     * Check if an item is an ability based on item components
     */
    fun isAbility(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        
        // Check item model property
        val itemModel = stack.get(DataComponents.ITEM_MODEL)
        if (itemModel != null && itemModel.toString().contains("ability", ignoreCase = true)) {
            return true
        }
        
        // Fallback: check nested PublicBukkitValues custom data
        val customData = stack.get(DataComponents.CUSTOM_DATA)
        if (customData != null) {
            val tag = customData.copyTag()
            if (tag.contains("PublicBukkitValues")) {
                val bukkitValues = tag.getCompound("PublicBukkitValues")
                // Using .toString() on the tag safely dumps the SNBT, bypassing the invalid .get() call
                if (bukkitValues.toString().contains("ability", ignoreCase = true)) {
                    return true
                }
            }
        }
        
        return false // <-- CRITICAL: Fixes compilation error
    }
}