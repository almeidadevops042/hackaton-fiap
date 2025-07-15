package com.fiapx.upload.controller

import com.fiapx.upload.model.UploadResponse
import com.fiapx.upload.model.UploadStatusResponse
import com.fiapx.upload.service.UploadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@RestController
@RequestMapping("/upload")
@CrossOrigin(origins = ["*"])
class UploadController(
    private val uploadService: UploadService
) {

    @PostMapping
    fun uploadFile(
        @RequestParam("video") file: MultipartFile,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<UploadResponse> {
        return try {
            val userId = request.getAttribute("userId") as? String
            val username = request.getAttribute("username") as? String
            
            val metadata = uploadService.uploadFile(file, userId, username)
            ResponseEntity.ok(
                UploadResponse(
                    success = true,
                    message = "File uploaded successfully",
                    data = metadata
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                UploadResponse(
                    success = false,
                    message = "Upload failed",
                    error = e.message
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                UploadResponse(
                    success = false,
                    message = "Upload failed",
                    error = e.message
                )
            )
        }
    }

    @GetMapping("/{id}/status")
    fun getUploadStatus(@PathVariable id: String): ResponseEntity<UploadStatusResponse> {
        val metadata = uploadService.getUploadStatus(id)
        return if (metadata != null) {
            ResponseEntity.ok(
                UploadStatusResponse(
                    success = true,
                    message = "Upload status retrieved",
                    data = metadata
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    fun listUploads(request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<UploadResponse> {
        val userId = request.getAttribute("userId") as? String
        
        val uploads = if (userId != null) {
            uploadService.listUploadsByUser(userId)
        } else {
            uploadService.listUploads()
        }
        
        return ResponseEntity.ok(
            UploadResponse(
                success = true,
                message = "Uploads listed successfully",
                data = mapOf(
                    "uploads" to uploads,
                    "total" to uploads.size
                )
            )
        )
    }

    @DeleteMapping("/{id}")
    fun deleteUpload(@PathVariable id: String): ResponseEntity<UploadResponse> {
        val deleted = uploadService.deleteUpload(id)
        return if (deleted) {
            ResponseEntity.ok(
                UploadResponse(
                    success = true,
                    message = "Upload deleted successfully"
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Upload Service is healthy",
                "data" to mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "version" to "1.0.0",
                    "service" to "upload"
                )
            )
        )
    }
} 