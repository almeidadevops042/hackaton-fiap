package com.fiapx.upload.service

import com.fiapx.upload.model.FileMetadata
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*
import org.slf4j.LoggerFactory

@Service
class UploadService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.upload.max-file-size:524288000}") private val maxFileSize: Long = 500 * 1024 * 1024, // 500MB
    @Value("\${app.upload.allowed-extensions:mp4,avi,mov,mkv,wmv,flv,webm}") private val allowedExtensions: String = "mp4,avi,mov,mkv,wmv,flv,webm"
) {
    private val logger = LoggerFactory.getLogger(UploadService::class.java)
    private val uploadDir = File("uploads")
    private val allowedExtList = allowedExtensions.split(",").map { ".$it" }

    init {
        uploadDir.mkdirs()
    }

    fun uploadFile(file: MultipartFile, userId: String?, username: String?): FileMetadata {
        // Validate file
        validateFile(file)
        
        // Generate unique file ID
        val fileId = generateFileId()
        val timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val originalFilename = file.originalFilename ?: "unknown"
        val filename = "${fileId}_${timestamp}_$originalFilename"
        val filePath = File(uploadDir, filename)
        
        // Calculate file hash
        val hash = calculateFileHash(file)
        
        // Save file
        file.transferTo(filePath)
        
        // Create metadata
        val metadata = FileMetadata(
            id = fileId,
            filename = originalFilename,
            size = file.size,
            hash = hash,
            mimeType = file.contentType ?: "application/octet-stream",
            uploadedAt = LocalDateTime.now(),
            status = "uploaded",
            userId = userId,
            username = username
        )
        
        // Store metadata in Redis
        redisTemplate.opsForValue().set(
            "upload:$fileId", 
            metadata.toJson(), 
            java.time.Duration.ofHours(24)
        )
        
        logger.info("File uploaded successfully: $fileId, size: ${file.size}, hash: $hash")
        
        return metadata
    }

    fun getUploadStatus(fileId: String): FileMetadata? {
        val metadataJson = redisTemplate.opsForValue().get("upload:$fileId")
        return metadataJson?.let { FileMetadata.fromJson(it) }
    }

    fun listUploads(): List<FileMetadata> {
        val uploads = mutableListOf<FileMetadata>()
        
        val keys = redisTemplate.keys("upload:*")
        keys?.forEach { key ->
            val metadataJson = redisTemplate.opsForValue().get(key)
            metadataJson?.let { 
                FileMetadata.fromJson(it)?.let { metadata -> uploads.add(metadata) }
            }
        }
        
        return uploads.sortedByDescending { it.uploadedAt }
    }

    fun listUploadsByUser(userId: String): List<FileMetadata> {
        return listUploads().filter { it.userId == userId }
    }

    fun deleteUpload(fileId: String): Boolean {
        // Get metadata first
        val metadata = getUploadStatus(fileId) ?: return false
        
        // Delete from Redis
        redisTemplate.delete("upload:$fileId")
        
        // Delete physical file
        val files = uploadDir.listFiles { file -> 
            file.name.startsWith(fileId) && file.isFile 
        }
        
        files?.forEach { it.delete() }
        
        logger.info("Upload deleted: $fileId")
        return true
    }

    private fun validateFile(file: MultipartFile) {
        // Check file size
        if (file.size > maxFileSize) {
            throw IllegalArgumentException("File too large. Max size: ${maxFileSize / (1024 * 1024)} MB")
        }
        
        // Check file extension
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("Invalid filename")
        val extension = getFileExtension(originalFilename)
        
        if (!allowedExtList.contains(extension.lowercase())) {
            throw IllegalArgumentException("Invalid file format. Allowed: ${allowedExtList.joinToString(", ")}")
        }
        
        // Check if file is empty
        if (file.isEmpty) {
            throw IllegalArgumentException("File is empty")
        }
    }

    private fun getFileExtension(filename: String): String {
        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            filename.substring(lastDotIndex)
        } else {
            ""
        }
    }

    private fun calculateFileHash(file: MultipartFile): String {
        val digest = MessageDigest.getInstance("MD5")
        
        file.inputStream.use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun generateFileId(): String {
        return "file_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }
} 