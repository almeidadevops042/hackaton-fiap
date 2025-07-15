package com.fiapx.notification.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

enum class NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    PROCESSING
}

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val userId: String? = null,
    val data: Map<String, Any>? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var read: Boolean = false,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var readAt: LocalDateTime? = null
) {
    fun toJson(): String {
        return com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .writeValueAsString(this)
    }

    companion object {
        fun fromJson(json: String): Notification? {
            return try {
                com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .readValue(json, Notification::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class NotificationResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: String? = null
)

data class CreateNotificationRequest(
    val type: NotificationType,
    val title: String,
    val message: String,
    val userId: String? = null,
    val data: Map<String, Any>? = null
)

data class MarkReadRequest(
    val notificationIds: List<String>
)

data class NotificationStats(
    val totalNotifications: Long,
    val unreadNotifications: Long,
    val readNotifications: Long,
    val expiredNotifications: Long
) 