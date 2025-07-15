package com.fiapx.auth.service

import com.fiapx.auth.entity.UserEntity
import com.fiapx.auth.model.*
import com.fiapx.auth.repository.UserRepository
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.Key
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Service
class AuthService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    
    @Value("\${app.auth.jwt.secret:defaultSecretKeyForDevelopmentOnly}")
    private val jwtSecret: String,
    
    @Value("\${app.auth.jwt.expiration:86400}") // 24 hours in seconds
    private val jwtExpiration: Long
) {

    private val jwtKey: Key = run {
        val secretBytes = jwtSecret.toByteArray()
        if (secretBytes.size < 32) {
            // If secret is too short, pad it to at least 256 bits (32 bytes)
            val paddedSecret = jwtSecret.padEnd(32, '0')
            Keys.hmacShaKeyFor(paddedSecret.toByteArray())
        } else {
            Keys.hmacShaKeyFor(secretBytes)
        }
    }

    fun registerUser(request: CreateUserRequest): AuthResponse {
        // Check if username already exists
        if (userRepository.existsByUsername(request.username)) {
            return AuthResponse(
                success = false,
                message = "Registration failed",
                error = "Username already exists"
            )
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.email)) {
            return AuthResponse(
                success = false,
                message = "Registration failed",
                error = "Email already exists"
            )
        }

        // Create new user
        val userId = generateUserId()
        val userEntity = UserEntity(
            id = userId,
            username = request.username,
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
        )

        // Save to PostgreSQL
        val savedUser = userRepository.save(userEntity)

        // Cache in Redis for performance
        val user = User(
            id = savedUser.id,
            username = savedUser.username,
            email = savedUser.email,
            passwordHash = savedUser.passwordHash,
            role = savedUser.role,
            isActive = savedUser.isActive,
            createdAt = savedUser.createdAt,
            lastLoginAt = savedUser.lastLoginAt
        )
        
        redisTemplate.opsForValue().set("user:${userId}", user, java.time.Duration.ofHours(1))
        redisTemplate.opsForValue().set("user:username:${request.username}", userId, java.time.Duration.ofHours(1))
        redisTemplate.opsForValue().set("user:email:${request.email}", userId, java.time.Duration.ofHours(1))

        return AuthResponse(
            success = true,
            message = "User registered successfully",
            data = UserInfo(
                id = user.id,
                username = user.username,
                email = user.email,
                role = user.role
            )
        )
    }

    fun login(request: LoginRequest): LoginResponse {
        val userEntity = userRepository.findByUsername(request.username).orElse(null)
        if (userEntity == null) {
            return LoginResponse(
                success = false,
                message = "Login failed",
                error = "Invalid credentials"
            )
        }

        if (!userEntity.isActive) {
            return LoginResponse(
                success = false,
                message = "Login failed",
                error = "Account is deactivated"
            )
        }

        if (!passwordEncoder.matches(request.password, userEntity.passwordHash)) {
            return LoginResponse(
                success = false,
                message = "Login failed",
                error = "Invalid credentials"
            )
        }

        // Update last login in PostgreSQL
        val updatedUserEntity = userEntity.copy(lastLoginAt = LocalDateTime.now())
        userRepository.save(updatedUserEntity)

        // Update cache
        val user = User(
            id = updatedUserEntity.id,
            username = updatedUserEntity.username,
            email = updatedUserEntity.email,
            passwordHash = updatedUserEntity.passwordHash,
            role = updatedUserEntity.role,
            isActive = updatedUserEntity.isActive,
            createdAt = updatedUserEntity.createdAt,
            lastLoginAt = updatedUserEntity.lastLoginAt
        )
        
        redisTemplate.opsForValue().set("user:${user.id}", user, java.time.Duration.ofHours(1))

        // Generate JWT token
        val token = generateJwtToken(user)

        return LoginResponse(
            success = true,
            message = "Login successful",
            token = token,
            user = UserInfo(
                id = user.id,
                username = user.username,
                email = user.email,
                role = user.role
            )
        )
    }

    fun validateToken(token: String): UserInfo? {
        return try {
            val claims = Jwts.parser()
                .setSigningKey(jwtKey)
                .parseClaimsJws(token)
                .body

            val userId = claims["userId"] as String
            val user = getUserById(userId)
            
            if (user != null && user.isActive) {
                UserInfo(
                    id = user.id,
                    username = user.username,
                    email = user.email,
                    role = user.role
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getUserById(userId: String): User? {
        // Try cache first
        val cachedUser = redisTemplate.opsForValue().get("user:${userId}") as? User
        if (cachedUser != null) {
            return cachedUser
        }

        // Fallback to database
        val userEntity = userRepository.findById(userId).orElse(null)
        return userEntity?.let { entity ->
            val user = User(
                id = entity.id,
                username = entity.username,
                email = entity.email,
                passwordHash = entity.passwordHash,
                role = entity.role,
                isActive = entity.isActive,
                createdAt = entity.createdAt,
                lastLoginAt = entity.lastLoginAt
            )
            
            // Cache for future requests
            redisTemplate.opsForValue().set("user:${userId}", user, java.time.Duration.ofHours(1))
            user
        }
    }

    fun getUserByUsername(username: String): User? {
        // Try cache first
        val userId = redisTemplate.opsForValue().get("user:username:${username}") as? String
        if (userId != null) {
            return getUserById(userId)
        }

        // Fallback to database
        val userEntity = userRepository.findByUsername(username).orElse(null)
        return userEntity?.let { entity ->
            val user = User(
                id = entity.id,
                username = entity.username,
                email = entity.email,
                passwordHash = entity.passwordHash,
                role = entity.role,
                isActive = entity.isActive,
                createdAt = entity.createdAt,
                lastLoginAt = entity.lastLoginAt
            )
            
            // Cache for future requests
            redisTemplate.opsForValue().set("user:${entity.id}", user, java.time.Duration.ofHours(1))
            redisTemplate.opsForValue().set("user:username:${username}", entity.id, java.time.Duration.ofHours(1))
            user
        }
    }

    fun getUserByEmail(email: String): User? {
        // Try cache first
        val userId = redisTemplate.opsForValue().get("user:email:${email}") as? String
        if (userId != null) {
            return getUserById(userId)
        }

        // Fallback to database
        val userEntity = userRepository.findByEmail(email).orElse(null)
        return userEntity?.let { entity ->
            val user = User(
                id = entity.id,
                username = entity.username,
                email = entity.email,
                passwordHash = entity.passwordHash,
                role = entity.role,
                isActive = entity.isActive,
                createdAt = entity.createdAt,
                lastLoginAt = entity.lastLoginAt
            )
            
            // Cache for future requests
            redisTemplate.opsForValue().set("user:${entity.id}", user, java.time.Duration.ofHours(1))
            redisTemplate.opsForValue().set("user:email:${email}", entity.id, java.time.Duration.ofHours(1))
            user
        }
    }

    fun getAllUsers(): List<UserInfo> {
        return userRepository.findAll().map { entity ->
            UserInfo(
                id = entity.id,
                username = entity.username,
                email = entity.email,
                role = entity.role
            )
        }
    }

    fun logout(token: String): AuthResponse {
        // Add token to blacklist
        val expiration = getTokenExpiration(token)
        if (expiration != null) {
            redisTemplate.opsForValue().set("blacklist:${token}", "logged_out", 
                java.time.Duration.ofSeconds(expiration))
        }

        return AuthResponse(
            success = true,
            message = "Logout successful"
        )
    }

    fun isTokenBlacklisted(token: String): Boolean {
        return redisTemplate.hasKey("blacklist:${token}") ?: false
    }

    private fun generateJwtToken(user: User): String {
        val now = LocalDateTime.now()
        val expiration = now.plusSeconds(jwtExpiration)

        return Jwts.builder()
            .setSubject(user.username)
            .claim("userId", user.id)
            .claim("email", user.email)
            .claim("role", user.role)
            .setIssuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
            .setExpiration(Date.from(expiration.toInstant(ZoneOffset.UTC)))
            .signWith(jwtKey)
            .compact()
    }

    private fun getTokenExpiration(token: String): Long? {
        return try {
            val claims = Jwts.parser()
                .setSigningKey(jwtKey)
                .parseClaimsJws(token)
                .body

            val expiration = claims.expiration
            val now = Date()
            
            if (expiration.after(now)) {
                (expiration.time - now.time) / 1000
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateUserId(): String {
        return "user_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }
} 