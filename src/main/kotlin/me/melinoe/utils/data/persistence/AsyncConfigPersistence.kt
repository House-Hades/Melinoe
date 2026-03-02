package me.melinoe.utils.data.persistence

import kotlinx.coroutines.*
import me.melinoe.Melinoe
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages asynchronous configuration persistence with debouncing.
 * Provides coroutine-based async save operations to prevent blocking the main thread.
 */
class AsyncConfigPersistence(
    private val scope: CoroutineScope,
    private val debounceDelayMs: Long = 5000L
) {
    // Track active save jobs for each data type
    private val saveJobs = ConcurrentHashMap<String, Job>()
    
    /**
     * Schedules a save operation with debouncing.
     * If a save is already scheduled for the same key, it will be cancelled and rescheduled.
     * 
     * @param key Unique identifier for the save operation (e.g., "pityCounters")
     * @param saveAction Suspend function that performs the actual save
     */
    fun scheduleSave(key: String, saveAction: suspend () -> Unit) {
        // Cancel any existing save job for this key
        saveJobs[key]?.cancel()
        
        // Schedule a new save job with debounce delay
        saveJobs[key] = scope.launch {
            try {
                delay(debounceDelayMs)
                saveAction()
                Melinoe.logger.debug("Async save completed for: $key")
            } catch (e: CancellationException) {
                // Job was cancelled (debounced), this is expected
                Melinoe.logger.debug("Save cancelled (debounced) for: $key")
            } catch (e: Exception) {
                Melinoe.logger.error("Failed to save $key: ${e.message}", e)
            } finally {
                saveJobs.remove(key)
            }
        }
    }
    
    /**
     * Forces an immediate save without debouncing.
     * Cancels any pending debounced save and executes immediately.
     * 
     * @param key Unique identifier for the save operation
     * @param saveAction Suspend function that performs the actual save
     */
    suspend fun forceSave(key: String, saveAction: suspend () -> Unit) {
        // Cancel any pending save
        saveJobs[key]?.cancel()
        saveJobs.remove(key)
        
        try {
            saveAction()
            Melinoe.logger.debug("Force save completed for: $key")
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to force save $key: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Waits for all pending save operations to complete.
     * Useful during shutdown to ensure all data is persisted.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds (default: 10 seconds)
     * @return true if all saves completed, false if timeout occurred
     */
    suspend fun awaitAllSaves(timeoutMs: Long = 10000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                // Get snapshot of current jobs
                val jobs = saveJobs.values.toList()
                
                if (jobs.isNotEmpty()) {
                    Melinoe.logger.info("Waiting for ${jobs.size} pending save(s) to complete...")
                    jobs.forEach { it.join() }
                    Melinoe.logger.info("All pending saves completed")
                }
                true
            }
        } catch (e: TimeoutCancellationException) {
            Melinoe.logger.warn("Timeout waiting for saves to complete after ${timeoutMs}ms")
            false
        }
    }
    
    /**
     * Cancels all pending save operations.
     * Use with caution - may result in data loss.
     */
    fun cancelAllSaves() {
        val count = saveJobs.size
        saveJobs.values.forEach { it.cancel() }
        saveJobs.clear()
        Melinoe.logger.warn("Cancelled $count pending save operation(s)")
    }
    
    /**
     * Gets the number of currently pending save operations.
     */
    fun getPendingSaveCount(): Int = saveJobs.size
    
    /**
     * Checks if a save is currently pending for the given key.
     */
    fun hasPendingSave(key: String): Boolean = saveJobs.containsKey(key)
}
