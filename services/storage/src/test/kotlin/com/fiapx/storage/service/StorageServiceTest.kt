package com.fiapx.storage.service

import com.fiapx.storage.model.FileInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class StorageServiceTest {
    
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
            size = 2097152L, // 2MB
            type = "output",
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            downloadCount = 10L
        )
        
        assertEquals(2.0, fileInfo.sizeInMB, 0.01)
        assertEquals(2048.0, fileInfo.sizeInKB, 0.01)
    }
    
    @Test
    fun `deve verificar valores padrão do FileInfo`() {
        // Teste com valores padrão
        val fileInfo = FileInfo(
            name = "test.mp4",
            size = 100L,
            type = "upload",
            createdAt = LocalDateTime.now()
        )
        
        assertEquals(0L, fileInfo.downloadCount) // valor padrão
        assertNotNull(fileInfo.createdAt)
    }
} 