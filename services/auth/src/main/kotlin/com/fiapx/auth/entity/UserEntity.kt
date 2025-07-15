package com.fiapx.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    val id: String = "",
    
    @Column(unique = true, nullable = false)
    val username: String = "",
    
    @Column(unique = true, nullable = false)
    val email: String = "",
    
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String = "",
    
    @Column(nullable = false)
    val role: String = "USER",
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "last_login_at")
    val lastLoginAt: LocalDateTime? = null
) {
    constructor() : this("", "", "", "", "USER", true, LocalDateTime.now(), null)
} 