package me.melinoe.utils.data.persistence

/**
 * Configuration options for the data persistence system.
 */
object ConfigOptions {
    /**
     * Enable/disable automatic backups on save.
     */
    var autoBackupEnabled: Boolean = true
    
    /**
     * Number of backups to keep (older backups are automatically deleted).
     */
    var maxBackups: Int = 10
    
    /**
     * Debounce delay in milliseconds for async saves.
     */
    var debounceDelayMs: Long = 5000L
    
    /**
     * Enable/disable compression for data files.
     */
    var compressionEnabled: Boolean = false
    
    /**
     * Enable/disable verbose logging for persistence operations.
     */
    var verboseLogging: Boolean = false
    
    /**
     * Interval in milliseconds for periodic saves (0 = disabled).
     */
    var periodicSaveIntervalMs: Long = 0L
    
    /**
     * Enable/disable data validation on load.
     */
    var validateOnLoad: Boolean = true
    
    /**
     * Maximum time to wait for saves during shutdown (milliseconds).
     */
    var shutdownTimeoutMs: Long = 10000L
}
