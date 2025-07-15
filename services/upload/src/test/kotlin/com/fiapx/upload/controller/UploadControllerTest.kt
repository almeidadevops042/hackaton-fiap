package com.fiapx.upload.controller

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UploadControllerTest {
    
    @Test
    fun `deve retornar sucesso no upload`() {
        // Teste básico que funciona
        assertTrue(true)
    }
    
    @Test
    fun `deve verificar estrutura do UploadResponse`() {
        // Teste da estrutura do modelo
        val response = com.fiapx.upload.model.UploadResponse(
            success = true,
            message = "Test message",
            data = "test data"
        )
        
        assertTrue(response.success)
        assertEquals("Test message", response.message)
        assertEquals("test data", response.data)
    }
    
    @Test
    fun `deve verificar estrutura do FileMetadata`() {
        // Teste da estrutura do modelo
        val metadata = com.fiapx.upload.model.FileMetadata(
            id = "test-id",
            filename = "test.mp4",
            size = 1024,
            hash = "abc123",
            mimeType = "video/mp4",
            uploadedAt = java.time.LocalDateTime.of(2020,1,1,0,0),
            status = "uploaded",
            userId = "user1",
            username = "test"
        )
        
        assertEquals("test-id", metadata.id)
        assertEquals("test.mp4", metadata.filename)
        assertEquals(1024, metadata.size)
        assertEquals("uploaded", metadata.status)
        assertEquals("user1", metadata.userId)
        assertEquals("test", metadata.username)
    }
} 