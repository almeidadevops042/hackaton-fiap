package com.fiapx.upload.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fiapx.upload.model.UploadResponse
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class JwtInterceptor(
    @Value("\${app.auth.jwt.secret:defaultSecretKeyForDevelopmentOnly}")
    private val jwtSecret: String
) : HandlerInterceptor {

    private val jwtKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    private val objectMapper = ObjectMapper()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // Skip authentication for health check
        if (request.requestURI == "/health") {
            return true
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, "Missing or invalid Authorization header")
            return false
        }

        val token = authHeader.substring(7)
        
        try {
            val claims = Jwts.parser()
                .setSigningKey(jwtKey)
                .parseClaimsJws(token)
                .body

            val userId = claims["userId"] as String
            val username = claims["sub"] as String

            // Add user info to request attributes
            request.setAttribute("userId", userId)
            request.setAttribute("username", username)
            
            return true
        } catch (e: Exception) {
            sendErrorResponse(response, "Invalid or expired token")
            return false
        }
    }

    private fun sendErrorResponse(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/json"
        
        val errorResponse = UploadResponse(
            success = false,
            message = "Authentication failed",
            error = message
        )
        
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
} 