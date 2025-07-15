package com.fiapx.auth.controller

import com.fiapx.auth.model.*
import com.fiapx.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = ["*"])
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: CreateUserRequest): ResponseEntity<AuthResponse> {
        val response = authService.registerUser(request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String): ResponseEntity<AuthResponse> {
        val token = authHeader.removePrefix("Bearer ")
        val response = authService.logout(token)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<AuthResponse> {
        val token = authHeader.removePrefix("Bearer ")
        
        if (authService.isTokenBlacklisted(token)) {
            return ResponseEntity.badRequest().body(
                AuthResponse(
                    success = false,
                    message = "Token validation failed",
                    error = "Token is blacklisted"
                )
            )
        }

        val userInfo = authService.validateToken(token)
        return if (userInfo != null) {
            ResponseEntity.ok(
                AuthResponse(
                    success = true,
                    message = "Token is valid",
                    data = userInfo
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                AuthResponse(
                    success = false,
                    message = "Token validation failed",
                    error = "Invalid or expired token"
                )
            )
        }
    }

    @GetMapping("/users")
    fun getAllUsers(): ResponseEntity<AuthResponse> {
        val users = authService.getAllUsers()
        return ResponseEntity.ok(
            AuthResponse(
                success = true,
                message = "Users retrieved successfully",
                data = users
            )
        )
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "auth-service"))
    }
} 