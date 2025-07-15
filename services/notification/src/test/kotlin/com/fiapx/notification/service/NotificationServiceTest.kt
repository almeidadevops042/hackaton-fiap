package com.fiapx.notification.service

import com.fiapx.notification.model.Notification
import com.fiapx.notification.model.NotificationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class NotificationServiceTest {
    
    @Test
    fun `deve verificar estrutura do Notification`() {
        // Teste da estrutura do modelo
        val notification = Notification(
            id = "test-id",
            type = NotificationType.SUCCESS,
            title = "Test Title",
            message = "Test Message",
            userId = "user1",
            data = mapOf("key" to "value"),
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            read = false
        )
        
        assertEquals("test-id", notification.id)
        assertEquals(NotificationType.SUCCESS, notification.type)
        assertEquals("Test Title", notification.title)
        assertEquals("Test Message", notification.message)
        assertEquals("user1", notification.userId)
        assertEquals("value", notification.data?.get("key"))
        assertEquals(false, notification.read)
        assertNotNull(notification.createdAt)
    }
    
    @Test
    fun `deve verificar serialização do Notification`() {
        // Teste de serialização
        val notification = Notification(
            id = "test-id",
            type = NotificationType.ERROR,
            title = "Error Title",
            message = "Error Message",
            userId = "user1",
            data = mapOf("errorCode" to "500"),
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            read = true
        )
        
        val json = notification.toJson()
        assertNotNull(json)
        assertTrue(json.contains("test-id"))
        assertTrue(json.contains("ERROR"))
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
    
    @Test
    fun `deve verificar comportamento do campo read`() {
        // Teste do comportamento do campo read
        val notification = Notification(
            id = "test-id",
            type = NotificationType.INFO,
            title = "Test",
            message = "Test",
            createdAt = LocalDateTime.now()
        )
        
        assertEquals(false, notification.read) // valor padrão
        notification.read = true
        assertEquals(true, notification.read)
        
        notification.readAt = LocalDateTime.now()
        assertNotNull(notification.readAt)
    }
} 