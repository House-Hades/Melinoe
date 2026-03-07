package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.data.Item
import me.melinoe.utils.data.persistence.TypeSafeDataAccess
import me.melinoe.utils.data.persistence.TrackingKey

/**
 * Configuration for pity counter displays in dungeon completion messages.
 * This replaces the massive when-expression with a data-driven approach.
 */
object PityCounterConfig {
    
    /**
     * Builds the pity counter line for a dungeon/boss based on the items it drops.
     * Groups items by rarity, creating a hoverable text message for each rarity.
     *
     * @param dungeon Nullable, as World Bosses do not have a dungeon instance
     * @param boss The specific boss data to check drops for
     */
    fun buildPityLine(dungeon: DungeonData?, boss: BossData): String {
        // If the boss has no items, don't show a pity line
        if (boss.items.isEmpty()) return ""
        
        // Group all items dropped by this boss by their rarity
        val itemsByRarity = boss.items.groupBy { it.rarity }
        
        val segments = itemsByRarity.mapNotNull { (rarity, items) ->
            if (items.isEmpty()) return@mapNotNull null
            
            // Map the rarity to its icon
            val icon = when (rarity) {
                Item.Rarity.IRRADIATED -> Constants.ICON_IRRADIATED
                Item.Rarity.GILDED -> Constants.ICON_GILDED
                Item.Rarity.ROYAL -> Constants.ICON_ROYAL
                Item.Rarity.BLOODSHOT -> Constants.ICON_BLOODSHOT
                Item.Rarity.VOIDBOUND -> Constants.ICON_VOIDBOUND
                Item.Rarity.UNHOLY -> Constants.ICON_UNHOLY
                Item.Rarity.COMPANION -> Constants.ICON_COMPANION
                Item.Rarity.RUNE -> Constants.ICON_RUNE
            }
            
            // Map the rarity to its text color
            val color = when (rarity) {
                Item.Rarity.IRRADIATED -> "<#15CD15>"
                Item.Rarity.GILDED -> "<#DF5320>"
                Item.Rarity.ROYAL -> "<#AA00AA>"
                Item.Rarity.BLOODSHOT -> "<#AA0000>"
                Item.Rarity.VOIDBOUND -> "<#8D15F0>"
                Item.Rarity.UNHOLY -> "<#BFBFBF>"
                Item.Rarity.COMPANION -> "<#FFAA00>"
                Item.Rarity.RUNE -> "<#616161>"
            }
            
            // Build the hover text displaying each item and its pity count
            val hoverText = items.joinToString("<br>") { item ->
                val pity = TypeSafeDataAccess.get(TrackingKey.PityCounter(item.name)) ?: 0
                
                // Prevent MiniMessage from breaking because of items with an apostrophe
                val safeName = item.displayName.replace("'", "\\'")
                
                // Add the sprite icon using the item's texture path
                "<sprite:\"minecraft:blocks\":\"${item.texturePath}\"> $color$safeName<gray>: <white>$pity</white>"
            }
            
            "<hover:show_text:'$hoverText'><white>$icon</white></hover>"
        }
        
        if (segments.isEmpty()) return ""
        
        // Join all grouped rarities with the divider
        return segments.joinToString(" <dark_gray>| <reset>")
    }
}
