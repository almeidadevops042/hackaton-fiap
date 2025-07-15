package com.fiapx.gateway.controller

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class WebController {

    fun getIndexPage(request: ServerRequest): Mono<ServerResponse> {
        val resource = ClassPathResource("static/index.html")
        val content = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(content)
    }

    fun health(request: ServerRequest): Mono<ServerResponse> {
        val healthData = mapOf(
            "status" to "UP",
            "service" to "gateway-service",
            "timestamp" to System.currentTimeMillis()
        )
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(healthData)
    }
} 