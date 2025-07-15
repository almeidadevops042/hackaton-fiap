package com.fiapx.notification.service

import com.fiapx.notification.model.Notification
import com.fiapx.notification.model.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

@Service
class NotificationService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.notification.retention-hours:168}") private val retentionHours: Long = 168 // 7 days
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    private val activeNotifications = ConcurrentHashMap<String, Notification>()

    fun createNotification(
        type: NotificationType,
        title: String,
        message: String,
        userId: String? = null,
        data: Map<String, Any>? = null
    ): Notification {
        val notification = Notification(
            id = generateNotificationId(),
            type = type,
            title = title,
            message = message,
            userId = userId,
            data = data,
            createdAt = LocalDateTime.now(),
            read = false
        )

        // Store in Redis
        redisTemplate.opsForValue().set(
            "notification:${notification.id}",
            notification.toJson(),
            java.time.Duration.ofHours(retentionHours)
        )

        // Add to user's notification list if userId is provided
        userId?.let { uid ->
            redisTemplate.opsForList().leftPush("notifications:user:$uid", notification.id)
        }

        // Add to global notifications
        redisTemplate.opsForList().leftPush("notifications:global", notification.id)

        // Store in memory for active tracking
        activeNotifications[notification.id] = notification

        logger.info("Created notification: ${notification.id} for user: $userId")
        return notification
    }

    fun getNotification(id: String): Notification? {
        // First check active notifications
        activeNotifications[id]?.let { return it }

        // Then check Redis
        val notificationJson = redisTemplate.opsForValue().get("notification:$id")
        return notificationJson?.let { Notification.fromJson(it) }
    }

    fun listNotifications(userId: String? = null): List<Notification> {
        val notifications = mutableListOf<Notification>()

        if (userId != null) {
            // Get user-specific notifications
            val notificationIds = redisTemplate.opsForList().range("notifications:user:$userId", 0, -1)
            notificationIds?.forEach { id ->
                getNotification(id)?.let { notifications.add(it) }
            }
        } else {
            // Get global notifications
            val notificationIds = redisTemplate.opsForList().range("notifications:global", 0, -1)
            notificationIds?.forEach { id ->
                getNotification(id)?.let { notifications.add(it) }
            }
        }

        return notifications.sortedByDescending { it.createdAt }
    }

    fun markAsRead(notificationId: String, userId: String? = null): Boolean {
        val notification = getNotification(notificationId) ?: return false

        // Check if user has permission to mark this notification as read
        if (userId != null && notification.userId != null && notification.userId != userId) {
            return false
        }

        notification.read = true
        notification.readAt = LocalDateTime.now()

        // Update in Redis and memory
        redisTemplate.opsForValue().set(
            "notification:$notificationId",
            notification.toJson(),
            java.time.Duration.ofHours(retentionHours)
        )
        activeNotifications[notificationId] = notification

        logger.info("Marked notification as read: $notificationId")
        return true
    }

    fun markAllAsRead(userId: String): Int {
        val notifications = listNotifications(userId)
        var markedCount = 0

        notifications.forEach { notification ->
            if (!notification.read) {
                if (markAsRead(notification.id, userId)) {
                    markedCount++
                }
            }
        }

        logger.info("Marked $markedCount notifications as read for user: $userId")
        return markedCount
    }

    fun deleteNotification(notificationId: String, userId: String? = null): Boolean {
        val notification = getNotification(notificationId) ?: return false

        // Check if user has permission to delete this notification
        if (userId != null && notification.userId != null && notification.userId != userId) {
            return false
        }

        // Remove from Redis
        redisTemplate.delete("notification:$notificationId")

        // Remove from user's list
        notification.userId?.let { uid ->
            redisTemplate.opsForList().remove("notifications:user:$uid", 0, notificationId)
        }

        // Remove from global list
        redisTemplate.opsForList().remove("notifications:global", 0, notificationId)

        // Remove from memory
        activeNotifications.remove(notificationId)

        logger.info("Deleted notification: $notificationId")
        return true
    }

    fun getNotificationStats(userId: String? = null): Map<String, Any> {
        val notifications = listNotifications(userId)
        
        val totalCount = notifications.size
        val unreadCount = notifications.count { !it.read }
        val readCount = totalCount - unreadCount

        val typeStats = notifications.groupBy { it.type }
            .mapValues { it.value.size }

        return mapOf(
            "total" to totalCount,
            "unread" to unreadCount,
            "read" to readCount,
            "by_type" to typeStats,
            "user_id" to (userId ?: "null")
        )
    }

    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM daily
    fun cleanupOldNotifications() {
        logger.info("Starting notification cleanup...")
        
        val cutoffTime = LocalDateTime.now().minusHours(retentionHours)
        var deletedCount = 0

        // Get all notification keys
        val keys = redisTemplate.keys("notification:*")
        keys?.forEach { key ->
            val notificationJson = redisTemplate.opsForValue().get(key)
            val notification = notificationJson?.let { Notification.fromJson(it) }
            
            if (notification != null && notification.createdAt.isBefore(cutoffTime)) {
                val notificationId = notification.id
                
                // Delete notification
                redisTemplate.delete(key)
                
                // Remove from user's list
                notification.userId?.let { uid ->
                    redisTemplate.opsForList().remove("notifications:user:$uid", 0, notificationId)
                }
                
                // Remove from global list
                redisTemplate.opsForList().remove("notifications:global", 0, notificationId)
                
                // Remove from memory
                activeNotifications.remove(notificationId)
                
                deletedCount++
            }
        }

        logger.info("Notification cleanup completed. Deleted $deletedCount notifications")
    }

    private fun generateNotificationId(): String {
        return "notif_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
    }
} 