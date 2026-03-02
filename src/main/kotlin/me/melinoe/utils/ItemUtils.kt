package me.melinoe.utils

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

/**
 * Utility functions for working with items.
 */
object ItemUtils {

    private val ITEM_RANGE_PATTERN = Pattern.compile("Range: (\\d+(\\.\\d+)?)")
    private val COOLDOWN_PATTERN = Pattern.compile("Cooldown: (\\d+(\\.\\d+)?)s")

    /**
     * Special item types with custom ranges and offsets.
     * All item data is stored directly in the enum for maintainability.
     * 
     * @param unicode The Unicode character identifier for this item
     * @param range The range value for this item (-1 if no range)
     * @param offset The offset value for range calculation
     * @param displayName The human-readable display name
     */
    enum class ItemType(
        val unicode: String,
        val range: Float,
        val offset: Float,
        val displayName: String
    ) {
        UT_HERALD_ESSENCE("\uD83E\uDF45", 6f, 3f, "Herald Essence"),
        EX_HERALD_ESSENCE("\uD83E\uDF46", 6f, 3f, "Herald Essence"),
        UT_AYAHUASCA_FLASK("\uD83E\uDF9D", 8f, 0f, "Ayahuasca Flask"),
        EX_AYAHUASCA_FLASK("\uD83E\uDF9C", 8f, 0f, "Ayahuasca Flask"),
        UT_MALICE("\uD83D\uDD25", 7f, 0f, "Malice"),
        EX_MALICE("\uD83D\uDE18", 7f, 0f, "Malice"),
        UT_HORIZON("\uD815\uDC74", 8f, 0f, "Horizon"),
        EX_HORIZON("\uD815\uDC75", 8f, 0f, "Horizon"),
        UT_NATURE("\uD815\uDC34", -1f, 0f, "Nature's Gift"),
        EX_NATURE("\uD815\uDC35", -1f, 0f, "Nature's Gift");

        /**
         * Check if this item type is Herald Essence (has special center-only rendering)
         */
        val isHeraldEssence: Boolean
            get() = this == UT_HERALD_ESSENCE || this == EX_HERALD_ESSENCE

        companion object {
            // O(1) lookup map for Unicode -> ItemType
            private val unicodeMap: Map<String, ItemType> = values().associateBy { it.unicode }

            /**
             * Find an ItemType by its Unicode character.
             */
            fun fromUnicode(unicode: String): ItemType? = unicodeMap[unicode]

            /**
             * Find an ItemType from an ItemStack by extracting its Unicode identifier.
             */
            fun fromItemStack(item: ItemStack): ItemType? {
                if (item.isEmpty) return null
                
                // Get the plain text name (strips formatting but keeps Unicode)
                val plainName = item.hoverName.string.trim()
                
                if (plainName.length < 2) return null
                
                // Extract Unicode character (typically between first and last char)
                val unicode = plainName.substring(1, plainName.length - 1)
                
                return unicodeMap[unicode]
            }
        }
    }
    
    /**
     * Get the plain text name from an ItemStack (strips formatting, keeps Unicode)
     */
    fun getPlainName(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return stack.hoverName.string.trim()
    }
    
    /**
     * Get the display name without Unicode characters.
     * Extracts the actual item display name from custom display component
     */
    fun getDisplayName(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        
        // Try to get the custom name from item components (this is what shows in inventory)
        val customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
        if (customName != null) {
            // Strip formatting from custom name
            val stripped = net.minecraft.ChatFormatting.stripFormatting(customName.string) ?: customName.string
            return stripped.trim()
        }
        
        // Fallback to hover name if no custom name
        val rawString = stack.hoverName.string
        val stripped = net.minecraft.ChatFormatting.stripFormatting(rawString) ?: rawString
        return stripped.trim()
    }

    /**
     * Parse the range value from an item's lore.
     * Returns -1 if no range is found.
     */
    fun parseItemRange(stack: ItemStack): Float {
        val loreComponent = stack.get(DataComponents.LORE) ?: return -1f

        val rangeMatcher = ITEM_RANGE_PATTERN.matcher("")

        for (line in loreComponent.lines()) {
            rangeMatcher.reset(line.string)

            if (rangeMatcher.find()) {
                val rangeString = rangeMatcher.group(1)
                return rangeString?.toFloatOrNull() ?: -1f
            }
        }

        return -1f
    }
    
    /**
     * Parse the cooldown value from an item's lore.
     * Returns -1 if no cooldown is found.
     */
    fun parseItemCooldown(stack: ItemStack): Float {
        val loreComponent = stack.get(DataComponents.LORE) ?: return -1f

        val cooldownMatcher = COOLDOWN_PATTERN.matcher("")

        for (line in loreComponent.lines()) {
            cooldownMatcher.reset(line.string)

            if (cooldownMatcher.find()) {
                val cooldownString = cooldownMatcher.group(1)
                return cooldownString?.toFloatOrNull() ?: -1f
            }
        }

        return -1f
    }

    /**
     * Get the range for an item, including special handling for specific items.
     * Returns a Pair of (range, offset) where offset is used for items like Herald Essence.
     */
    fun getItemRangeWithOffset(stack: ItemStack): Pair<Float, Float> {
        // Check for special items first
        val itemType = ItemType.fromItemStack(stack)
        
        if (itemType != null) {
            // Use data directly from enum
            return Pair(itemType.range, itemType.offset)
        }
        
        // Default: parse from lore
        val range = parseItemRange(stack)
        return Pair(range, 0f)
    }
    
    /**
     * Check if an item is Herald Essence (which should only show center square, not full range)
     */
    fun isHeraldEssence(stack: ItemStack): Boolean {
        val itemType = ItemType.fromItemStack(stack)
        return itemType?.isHeraldEssence ?: false
    }
    
    /**
     * Check if an item is a weapon (shovel items)
     */
    fun isWeapon(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return itemId.endsWith("_shovel")
    }
    
    /**
     * Check if an item is an ability (hoe items)
     */
    fun isAbility(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return itemId.endsWith("_hoe")
    }
}
