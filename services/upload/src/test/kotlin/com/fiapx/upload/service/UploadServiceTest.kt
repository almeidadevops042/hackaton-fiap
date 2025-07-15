package com.fiapx.upload.service

import com.fiapx.upload.model.FileMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime
import java.time.Duration

class UploadServiceTest {
    private val redisTemplate: RedisTemplate<String, String> = mockk(relaxed = true)
    private val service = UploadService(redisTemplate)

    @Test
    fun `deve fazer upload de arquivo válido`() {
        val file = MockMultipartFile("video", "video.mp4", "video/mp4", ByteArray(1024))
        val metadata = service.uploadFile(file, userId = "user1", username = "test")
        assertEquals("video.mp4", metadata.filename)
        assertEquals(1024, metadata.size)
        assertEquals("uploaded", metadata.status)
        assertEquals("user1", metadata.userId)
        assertEquals("test", metadata.username)
        assertNotNull(metadata.id)
        assertNotNull(metadata.hash)
        assertNotNull(metadata.uploadedAt)
        verify { redisTemplate.opsForValue().set(match { it.startsWith("upload:") }, any(), Duration.ofHours(24)) }
    }

    @Test
    fun `deve lançar exceção para arquivo inválido`() {
        val file = MockMultipartFile("video", "video.txt", "text/plain", ByteArray(10))
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.uploadFile(file, null, null)
        }
        assertTrue(ex.message!!.contains("Invalid file format"))
    }
} 