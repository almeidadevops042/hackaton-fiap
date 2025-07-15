package com.fiapx.auth.model

import java.time.LocalDateTime

data class User(
    val id: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: String = "USER",
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastLoginAt: LocalDateTime? = null
)

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserInfo? = null,
    val error: String? = null
)

data class UserInfo(
    val id: String,
    val username: String,
    val email: String,
    val role: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: String? = null
) 