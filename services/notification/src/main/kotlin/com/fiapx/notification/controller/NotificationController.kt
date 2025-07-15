package com.fiapx.notification.controller

import com.fiapx.notification.model.*
import com.fiapx.notification.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = ["*"])
class NotificationController(
    private val notificationService: NotificationService
) {

    @PostMapping
    fun createNotification(@RequestBody request: CreateNotificationRequest): ResponseEntity<NotificationResponse> {
        val notification = notificationService.createNotification(
            type = request.type,
            title = request.title,
            message = request.message,
            userId = request.userId,
            data = request.data
        )
        
        return ResponseEntity.ok(
            NotificationResponse(
                success = true,
                message = "Notification created successfully",
                data = notification
            )
        )
    }

    @GetMapping
    fun listNotifications(@RequestParam(required = false) userId: String?): ResponseEntity<NotificationResponse> {
        val notifications = notificationService.listNotifications(userId)
        
        return ResponseEntity.ok(
            NotificationResponse(
                success = true,
                message = "Notifications retrieved successfully",
                data = mapOf(
                    "notifications" to notifications,
                    "total" to notifications.size
                )
            )
        )
    }

    @GetMapping("/{id}")
    fun getNotification(@PathVariable id: String): ResponseEntity<NotificationResponse> {
        val notification = notificationService.getNotification(id)
        
        return if (notification != null) {
            ResponseEntity.ok(
                NotificationResponse(
                    success = true,
                    message = "Notification retrieved successfully",
                    data = notification
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: String,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<NotificationResponse> {
        val marked = notificationService.markAsRead(id, userId)
        
        return if (marked) {
            ResponseEntity.ok(
                NotificationResponse(
                    success = true,
                    message = "Notification marked as read"
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                NotificationResponse(
                    success = false,
                    message = "Failed to mark notification as read",
                    error = "Notification not found or access denied"
                )
            )
        }
    }

    @PostMapping("/mark-all-read")
    fun markAllAsRead(@RequestParam(required = false) userId: String?): ResponseEntity<NotificationResponse> {
        if (userId == null) {
            return ResponseEntity.badRequest().body(
                NotificationResponse(
                    success = false,
                    message = "User ID is required",
                    error = "userId parameter is required"
                )
            )
        }
        
        val markedCount = notificationService.markAllAsRead(userId)
        
        return ResponseEntity.ok(
            NotificationResponse(
                success = true,
                message = "Marked $markedCount notifications as read",
                data = mapOf("marked_count" to markedCount)
            )
        )
    }

    @DeleteMapping("/{id}")
    fun deleteNotification(
        @PathVariable id: String,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<NotificationResponse> {
        val deleted = notificationService.deleteNotification(id, userId)
        
        return if (deleted) {
            ResponseEntity.ok(
                NotificationResponse(
                    success = true,
                    message = "Notification deleted successfully"
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                NotificationResponse(
                    success = false,
                    message = "Failed to delete notification",
                    error = "Notification not found or access denied"
                )
            )
        }
    }

    @GetMapping("/stats")
    fun getNotificationStats(@RequestParam(required = false) userId: String?): ResponseEntity<NotificationResponse> {
        val stats = notificationService.getNotificationStats(userId)
        
        return ResponseEntity.ok(
            NotificationResponse(
                success = true,
                message = "Notification statistics retrieved",
                data = stats
            )
        )
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Notification Service is healthy",
                "data" to mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "version" to "1.0.0",
                    "service" to "notification"
                )
            )
        )
    }
} 