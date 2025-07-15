package com.fiapx.processing.controller

import com.fiapx.processing.model.ProcessingJob
import com.fiapx.processing.model.ProcessingStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class ProcessingControllerTest {
    
    @Test
    fun `deve verificar estrutura do ProcessingResponse`() {
        // Teste da estrutura do modelo
        val response = com.fiapx.processing.model.ProcessingResponse(
            success = true,
            message = "Test message",
            data = "test data"
        )
        
        assertTrue(response.success)
        assertEquals("Test message", response.message)
        assertEquals("test data", response.data)
    }
    
    @Test
    fun `deve verificar estrutura do ProcessRequest`() {
        // Teste da estrutura do modelo
        val request = com.fiapx.processing.model.ProcessRequest(
            fileId = "file-123"
        )
        
        assertEquals("file-123", request.fileId)
    }
    
    @Test
    fun `deve verificar estrutura do ProcessingJob`() {
        // Teste da estrutura do modelo
        val job = ProcessingJob(
            id = "job-123",
            fileId = "file-456",
            status = ProcessingStatus.PROCESSING,
            progress = 50,
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            startedAt = LocalDateTime.of(2020,1,1,1,0),
            completedAt = null,
            error = null
        )
        
        assertEquals("job-123", job.id)
        assertEquals("file-456", job.fileId)
        assertEquals(ProcessingStatus.PROCESSING, job.status)
        assertEquals(50, job.progress)
        assertNotNull(job.createdAt)
        assertNotNull(job.startedAt)
        assertNull(job.completedAt)
        assertNull(job.error)
    }
    
    @Test
    fun `deve verificar enum ProcessingStatus`() {
        // Teste dos valores do enum
        assertEquals("PENDING", ProcessingStatus.PENDING.name)
        assertEquals("PROCESSING", ProcessingStatus.PROCESSING.name)
        assertEquals("COMPLETED", ProcessingStatus.COMPLETED.name)
        assertEquals("FAILED", ProcessingStatus.FAILED.name)
        assertEquals("CANCELLED", ProcessingStatus.CANCELLED.name)
    }
    
    @Test
    fun `deve verificar job completado`() {
        // Teste de job completado
        val job = ProcessingJob(
            id = "job-123",
            fileId = "file-456",
            status = ProcessingStatus.COMPLETED,
            progress = 100,
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            startedAt = LocalDateTime.of(2020,1,1,1,0),
            completedAt = LocalDateTime.of(2020,1,1,2,0),
            error = null
        )
        
        assertEquals(ProcessingStatus.COMPLETED, job.status)
        assertEquals(100, job.progress)
        assertNotNull(job.completedAt)
    }
} 