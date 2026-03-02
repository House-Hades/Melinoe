package me.melinoe.utils.data.persistence

import me.melinoe.Melinoe
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages backup creation, rotation, and restoration for configuration data.
 */
object BackupManager {
    private const val BACKUP_DIR = "config/melinoe/backups"
    private const val MAX_BACKUPS = 10
    private const val BACKUP_PREFIX = "melinoe_backup_"
    private const val BACKUP_EXTENSION = ".zip"
    
    /**
     * Information about a backup file.
     */
    data class BackupInfo(
        val file: File,
        val timestamp: Long,
        val formattedDate: String,
        val sizeBytes: Long,
        val formattedSize: String
    )
    
    init {
        ensureBackupDirectoryExists()
    }
    
    /**
     * Ensures the backup directory exists.
     */
    private fun ensureBackupDirectoryExists() {
        try {
            val backupPath = Paths.get(BACKUP_DIR)
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath)
                Melinoe.logger.info("Created backup directory: $BACKUP_DIR")
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to create backup directory: ${e.message}", e)
        }
    }
    
    /**
     * Creates a backup of the specified directory.
     * 
     * @param sourceDir The directory to backup
     * @return true if backup was successful, false otherwise
     */
    fun createBackup(sourceDir: File): Boolean {
        return try {
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                Melinoe.logger.error("Source directory does not exist or is not a directory: ${sourceDir.path}")
                return false
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val backupFile = File(BACKUP_DIR, "$BACKUP_PREFIX$timestamp$BACKUP_EXTENSION")
            
            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                sourceDir.walk()
                    .filter { it.isFile && it.extension == "json" }
                    .forEach { file ->
                        val entry = ZipEntry(file.relativeTo(sourceDir).path)
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
            }
            
            Melinoe.logger.info("Created backup: ${backupFile.name} (${formatSize(backupFile.length())})")
            
            // Rotate old backups
            rotateBackups()
            
            true
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to create backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Rotates backups, keeping only the most recent MAX_BACKUPS files.
     */
    fun rotateBackups() {
        try {
            val backups = listBackups()
            
            if (backups.size > MAX_BACKUPS) {
                val toDelete = backups.sortedBy { it.timestamp }.take(backups.size - MAX_BACKUPS)
                toDelete.forEach { backup ->
                    if (backup.file.delete()) {
                        Melinoe.logger.info("Deleted old backup: ${backup.file.name}")
                    } else {
                        Melinoe.logger.warn("Failed to delete old backup: ${backup.file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to rotate backups: ${e.message}", e)
        }
    }
    
    /**
     * Restores data from a backup file.
     * 
     * @param backupFile The backup file to restore from
     * @param targetDir The directory to restore to
     * @return true if restoration was successful, false otherwise
     */
    fun restoreFromBackup(backupFile: File, targetDir: File): Boolean {
        return try {
            if (!backupFile.exists() || !backupFile.isFile) {
                Melinoe.logger.error("Backup file does not exist: ${backupFile.path}")
                return false
            }
            
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            ZipInputStream(FileInputStream(backupFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    file.parentFile?.mkdirs()
                    
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                    
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            
            Melinoe.logger.info("Restored backup from: ${backupFile.name}")
            true
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to restore backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Restores from a backup by index (0 = most recent).
     * 
     * @param index The backup index (0 = most recent)
     * @param targetDir The directory to restore to
     * @return true if restoration was successful, false otherwise
     */
    fun restoreFromBackup(index: Int, targetDir: File): Boolean {
        val backups = listBackups().sortedByDescending { it.timestamp }
        
        if (index < 0 || index >= backups.size) {
            Melinoe.logger.error("Invalid backup index: $index (available: 0-${backups.size - 1})")
            return false
        }
        
        return restoreFromBackup(backups[index].file, targetDir)
    }
    
    /**
     * Lists all available backups.
     * 
     * @return List of BackupInfo objects, sorted by timestamp (newest first)
     */
    fun listBackups(): List<BackupInfo> {
        return try {
            val backupDir = File(BACKUP_DIR)
            if (!backupDir.exists()) {
                return emptyList()
            }
            
            backupDir.listFiles { _, name ->
                name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_EXTENSION)
            }?.map { file ->
                val timestamp = extractTimestamp(file.name)
                BackupInfo(
                    file = file,
                    timestamp = timestamp,
                    formattedDate = formatTimestamp(timestamp),
                    sizeBytes = file.length(),
                    formattedSize = formatSize(file.length())
                )
            }?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to list backups: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Extracts timestamp from backup filename.
     */
    private fun extractTimestamp(filename: String): Long {
        return try {
            val timestampStr = filename
                .removePrefix(BACKUP_PREFIX)
                .removeSuffix(BACKUP_EXTENSION)
            SimpleDateFormat("yyyyMMdd_HHmmss").parse(timestampStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Formats a timestamp to a readable date string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        return if (timestamp > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp))
        } else {
            "Unknown"
        }
    }
    
    /**
     * Formats a file size to a human-readable string.
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Deletes a specific backup file.
     * 
     * @param backupFile The backup file to delete
     * @return true if deletion was successful, false otherwise
     */
    fun deleteBackup(backupFile: File): Boolean {
        return try {
            if (backupFile.delete()) {
                Melinoe.logger.info("Deleted backup: ${backupFile.name}")
                true
            } else {
                Melinoe.logger.warn("Failed to delete backup: ${backupFile.name}")
                false
            }
        } catch (e: Exception) {
            Melinoe.logger.error("Failed to delete backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Deletes all backups.
     * 
     * @return Number of backups deleted
     */
    fun deleteAllBackups(): Int {
        var deleted = 0
        listBackups().forEach { backup ->
            if (deleteBackup(backup.file)) {
                deleted++
            }
        }
        return deleted
    }
}
