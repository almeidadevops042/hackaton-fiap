package com.fiapx.processing.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ProcessingJob(
    val id: String,
    val fileId: String,
    var status: ProcessingStatus,
    var progress: Int = 0,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var startedAt: LocalDateTime? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var completedAt: LocalDateTime? = null,
    var outputFile: String? = null,
    var frameCount: Int? = null,
    var error: String? = null
) {
    fun toJson(): String {
        return com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .writeValueAsString(this)
    }

    companion object {
        fun fromJson(json: String): ProcessingJob? {
            return try {
                com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .readValue(json, ProcessingJob::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class ProcessRequest(
    @JsonProperty("file_id")
    val fileId: String
)

data class ProcessingResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: String? = null
) 