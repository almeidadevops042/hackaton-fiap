package com.fiapx.storage.controller

import com.fiapx.storage.model.FileInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class StorageControllerTest {
    
    @Test
    fun `deve verificar estrutura do StorageResponse`() {
        // Teste da estrutura do modelo
        val response = com.fiapx.storage.model.StorageResponse(
            success = true,
            message = "Test message",
            data = "test data"
        )
        
        assertTrue(response.success)
        assertEquals("Test message", response.message)
        assertEquals("test data", response.data)
    }
    
    @Test
    fun `deve verificar estrutura do FileInfo`() {
        // Teste da estrutura do modelo
        val fileInfo = FileInfo(
            name = "test-video.mp4",
            size = 1024L,
            type = "upload",
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            downloadCount = 5L
        )
        
        assertEquals("test-video.mp4", fileInfo.name)
        assertEquals(1024L, fileInfo.size)
        assertEquals("upload", fileInfo.type)
        assertEquals(5L, fileInfo.downloadCount)
        assertNotNull(fileInfo.createdAt)
    }
    
    @Test
    fun `deve verificar propriedades calculadas do FileInfo`() {
        // Teste das propriedades calculadas
        val fileInfo = FileInfo(
            name = "test-video.mp4",
            size = 1048576L, // 1MB
            type = "output",
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            downloadCount = 10L
        )
        
        assertEquals(1.0, fileInfo.sizeInMB, 0.01)
        assertEquals(1024.0, fileInfo.sizeInKB, 0.01)
    }
    
    @Test
    fun `deve verificar tipos de arquivo`() {
        // Teste dos tipos de arquivo
        val uploadFile = FileInfo(
            name = "upload.mp4",
            size = 100L,
            type = "upload",
            createdAt = LocalDateTime.now()
        )
        
        val outputFile = FileInfo(
            name = "output.mp4",
            size = 200L,
            type = "output",
            createdAt = LocalDateTime.now()
        )
        
        assertEquals("upload", uploadFile.type)
        assertEquals("output", outputFile.type)
    }
} 