package me.melinoe.utils.data.persistence

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.melinoe.Melinoe
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * New centralized configuration manager for tracking data.
 * Uses async I/O, compression, and backup capabilities.
 */
object DataConfig {
    private const val DATA_DIR = "config/melinoe/data"
    private const val PITY_COUNTERS_FILE = "pity_counters.json"
    private const val LIFETIME_STATS_FILE = "lifetime_stats.json"
    private const val PERSONAL_BESTS_FILE = "personal_bests.json"
    private const val TRACKING_METADATA_FILE = "tracking_metadata.json"
    
    private val GSON = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Async persistence manager
    private val asyncPersistence = AsyncConfigPersistence(scope, debounceDelayMs = 5000L)
    
    // Data storage
    private val pityCounters = ConcurrentHashMap<String, Int>()
    private val lifetimeStats = ConcurrentHashMap<String, Int>()
    private val personalBests = ConcurrentHashMap<String, Float>() // Legacy format for backward compatibility
    private val personalBestRecords = ConcurrentHashMap<String, PersonalBestRecord>() // New enhanced format
    private val trackingMetadata = ConcurrentHashMap<String, Any>()
    
    // Callback support for modules that need instant updates
    private val updateCallbacks = mutableListOf<() -> Unit>()
    
    // Backup manager
    private val backupManager = BackupManager
    
    @Volatile
    private var initialized = false
    
    /**
     * Register a callback to be notified when tracking data changes.
     * Useful for modules that need instant updates (e.g., HUD displays).
     */
    fun registerUpdateCallback(callback: () -> Unit) {
        updateCallbacks.add(callback)
    }
    
    /**
     * Notify all registered callbacks that data has changed.
     */
    private fun notifyCallbacks() {
        updateCallbacks.forEach { callback ->
            try {
                callback()
            } catch (e: Exception) {
                Melinoe.logger.error("Error in update callback: ${e.message}", e)
            }
        }
    }
    
    /**
     * Initialize the data config system.
     * Loads all data from files and sets up backup directory.
     */
    fun initialize() {
        if (initialized) {
            Melinoe.logger.warn("DataConfig already initialized")
            return
        }
        
        try {
            createDataDirectory()
            runBlocking {
                loadAllData()
            }
            initialized = true
            Melinoe.logger.info("DataConfig initialized successfully")
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to initialize DataConfig: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Create the data directory if it doesn't exist.
     */
    private fun createDataDirectory() {
        try {
            val dataPath = Paths.get(DATA_DIR)
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath)
                Melinoe.logger.info("Created data directory: $DATA_DIR")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to create data directory: ${e.message}", e)
            throw e
        }
    }
    
    // ==================== READ OPERATIONS ====================
    
    /**
     * Get pity counter value for a specific boss.
     */
    fun getPityCounter(bossName: String): Int {
        return pityCounters.getOrDefault(bossName, 0)
    }
    
    /**
     * Get lifetime stat value.
     */
    fun getLifetimeStat(statName: String): Int {
        return lifetimeStats.getOrDefault(statName, 0)
    }
    
    /**
     * Get personal best time for a dungeon.
     */
    fun getPersonalBest(dungeonName: String): Float {
        return personalBestRecords[dungeonName]?.time ?: personalBests.getOrDefault(dungeonName, 0.0f)
    }
    
    /**
     * Get personal best record with metadata for a dungeon.
     */
    fun getPersonalBestRecord(dungeonName: String): PersonalBestRecord? {
        return personalBestRecords[dungeonName]
    }
    
    /**
     * Get tracking metadata.
     */
    fun getTrackingMetadata(key: String): Any? {
        return trackingMetadata[key]
    }
    
    // ==================== WRITE OPERATIONS ====================
    
    /**
     * Set pity counter value for a specific boss.
     */
    fun setPityCounter(bossName: String, value: Int) {
        pityCounters[bossName] = value
        notifyCallbacks()
        asyncPersistence.scheduleSave("pityCounters") {
            savePityCounters()
        }
    }
    
    /**
     * Set lifetime stat value.
     */
    fun setLifetimeStat(statName: String, value: Int) {
        lifetimeStats[statName] = value
        notifyCallbacks()
        asyncPersistence.scheduleSave("lifetimeStats") {
            saveLifetimeStats()
        }
    }
    
    /**
     * Set personal best time for a dungeon (simple version).
     */
    fun setPersonalBest(dungeonName: String, timeInSeconds: Float) {
        val existingRecord = personalBestRecords[dungeonName]
        val newRecord = if (existingRecord != null) {
            existingRecord.copy(
                time = timeInSeconds,
                date = java.time.Instant.now().toString(),
                attempts = existingRecord.attempts + 1
            )
        } else {
            PersonalBestRecord(time = timeInSeconds, attempts = 1)
        }
        
        personalBestRecords[dungeonName] = newRecord
        notifyCallbacks()
        asyncPersistence.scheduleSave("personalBests") {
            savePersonalBests()
        }
    }
    
    /**
     * Set personal best record with full metadata.
     */
    fun setPersonalBestRecord(dungeonName: String, record: PersonalBestRecord) {
        personalBestRecords[dungeonName] = record
        notifyCallbacks()
        asyncPersistence.scheduleSave("personalBests") {
            savePersonalBests()
        }
    }
    
    /**
     * Update personal best with boss splits.
     */
    fun setPersonalBestWithSplits(dungeonName: String, timeInSeconds: Float, splits: Map<String, Float>) {
        val existingRecord = personalBestRecords[dungeonName]
        val newRecord = PersonalBestRecord(
            time = timeInSeconds,
            date = java.time.Instant.now().toString(),
            attempts = (existingRecord?.attempts ?: 0) + 1,
            splits = splits
        )
        
        personalBestRecords[dungeonName] = newRecord
        notifyCallbacks()
        asyncPersistence.scheduleSave("personalBests") {
            savePersonalBests()
        }
    }
    
    /**
     * Set tracking metadata.
     */
    fun setTrackingMetadata(key: String, value: Any) {
        trackingMetadata[key] = value
        notifyCallbacks()
        asyncPersistence.scheduleSave("trackingMetadata") {
            saveTrackingMetadata()
        }
    }
    
    // ==================== PERSISTENCE OPERATIONS ====================
    
    /**
     * Load all data from files.
     */
    suspend fun loadAllData() {
        loadPityCounters()
        loadLifetimeStats()
        loadPersonalBests()
        loadTrackingMetadata()
        Melinoe.logger.info("Loaded all tracking data")
    }
    
    /**
     * Save all data to files.
     */
    suspend fun saveAllData() {
        savePityCounters()
        saveLifetimeStats()
        savePersonalBests()
        saveTrackingMetadata()
        Melinoe.logger.info("Saved all tracking data")
    }
    
    /**
     * Load pity counters from file.
     */
    private fun loadPityCounters() {
        try {
            val filePath = Paths.get(DATA_DIR, PITY_COUNTERS_FILE)
            if (Files.exists(filePath)) {
                val content = Files.readString(filePath)
                val json = JsonParser.parseString(content).asJsonObject
                
                pityCounters.clear()
                for ((key, value) in json.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonPrimitive) {
                        pityCounters[key] = value.asInt
                    }
                }
                Melinoe.logger.info("Loaded ${pityCounters.size} pity counters")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to load pity counters: ${e.message}", e)
        }
    }
    
    /**
     * Save pity counters to file.
     */
    private fun savePityCounters() {
        try {
            val filePath = Paths.get(DATA_DIR, PITY_COUNTERS_FILE)
            val json = JsonObject()
            
            for ((key, value) in pityCounters) {
                json.addProperty(key, value)
            }
            
            json.addProperty("_version", "1.0")
            
            Files.writeString(filePath, GSON.toJson(json))
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to save pity counters: ${e.message}", e)
        }
    }
    
    /**
     * Load lifetime stats from file.
     */
    private fun loadLifetimeStats() {
        try {
            val filePath = Paths.get(DATA_DIR, LIFETIME_STATS_FILE)
            if (Files.exists(filePath)) {
                val content = Files.readString(filePath)
                val json = JsonParser.parseString(content).asJsonObject
                
                lifetimeStats.clear()
                for ((key, value) in json.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonPrimitive) {
                        lifetimeStats[key] = value.asInt
                    }
                }
                Melinoe.logger.info("Loaded ${lifetimeStats.size} lifetime stats")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to load lifetime stats: ${e.message}", e)
        }
    }
    
    /**
     * Save lifetime stats to file.
     */
    private fun saveLifetimeStats() {
        try {
            val filePath = Paths.get(DATA_DIR, LIFETIME_STATS_FILE)
            val json = JsonObject()
            
            for ((key, value) in lifetimeStats) {
                json.addProperty(key, value)
            }
            
            json.addProperty("_version", "1.0")
            
            Files.writeString(filePath, GSON.toJson(json))
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to save lifetime stats: ${e.message}", e)
        }
    }
    
    /**
     * Load personal bests from file.
     * Supports both v1.0 (simple) and v2.0 (enhanced) formats.
     */
    private fun loadPersonalBests() {
        try {
            val filePath = Paths.get(DATA_DIR, PERSONAL_BESTS_FILE)
            if (Files.exists(filePath)) {
                val content = Files.readString(filePath)
                val json = JsonParser.parseString(content).asJsonObject
                
                val version = json.get("_version")?.asString ?: "1.0"
                
                personalBests.clear()
                personalBestRecords.clear()
                
                when (version) {
                    "2.0" -> {
                        // Load enhanced format
                        val dungeonsObj = json.getAsJsonObject("dungeons")
                        val bossesObj = json.getAsJsonObject("bosses")
                        
                        dungeonsObj?.entrySet()?.forEach { (key, value) ->
                            if (value.isJsonObject) {
                                val record = parsePersonalBestRecord(value.asJsonObject)
                                personalBestRecords[key] = record
                            }
                        }
                        
                        bossesObj?.entrySet()?.forEach { (key, value) ->
                            if (value.isJsonObject) {
                                val record = parsePersonalBestRecord(value.asJsonObject)
                                personalBestRecords[key] = record
                            }
                        }
                        
                        Melinoe.logger.info("Loaded ${personalBestRecords.size} personal bests (v2.0)")
                    }
                    else -> {
                        // Load legacy format (v1.0) and migrate
                        for ((key, value) in json.entrySet()) {
                            if (!key.startsWith("_") && value.isJsonPrimitive) {
                                val time = value.asFloat
                                personalBests[key] = time
                                personalBestRecords[key] = PersonalBestRecord.fromTime(time)
                            }
                        }
                        Melinoe.logger.info("Loaded ${personalBests.size} personal bests (v1.0 - migrated to v2.0)")
                        
                        // Save in new format
                        savePersonalBests()
                    }
                }
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to load personal bests: ${e.message}", e)
        }
    }
    
    /**
     * Parse a PersonalBestRecord from JSON.
     */
    private fun parsePersonalBestRecord(json: JsonObject): PersonalBestRecord {
        val time = json.get("time")?.asFloat ?: 0f
        val date = json.get("date")?.asString ?: java.time.Instant.now().toString()
        val attempts = json.get("attempts")?.asInt ?: 0
        
        val splits = mutableMapOf<String, Float>()
        json.getAsJsonObject("splits")?.entrySet()?.forEach { (key, value) ->
            if (value.isJsonPrimitive) {
                splits[key] = value.asFloat
            }
        }
        
        return PersonalBestRecord(time, date, attempts, splits)
    }
    
    /**
     * Save personal bests to file in enhanced v2.0 format.
     */
    private fun savePersonalBests() {
        try {
            val filePath = Paths.get(DATA_DIR, PERSONAL_BESTS_FILE)
            val json = JsonObject()
            
            json.addProperty("_version", "2.0")
            
            val dungeonsObj = JsonObject()
            val bossesObj = JsonObject()
            
            for ((key, record) in personalBestRecords) {
                val recordJson = JsonObject()
                recordJson.addProperty("time", record.time)
                recordJson.addProperty("date", record.date)
                recordJson.addProperty("attempts", record.attempts)
                
                if (record.splits.isNotEmpty()) {
                    val splitsJson = JsonObject()
                    for ((splitName, splitTime) in record.splits) {
                        splitsJson.addProperty(splitName, splitTime)
                    }
                    recordJson.add("splits", splitsJson)
                }
                
                // Determine if it's a dungeon or boss based on naming convention
                // Dungeons typically have longer names, bosses are shorter
                // You can adjust this logic based on your actual data structure
                if (isDungeonName(key)) {
                    dungeonsObj.add(key, recordJson)
                } else {
                    bossesObj.add(key, recordJson)
                }
            }
            
            json.add("dungeons", dungeonsObj)
            json.add("bosses", bossesObj)
            
            Files.writeString(filePath, GSON.toJson(json))
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to save personal bests: ${e.message}", e)
        }
    }
    
    /**
     * Determine if a name represents a dungeon (vs a boss).
     * Dungeons: "Rustborn Kingdom", "Celestials Province", etc.
     * Bosses: "Asmodeus", "Valerion", etc.
     */
    private fun isDungeonName(name: String): Boolean {
        // Dungeons typically contain spaces or specific keywords
        return name.contains(" ") || 
               name.contains("Kingdom") || 
               name.contains("Province") || 
               name.contains("Creation") ||
               name.contains("Lowlands")
    }
    
    /**
     * Load tracking metadata from file.
     */
    private fun loadTrackingMetadata() {
        try {
            val filePath = Paths.get(DATA_DIR, TRACKING_METADATA_FILE)
            if (Files.exists(filePath)) {
                val content = Files.readString(filePath)
                val json = JsonParser.parseString(content).asJsonObject
                
                trackingMetadata.clear()
                for ((key, value) in json.entrySet()) {
                    if (!key.startsWith("_")) {
                        trackingMetadata[key] = value
                    }
                }
                Melinoe.logger.info("Loaded tracking metadata")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to load tracking metadata: ${e.message}", e)
        }
    }
    
    /**
     * Save tracking metadata to file.
     */
    private fun saveTrackingMetadata() {
        try {
            val filePath = Paths.get(DATA_DIR, TRACKING_METADATA_FILE)
            val json = JsonObject()
            
            for ((key, value) in trackingMetadata) {
                json.add(key, GSON.toJsonTree(value))
            }
            
            json.addProperty("_version", "1.0")
            
            Files.writeString(filePath, GSON.toJson(json))
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to save tracking metadata: ${e.message}", e)
        }
    }
    
    // ==================== EXPORT/IMPORT OPERATIONS ====================
    
    /**
     * Export all tracking data to a Base64-encoded string.
     * 
     * @param compressed Whether to compress the data (default: true)
     * @return Base64-encoded string containing all tracking data, or null if export fails
     */
    fun exportData(compressed: Boolean = true): String? {
        return try {
            val exportData = JsonObject().apply {
                addProperty("version", "1.0")
                addProperty("timestamp", System.currentTimeMillis())
                add("data", JsonObject().apply {
                    add("pityCounters", GSON.toJsonTree(pityCounters))
                    add("lifetimeStats", GSON.toJsonTree(lifetimeStats))
                    add("personalBestRecords", GSON.toJsonTree(personalBestRecords))
                    add("trackingMetadata", GSON.toJsonTree(trackingMetadata))
                })
            }
            
            val jsonString = GSON.toJson(exportData)
            
            if (compressed) {
                CompressionUtils.compressAndEncode(jsonString)
            } else {
                jsonString
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Export failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Import tracking data from a Base64-encoded string.
     * 
     * @param data The Base64-encoded string or raw JSON
     * @param merge Whether to merge with existing data (true) or replace it (false)
     * @return true if import was successful, false otherwise
     */
    fun importData(data: String, merge: Boolean = false): Boolean {
        return try {
            // Detect if compressed (Base64) or raw JSON
            val jsonString = if (data.startsWith("{")) {
                data
            } else {
                CompressionUtils.decodeAndDecompress(data)
            }
            
            val importData = JsonParser.parseString(jsonString).asJsonObject
            
            // Validate version
            val version = importData.get("version")?.asString
            if (version == null) {
                Melinoe.logger.error("Import data missing version field")
                return false
            }
            
            if (version > "1.0") {
                Melinoe.logger.error("Unsupported import version: $version")
                return false
            }
            
            val dataObj = importData.getAsJsonObject("data")
            
            // Clear existing data if not merging
            if (!merge) {
                pityCounters.clear()
                lifetimeStats.clear()
                personalBests.clear()
                personalBestRecords.clear()
                trackingMetadata.clear()
            }
            
            // Import pity counters
            dataObj.getAsJsonObject("pityCounters")?.let { obj ->
                for ((key, value) in obj.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonPrimitive) {
                        pityCounters[key] = value.asInt
                    }
                }
            }
            
            // Import lifetime stats
            dataObj.getAsJsonObject("lifetimeStats")?.let { obj ->
                for ((key, value) in obj.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonPrimitive) {
                        lifetimeStats[key] = value.asInt
                    }
                }
            }
            
            // Import personal bests (support both old and new format)
            dataObj.getAsJsonObject("personalBests")?.let { obj ->
                for ((key, value) in obj.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonPrimitive) {
                        personalBests[key] = value.asFloat
                        personalBestRecords[key] = PersonalBestRecord.fromTime(value.asFloat)
                    }
                }
            }
            
            // Import personal best records (new format)
            dataObj.getAsJsonObject("personalBestRecords")?.let { obj ->
                for ((key, value) in obj.entrySet()) {
                    if (!key.startsWith("_") && value.isJsonObject) {
                        personalBestRecords[key] = parsePersonalBestRecord(value.asJsonObject)
                    }
                }
            }
            
            // Import tracking metadata
            dataObj.getAsJsonObject("trackingMetadata")?.let { obj ->
                for ((key, value) in obj.entrySet()) {
                    if (!key.startsWith("_")) {
                        trackingMetadata[key] = value
                    }
                }
            }
            
            // Save imported data
            runBlocking {
                saveAllData()
            }
            
            Melinoe.logger.info("Successfully imported tracking data (merge=$merge)")
            true
        } catch (e: Exception) {
            Melinoe.logger.error("Import failed: ${e.message}", e)
            false
        }
    }
    
    // ==================== BACKUP OPERATIONS ====================
    
    /**
     * Create a backup of all data files.
     */
    fun createBackup(): Boolean {
        return try {
            val dataDir = File(DATA_DIR)
            backupManager.createBackup(dataDir)
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to create backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Restore from a backup by index (0 = most recent).
     */
    fun restoreFromBackup(index: Int): Boolean {
        return try {
            val dataDir = File(DATA_DIR)
            val success = backupManager.restoreFromBackup(index, dataDir)
            if (success) {
                // Reload data after restoration
                runBlocking {
                    loadAllData()
                }
            }
            success
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to restore backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * List all available backups.
     */
    fun listBackups(): List<BackupManager.BackupInfo> {
        return backupManager.listBackups()
    }
    
    // ==================== UTILITY OPERATIONS ====================
    
    /**
     * Get all pity counters as a map.
     */
    fun getAllPityCounters(): Map<String, Int> {
        return HashMap(pityCounters)
    }
    
    /**
     * Get all lifetime stats as a map.
     */
    fun getAllLifetimeStats(): Map<String, Int> {
        return HashMap(lifetimeStats)
    }
    
    /**
     * Get all personal bests as a map (simple time values).
     */
    fun getAllPersonalBests(): Map<String, Float> {
        return personalBestRecords.mapValues { it.value.time }
    }
    
    /**
     * Get all personal best records with metadata.
     */
    fun getAllPersonalBestRecords(): Map<String, PersonalBestRecord> {
        return HashMap(personalBestRecords)
    }
    
    /**
     * Clear all tracking data (use with caution!).
     * Creates a backup before clearing.
     */
    fun clearAllData() {
        pityCounters.clear()
        lifetimeStats.clear()
        personalBests.clear()
        personalBestRecords.clear()
        trackingMetadata.clear()
        
        runBlocking {
            saveAllData()
        }
        
        Melinoe.logger.warn("Cleared all tracking data")
    }
    
    // ==================== SHUTDOWN ====================
    
    /**
     * Shutdown the data config system.
     * Waits for all pending saves to complete.
     */
    fun shutdown() {
        if (!initialized) {
            return
        }
        
        Melinoe.logger.info("Shutting down DataConfig...")
        
        try {
            // Wait for all pending saves
            runBlocking {
                asyncPersistence.awaitAllSaves(timeoutMs = 10000L)
            }
            
            // Create final backup
            createBackup()
            
            initialized = false
            Melinoe.logger.info("DataConfig shutdown complete")
        } catch (e: Exception) {
            Melinoe.logger.error("Error during DataConfig shutdown: ${e.message}", e)
        }
    }
}
