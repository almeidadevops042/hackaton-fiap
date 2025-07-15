package com.fiapx.storage.service

import com.fiapx.storage.model.FileInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@Service
class StorageService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.storage.uploads-dir:uploads}") private val uploadsDir: String,
    @Value("\${app.storage.outputs-dir:outputs}") private val outputsDir: String,
    @Value("\${app.storage.cleanup.enabled:true}") private val cleanupEnabled: Boolean = true,
    @Value("\${app.storage.cleanup.max-age-hours:24}") private val maxAgeHours: Long = 24
) {
    private val logger = LoggerFactory.getLogger(StorageService::class.java)
    private val uploadsPath = Paths.get(uploadsDir)
    private val outputsPath = Paths.get(outputsDir)
    private val downloadCounter = AtomicLong(0)

    init {
        Files.createDirectories(uploadsPath)
        Files.createDirectories(outputsPath)
    }

    fun listFiles(type: String? = null): List<FileInfo> {
        val files = mutableListOf<FileInfo>()
        
        when (type?.lowercase()) {
            "upload" -> files.addAll(listUploadFiles())
            "output" -> files.addAll(listOutputFiles())
            else -> {
                files.addAll(listUploadFiles())
                files.addAll(listOutputFiles())
            }
        }
        
        return files.sortedByDescending { it.createdAt }
    }

    fun getFile(filename: String): File? {
        // Check uploads first
        val uploadFile = uploadsPath.resolve(filename).toFile()
        if (uploadFile.exists()) {
            incrementDownloadCount(filename)
            return uploadFile
        }
        
        // Check outputs
        val outputFile = outputsPath.resolve(filename).toFile()
        if (outputFile.exists()) {
            incrementDownloadCount(filename)
            return outputFile
        }
        
        return null
    }

    fun deleteFile(filename: String): Boolean {
        // Try to delete from uploads
        val uploadFile = uploadsPath.resolve(filename).toFile()
        if (uploadFile.exists() && uploadFile.delete()) {
            logger.info("Deleted upload file: $filename")
            return true
        }
        
        // Try to delete from outputs
        val outputFile = outputsPath.resolve(filename).toFile()
        if (outputFile.exists() && outputFile.delete()) {
            logger.info("Deleted output file: $filename")
            return true
        }
        
        return false
    }

    fun getStorageStats(): Map<String, Any> {
        val uploadFiles = listUploadFiles()
        val outputFiles = listOutputFiles()
        
        val totalUploadSize = uploadFiles.sumOf { it.size }
        val totalOutputSize = outputFiles.sumOf { it.size }
        val totalSize = totalUploadSize + totalOutputSize
        
        val totalDownloads = getTotalDownloadCount()
        
        return mapOf(
            "total_files" to (uploadFiles.size + outputFiles.size),
            "upload_files" to uploadFiles.size,
            "output_files" to outputFiles.size,
            "total_size_bytes" to totalSize,
            "total_size_mb" to (totalSize / (1024 * 1024)),
            "upload_size_bytes" to totalUploadSize,
            "output_size_bytes" to totalOutputSize,
            "total_downloads" to totalDownloads,
            "last_cleanup" to (getLastCleanupTime()?.toString() ?: "null"),
            "cleanup_enabled" to cleanupEnabled
        )
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    fun cleanupOldFiles() {
        if (!cleanupEnabled) {
            logger.info("File cleanup is disabled")
            return
        }
        
        logger.info("Starting file cleanup...")
        val cutoffTime = LocalDateTime.now().minusHours(maxAgeHours)
        var deletedCount = 0
        
        // Clean up upload files
        val uploadFiles = uploadsPath.toFile().listFiles()
        uploadFiles?.forEach { file ->
            if (isFileOlderThan(file, cutoffTime)) {
                if (file.delete()) {
                    deletedCount++
                    logger.debug("Deleted old upload file: ${file.name}")
                }
            }
        }
        
        // Clean up output files
        val outputFiles = outputsPath.toFile().listFiles()
        outputFiles?.forEach { file ->
            if (isFileOlderThan(file, cutoffTime)) {
                if (file.delete()) {
                    deletedCount++
                    logger.debug("Deleted old output file: ${file.name}")
                }
            }
        }
        
        // Update last cleanup time
        setLastCleanupTime(LocalDateTime.now())
        
        logger.info("File cleanup completed. Deleted $deletedCount files")
    }

    private fun listUploadFiles(): List<FileInfo> {
        return uploadsPath.toFile().listFiles()
            ?.filter { it.isFile }
            ?.map { file ->
                FileInfo(
                    name = file.name,
                    size = file.length(),
                    type = "upload",
                    createdAt = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(file.toPath()).toInstant(),
                        java.time.ZoneId.systemDefault()
                    ),
                    downloadCount = getDownloadCount(file.name)
                )
            } ?: emptyList()
    }

    private fun listOutputFiles(): List<FileInfo> {
        return outputsPath.toFile().listFiles()
            ?.filter { it.isFile }
            ?.map { file ->
                FileInfo(
                    name = file.name,
                    size = file.length(),
                    type = "output",
                    createdAt = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(file.toPath()).toInstant(),
                        java.time.ZoneId.systemDefault()
                    ),
                    downloadCount = getDownloadCount(file.name)
                )
            } ?: emptyList()
    }

    private fun incrementDownloadCount(filename: String) {
        val key = "downloads:$filename"
        redisTemplate.opsForValue().increment(key)
        
        // Also increment total downloads
        redisTemplate.opsForValue().increment("downloads:total")
    }

    private fun getDownloadCount(filename: String): Long {
        val key = "downloads:$filename"
        return redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0
    }

    private fun getTotalDownloadCount(): Long {
        return redisTemplate.opsForValue().get("downloads:total")?.toLongOrNull() ?: 0
    }

    private fun isFileOlderThan(file: File, cutoffTime: LocalDateTime): Boolean {
        val fileTime = LocalDateTime.ofInstant(
            Files.getLastModifiedTime(file.toPath()).toInstant(),
            java.time.ZoneId.systemDefault()
        )
        return fileTime.isBefore(cutoffTime)
    }

    private fun getLastCleanupTime(): LocalDateTime? {
        val timeStr = redisTemplate.opsForValue().get("storage:last-cleanup")
        return timeStr?.let { 
            LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
    }

    private fun setLastCleanupTime(time: LocalDateTime) {
        redisTemplate.opsForValue().set(
            "storage:last-cleanup",
            time.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
} 