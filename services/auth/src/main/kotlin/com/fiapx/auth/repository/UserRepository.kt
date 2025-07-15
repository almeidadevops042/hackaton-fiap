package com.fiapx.auth.repository

import com.fiapx.auth.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun findByEmail(email: String): Optional<UserEntity>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
} 