package com.fiapx.upload.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class FileMetadata(
    val id: String,
    val filename: String,
    val size: Long,
    val hash: String,
    val mimeType: String,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val uploadedAt: LocalDateTime,
    val status: String,
    val userId: String? = null,
    val username: String? = null
) {
    fun toJson(): String {
        return com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .writeValueAsString(this)
    }

    companion object {
        fun fromJson(json: String): FileMetadata? {
            return try {
                com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .readValue(json, FileMetadata::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class UploadResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: String? = null
)

data class UploadStatusResponse(
    val success: Boolean,
    val message: String,
    val data: FileMetadata? = null,
    val error: String? = null
) 