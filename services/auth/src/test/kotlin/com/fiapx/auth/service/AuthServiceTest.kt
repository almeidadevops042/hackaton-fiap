package com.fiapx.auth.service

import com.fiapx.auth.model.CreateUserRequest
import com.fiapx.auth.model.LoginRequest
import com.fiapx.auth.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.context.ActiveProfiles

@EnableAutoConfiguration(exclude = [RedisAutoConfiguration::class, RedisRepositoriesAutoConfiguration::class])
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @MockBean
    private lateinit var valueOperations: ValueOperations<String, Any>

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        // Mock Redis operations
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        doNothing().`when`(valueOperations).set(anyString(), any(), any(java.time.Duration::class.java))
        `when`(valueOperations.get(anyString())).thenReturn(null)
        `when`(redisTemplate.hasKey(anyString())).thenReturn(false)
    }

    @Test
    fun `should register user successfully`() {
        // Given
        val request = CreateUserRequest(
            username = "testuser",
            email = "test@example.com",
            password = "password123"
        )

        // When
        val response = authService.registerUser(request)

        // Then
        assertTrue(response.success)
        assertEquals("User registered successfully", response.message)
        assertNull(response.error)
        
        val userInfo = response.data as com.fiapx.auth.model.UserInfo
        assertEquals("testuser", userInfo.username)
        assertEquals("test@example.com", userInfo.email)
        assertEquals("USER", userInfo.role)
    }

    @Test
    fun `should fail registration with duplicate username`() {
        // Given
        val request1 = CreateUserRequest(
            username = "duplicateuser",
            email = "user1@example.com",
            password = "password123"
        )
        
        val request2 = CreateUserRequest(
            username = "duplicateuser",
            email = "user2@example.com",
            password = "password456"
        )

        // When
        authService.registerUser(request1)
        val response = authService.registerUser(request2)

        // Then
        assertFalse(response.success)
        assertEquals("Registration failed", response.message)
        assertEquals("Username already exists", response.error)
    }

    @Test
    fun `should login successfully with valid credentials`() {
        // Given
        val registerRequest = CreateUserRequest(
            username = "loginuser",
            email = "login@example.com",
            password = "password123"
        )
        
        val loginRequest = LoginRequest(
            username = "loginuser",
            password = "password123"
        )

        // When
        authService.registerUser(registerRequest)
        val response = authService.login(loginRequest)

        // Then
        assertTrue(response.success)
        assertEquals("Login successful", response.message)
        assertNotNull(response.token)
        assertNotNull(response.user)
        assertEquals("loginuser", response.user?.username)
    }

    @Test
    fun `should fail login with invalid credentials`() {
        // Given
        val loginRequest = LoginRequest(
            username = "nonexistentuser",
            password = "wrongpassword"
        )

        // When
        val response = authService.login(loginRequest)

        // Then
        assertFalse(response.success)
        assertEquals("Login failed", response.message)
        assertEquals("Invalid credentials", response.error)
        assertNull(response.token)
        assertNull(response.user)
    }
} 