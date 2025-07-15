package com.fiapx.storage.controller

import com.fiapx.storage.model.StorageResponse
import com.fiapx.storage.service.StorageService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File

@RestController
@RequestMapping("/files")
@CrossOrigin(origins = ["*"])
class StorageController(
    private val storageService: StorageService
) {

    @GetMapping
    fun listFiles(@RequestParam(required = false) type: String?): ResponseEntity<StorageResponse> {
        val files = storageService.listFiles(type)
        return ResponseEntity.ok(
            StorageResponse(
                success = true,
                message = "Files listed successfully",
                data = mapOf(
                    "files" to files,
                    "total" to files.size
                )
            )
        )
    }

    @GetMapping("/download/{filename}")
    fun downloadFile(@PathVariable filename: String): ResponseEntity<FileSystemResource> {
        val file = storageService.getFile(filename)
        return if (file != null) {
            val headers = HttpHeaders().apply {
                add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                add(HttpHeaders.CONTENT_TYPE, getContentType(filename))
            }
            
            ResponseEntity.ok()
                .headers(headers)
                .body(FileSystemResource(file))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{filename}")
    fun deleteFile(@PathVariable filename: String): ResponseEntity<StorageResponse> {
        val deleted = storageService.deleteFile(filename)
        return if (deleted) {
            ResponseEntity.ok(
                StorageResponse(
                    success = true,
                    message = "File deleted successfully"
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/stats")
    fun getStorageStats(): ResponseEntity<StorageResponse> {
        val stats = storageService.getStorageStats()
        return ResponseEntity.ok(
            StorageResponse(
                success = true,
                message = "Storage statistics retrieved",
                data = stats
            )
        )
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Storage Service is healthy",
                "data" to mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "version" to "1.0.0",
                    "service" to "storage"
                )
            )
        )
    }

    private fun getContentType(filename: String): String {
        return when {
            filename.endsWith(".mp4") -> "video/mp4"
            filename.endsWith(".avi") -> "video/x-msvideo"
            filename.endsWith(".mov") -> "video/quicktime"
            filename.endsWith(".mkv") -> "video/x-matroska"
            filename.endsWith(".zip") -> "application/zip"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
} 