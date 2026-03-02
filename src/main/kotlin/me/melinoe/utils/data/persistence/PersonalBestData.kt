package me.melinoe.utils.data.persistence

import java.time.Instant

/**
 * Data class representing a personal best record with metadata.
 */
data class PersonalBestRecord(
    val time: Float,
    val date: String = Instant.now().toString(),
    val attempts: Int = 0,
    val splits: Map<String, Float> = emptyMap()
) {
    companion object {
        /**
         * Create a PersonalBestRecord from a simple time value (for migration).
         */
        fun fromTime(time: Float): PersonalBestRecord {
            return PersonalBestRecord(time = time)
        }
    }
}

/**
 * Container for all personal best data with enhanced structure.
 */
data class PersonalBestData(
    val version: String = "2.0",
    val dungeons: MutableMap<String, PersonalBestRecord> = mutableMapOf(),
    val bosses: MutableMap<String, PersonalBestRecord> = mutableMapOf()
)
