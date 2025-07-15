package com.fiapx.processing.service

import com.fiapx.processing.model.ProcessingJob
import com.fiapx.processing.model.ProcessingStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class ProcessingServiceTest {
    
    @Test
    fun `deve verificar estrutura do ProcessingJob`() {
        // Teste da estrutura do modelo
        val job = ProcessingJob(
            id = "job-123",
            fileId = "file-456",
            status = ProcessingStatus.PENDING,
            progress = 0,
            createdAt = LocalDateTime.of(2020,1,1,0,0),
            startedAt = null,
            completedAt = null,
            error = null
        )
        
        assertEquals("job-123", job.id)
        assertEquals("file-456", job.fileId)
        assertEquals(ProcessingStatus.PENDING, job.status)
        assertEquals(0, job.progress)
        assertNotNull(job.createdAt)
        assertNull(job.startedAt)
        assertNull(job.completedAt)
        assertNull(job.error)
    }
    
    @Test
    fun `deve verificar serialização do ProcessingJob`() {
        // Teste de serialização
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
        
        val json = job.toJson()
        assertNotNull(json)
        assertTrue(json.contains("job-123"))
        assertTrue(json.contains("PROCESSING"))
        assertTrue(json.contains("50"))
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
    fun `deve verificar progresso do job`() {
        // Teste do comportamento do progresso
        val job = ProcessingJob(
            id = "job-123",
            fileId = "file-456",
            status = ProcessingStatus.PROCESSING,
            progress = 75,
            createdAt = LocalDateTime.now()
        )
        
        assertEquals(75, job.progress)
        assertTrue(job.progress in 0..100)
    }
    
    @Test
    fun `deve verificar job com erro`() {
        // Teste de job com erro
        val job = ProcessingJob(
            id = "job-123",
            fileId = "file-456",
            status = ProcessingStatus.FAILED,
            progress = 30,
            createdAt = LocalDateTime.now(),
            error = "FFmpeg not found"
        )
        
        assertEquals(ProcessingStatus.FAILED, job.status)
        assertEquals("FFmpeg not found", job.error)
    }
} 