package com.fiapx.notification.controller

import com.fiapx.notification.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NotificationControllerTest {
    
    @Test
    fun `deve verificar estrutura do NotificationResponse`() {
        // Teste da estrutura do modelo
        val response = NotificationResponse(
            success = true,
            message = "Test message",
            data = "test data"
        )
        
        assertTrue(response.success)
        assertEquals("Test message", response.message)
        assertEquals("test data", response.data)
    }
    
    @Test
    fun `deve verificar estrutura do CreateNotificationRequest`() {
        // Teste da estrutura do modelo
        val request = CreateNotificationRequest(
            type = NotificationType.SUCCESS,
            title = "Test Title",
            message = "Test Message",
            userId = "user1",
            data = mapOf("key" to "value")
        )
        
        assertEquals(NotificationType.SUCCESS, request.type)
        assertEquals("Test Title", request.title)
        assertEquals("Test Message", request.message)
        assertEquals("user1", request.userId)
        assertEquals("value", request.data?.get("key"))
    }
    
    @Test
    fun `deve verificar estrutura do MarkReadRequest`() {
        // Teste da estrutura do modelo
        val request = MarkReadRequest(
            notificationIds = listOf("notif1", "notif2")
        )
        
        assertEquals(2, request.notificationIds.size)
        assertTrue(request.notificationIds.contains("notif1"))
        assertTrue(request.notificationIds.contains("notif2"))
    }
    
    @Test
    fun `deve verificar estrutura do NotificationStats`() {
        // Teste da estrutura do modelo
        val stats = NotificationStats(
            totalNotifications = 10,
            unreadNotifications = 5,
            readNotifications = 3,
            expiredNotifications = 2
        )
        
        assertEquals(10, stats.totalNotifications)
        assertEquals(5, stats.unreadNotifications)
        assertEquals(3, stats.readNotifications)
        assertEquals(2, stats.expiredNotifications)
    }
    
    @Test
    fun `deve verificar enum NotificationType`() {
        // Teste dos valores do enum
        assertEquals("INFO", NotificationType.INFO.name)
        assertEquals("SUCCESS", NotificationType.SUCCESS.name)
        assertEquals("WARNING", NotificationType.WARNING.name)
        assertEquals("ERROR", NotificationType.ERROR.name)
        assertEquals("PROCESSING", NotificationType.PROCESSING.name)
    }
} 