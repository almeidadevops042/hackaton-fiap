package com.fiapx.storage.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class FileInfo(
    val name: String,
    val size: Long,
    val type: String, // "upload" or "output"
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime,
    val downloadCount: Long = 0
) {
    val sizeInMB: Double
        get() = size / (1024.0 * 1024.0)
    
    val sizeInKB: Double
        get() = size / 1024.0
}

data class StorageResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: String? = null
)

data class StorageStats(
    val totalFiles: Int,
    val totalSize: Long,
    val uploadFiles: Int,
    val uploadSize: Long,
    val outputFiles: Int,
    val outputSize: Long,
    val diskUsage: DiskUsage
)

data class DiskUsage(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val usagePercentage: Double
) 