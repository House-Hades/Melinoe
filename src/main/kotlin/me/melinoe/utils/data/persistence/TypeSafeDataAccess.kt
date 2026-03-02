package me.melinoe.utils.data.persistence

import me.melinoe.Melinoe

/**
 * Type-safe wrapper for DataConfig using TrackingKey sealed classes.
 * Provides compile-time safety and prevents typos in key names.
 * 
 * This is the recommended way to access tracking data.
 */
object TypeSafeDataAccess {
    
    /**
     * Get a value from the data store using a type-safe key.
     * Returns null if the key doesn't exist.
     */
    fun <T> get(key: TrackingKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (key) {
            is TrackingKey.PityCounter -> DataConfig.getPityCounter(key.bossName) as T
            is TrackingKey.LifetimeStat -> DataConfig.getLifetimeStat(key.key) as T
            is TrackingKey.PersonalBest -> DataConfig.getPersonalBestRecord(key.name) as T
            is TrackingKey.Metadata -> DataConfig.getTrackingMetadata(key.key) as T
        }
    }
    
    /**
     * Get a value with a default if it doesn't exist.
     */
    fun <T> getOrDefault(key: TrackingKey<T>, default: T): T {
        return get(key) ?: default
    }
    
    /**
     * Set a value in the data store using a type-safe key.
     * This triggers update callbacks for UI refresh.
     */
    fun <T> set(key: TrackingKey<T>, value: T) {
        when (key) {
            is TrackingKey.PityCounter -> {
                require(value is Int) { "PityCounter value must be Int" }
                DataConfig.setPityCounter(key.bossName, value)
            }
            is TrackingKey.LifetimeStat -> {
                require(value is Int) { "LifetimeStat value must be Int" }
                DataConfig.setLifetimeStat(key.key, value)
            }
            is TrackingKey.PersonalBest -> {
                require(value is PersonalBestRecord) { "PersonalBest value must be PersonalBestRecord" }
                DataConfig.setPersonalBestRecord(key.name, value)
            }
            is TrackingKey.Metadata -> {
                require(value is String) { "Metadata value must be String" }
                DataConfig.setTrackingMetadata(key.key, value)
            }
        }
    }
    
    /**
     * Increment an integer value (for pity counters and lifetime stats).
     * Returns the new value.
     */
    fun increment(key: TrackingKey<Int>): Int {
        val current = get(key) ?: 0
        val newValue = current + 1
        set(key, newValue)
        return newValue
    }
    
    /**
     * Decrement an integer value (for pity counters and lifetime stats).
     * Returns the new value. Won't go below 0.
     */
    fun decrement(key: TrackingKey<Int>): Int {
        val current = get(key) ?: 0
        val newValue = maxOf(0, current - 1)
        set(key, newValue)
        return newValue
    }
    
    /**
     * Reset an integer value to 0.
     */
    fun reset(key: TrackingKey<Int>) {
        set(key, 0)
    }
    
    /**
     * Update a personal best if the new time is better.
     * Returns true if a new PB was set.
     */
    fun updatePersonalBestIfBetter(
        key: TrackingKey.PersonalBest,
        newTime: Float,
        splits: Map<String, Float> = emptyMap()
    ): Boolean {
        val current = get(key)
        val currentTime = current?.time ?: Float.MAX_VALUE
        
        if (newTime < currentTime) {
            val newRecord = PersonalBestRecord(
                time = newTime,
                date = java.time.Instant.now().toString(),
                splits = splits
            )
            set(key, newRecord)
            return true
        }
        return false
    }
    
    // ==================== CONVENIENCE METHODS ====================
    
    /**
     * Get all pity counters for a list of bosses.
     */
    fun getPityCounters(bosses: List<String>): Map<String, Int> {
        return bosses.associateWith { boss ->
            get(TrackingKey.PityCounter(boss)) ?: 0
        }
    }
    
    /**
     * Get all lifetime stats.
     */
    fun getAllLifetimeStats(): Map<TrackingKey.LifetimeStat, Int> {
        return TrackingKey.LifetimeStat.all().associateWith { stat ->
            get(stat) ?: 0
        }
    }
    
    /**
     * Check if a personal best exists.
     */
    fun hasPersonalBest(dungeonName: String): Boolean {
        return get(TrackingKey.PersonalBest(dungeonName)) != null
    }
}
