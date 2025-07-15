package com.fiapx.gateway.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.test.context.TestPropertySource
import org.junit.jupiter.api.Assertions.*

@SpringBootTest
@TestPropertySource(properties = [
    "services.upload-url=http://upload-service:8081",
    "services.processing-url=http://processing-service:8082",
    "services.storage-url=http://storage-service:8083",
    "services.notification-url=http://notification-service:8084",
    "services.auth-url=http://auth-service:8085"
])
class GatewayConfigTest {
    @Autowired
    lateinit var routeLocator: RouteLocator

    @Test
    fun `deve configurar rotas do gateway`() {
        assertNotNull(routeLocator)
        
        val routes = routeLocator.routes.collectList().block()
        assertNotNull(routes)
        assertTrue(routes!!.isNotEmpty())
        
        // Verifica se as rotas principais estão configuradas
        val routeIds = routes.map { it.id }
        assertTrue(routeIds.contains("upload-service"))
        assertTrue(routeIds.contains("processing-service"))
        assertTrue(routeIds.contains("storage-service-files"))
        assertTrue(routeIds.contains("storage-service-download"))
        assertTrue(routeIds.contains("notification-service"))
        assertTrue(routeIds.contains("auth-service"))
    }
} 