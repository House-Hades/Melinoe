package me.melinoe.utils.data.persistence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.melinoe.Melinoe

/**
 * Unified configuration manager that orchestrates both module config and data config.
 * Provides a single entry point for all configuration operations.
 */
object UnifiedConfigManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var initialized = false
    
    /**
     * Initialize all configuration systems.
     * Initializes in the correct order: DataConfig first, then other systems.
     */
    fun initialize() {
        if (initialized) {
            Melinoe.logger.warn("UnifiedConfigManager already initialized")
            return
        }
        
        try {
            Melinoe.logger.info("Initializing UnifiedConfigManager...")
            
            // Initialize DataConfig
            DataConfig.initialize()
            
            // Note: ModuleConfig is initialized separately by the module system
            
            initialized = true
            Melinoe.logger.info("UnifiedConfigManager initialized successfully")
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to initialize UnifiedConfigManager: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Shutdown all configuration systems gracefully.
     * Ensures all pending saves complete and creates final backups.
     */
    fun shutdown() {
        if (!initialized) {
            return
        }
        
        Melinoe.logger.info("Shutting down UnifiedConfigManager...")
        
        try {
            // Shutdown DataConfig (waits for pending saves)
            DataConfig.shutdown()
            
            // Note: PersistentTrackingManager shutdown is handled separately during migration
            
            initialized = false
            Melinoe.logger.info("UnifiedConfigManager shutdown complete")
        } catch (e: Exception) {
            Melinoe.logger.error("Error during UnifiedConfigManager shutdown: ${e.message}", e)
        }
    }
    
    // ==================== DATA ACCESS API ====================
    
    /**
     * Get pity counter value.
     */
    fun getPityCounter(bossName: String): Int {
        return DataConfig.getPityCounter(bossName)
    }
    
    /**
     * Set pity counter value.
     */
    fun setPityCounter(bossName: String, value: Int) {
        DataConfig.setPityCounter(bossName, value)
    }
    
    /**
     * Get lifetime stat value.
     */
    fun getLifetimeStat(statName: String): Int {
        return DataConfig.getLifetimeStat(statName)
    }
    
    /**
     * Set lifetime stat value.
     */
    fun setLifetimeStat(statName: String, value: Int) {
        DataConfig.setLifetimeStat(statName, value)
    }
    
    /**
     * Get personal best time.
     */
    fun getPersonalBest(dungeonName: String): Float {
        return DataConfig.getPersonalBest(dungeonName)
    }
    
    /**
     * Set personal best time.
     */
    fun setPersonalBest(dungeonName: String, timeInSeconds: Float) {
        DataConfig.setPersonalBest(dungeonName, timeInSeconds)
    }
    
    /**
     * Get tracking metadata.
     */
    fun getTrackingMetadata(key: String): Any? {
        return DataConfig.getTrackingMetadata(key)
    }
    
    /**
     * Set tracking metadata.
     */
    fun setTrackingMetadata(key: String, value: Any) {
        DataConfig.setTrackingMetadata(key, value)
    }
    
    // ==================== BACKUP OPERATIONS ====================
    
    /**
     * Create a backup of all configuration data.
     */
    fun createBackup(): Boolean {
        return DataConfig.createBackup()
    }
    
    /**
     * Restore from a backup by index (0 = most recent).
     */
    fun restoreFromBackup(index: Int): Boolean {
        return DataConfig.restoreFromBackup(index)
    }
    
    /**
     * List all available backups.
     */
    fun listBackups(): List<BackupManager.BackupInfo> {
        return DataConfig.listBackups()
    }
    
    // ==================== SAVE/LOAD OPERATIONS ====================
    
    /**
     * Save all data immediately.
     */
    fun saveData() {
        runBlocking {
            DataConfig.saveAllData()
        }
    }
    
    /**
     * Load all data from files.
     */
    fun loadData() {
        runBlocking {
            DataConfig.loadAllData()
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
        return DataConfig.exportData(compressed)
    }
    
    /**
     * Import tracking data from a Base64-encoded string.
     * 
     * @param data The Base64-encoded string or raw JSON
     * @param merge Whether to merge with existing data (true) or replace it (false)
     * @return true if import was successful, false otherwise
     */
    fun importData(data: String, merge: Boolean = false): Boolean {
        return DataConfig.importData(data, merge)
    }
}
