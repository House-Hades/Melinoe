package me.melinoe.utils.data.persistence

/**
 * Type-safe keys for tracking data storage.
 * Uses sealed classes to prevent typos and provide compile-time safety.
 * 
 * Each key type is parameterized with its value type (T) to ensure type safety
 * when getting/setting values in the data store.
 */
sealed class TrackingKey<T> {
    abstract val key: String
    
    /**
     * Pity counters for boss drops (bloodshot bags, unholy items, etc.)
     * Key format: "pity_<bossName>"
     */
    data class PityCounter(val bossName: String) : TrackingKey<Int>() {
        override val key: String = "pity_$bossName"
    }
    
    /**
     * Lifetime statistics (total runs, bag drops, etc.)
     * Uses sealed class for predefined stats to prevent typos.
     */
    sealed class LifetimeStat(override val key: String) : TrackingKey<Int>() {
        // Run counters
        data object TotalRuns : LifetimeStat("totalRuns")
        
        // Bag drop counters
        data object BloodshotBags : LifetimeStat("bloodshotBags")
        data object UnholyBags : LifetimeStat("unholy")
        data object VoidboundBags : LifetimeStat("voidbound")
        data object RoyalBags : LifetimeStat("royalBags")
        data object CompanionBags : LifetimeStat("companionBags")
        data object EventBags : LifetimeStat("eventBags")
        
        // Special item counters
        data object NihilityDrops : LifetimeStat("nihility")
        
        companion object {
            /**
             * Get all lifetime stat keys for iteration/display purposes.
             */
            fun all(): List<LifetimeStat> = listOf(
                TotalRuns,
                BloodshotBags,
                UnholyBags,
                VoidboundBags,
                RoyalBags,
                CompanionBags,
                EventBags,
                NihilityDrops
            )
        }
    }
    
    /**
     * Personal best records for dungeons and bosses.
     * Key format: "pb_<dungeonOrBossName>"
     */
    data class PersonalBest(val name: String) : TrackingKey<PersonalBestRecord>() {
        override val key: String = "pb_$name"
    }
    
    /**
     * Tracking metadata (last update time, version, etc.)
     */
    sealed class Metadata(override val key: String) : TrackingKey<String>() {
        data object LastUpdateTime : Metadata("lastUpdateTime")
        data object DataVersion : Metadata("dataVersion")
        data object PlayerUUID : Metadata("playerUUID")
    }
}
