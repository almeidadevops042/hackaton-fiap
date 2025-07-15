package com.fiapx.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class GatewayConfig(
    @Value("\${services.upload-url:http://upload-service:8081}") 
    private val uploadServiceUrl: String,
    
    @Value("\${services.processing-url:http://processing-service:8082}") 
    private val processingServiceUrl: String,
    
    @Value("\${services.storage-url:http://storage-service:8083}") 
    private val storageServiceUrl: String,
    
    @Value("\${services.notification-url:http://notification-service:8084}") 
    private val notificationServiceUrl: String,
    
    @Value("\${services.auth-url:http://auth-service:8085}") 
    private val authServiceUrl: String
) {

    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            // Upload Service Routes
            .route("upload-service") { r ->
                r.path("/api/v1/upload/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(uploadServiceUrl)
            }
            
            // Processing Service Routes
            .route("processing-service") { r ->
                r.path("/api/v1/process/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(processingServiceUrl)
            }
            
            // Storage Service Routes
            .route("storage-service-files") { r ->
                r.path("/api/v1/files/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(storageServiceUrl)
            }
            
            .route("storage-service-download") { r ->
                r.path("/api/v1/download/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(storageServiceUrl)
            }
            
            // Notification Service Routes
            .route("notification-service") { r ->
                r.path("/api/v1/notifications/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(notificationServiceUrl)
            }
            
            // Auth Service Routes
            .route("auth-service") { r ->
                r.path("/api/v1/auth/**")
                    .filters { f ->
                        f.stripPrefix(2)
                        f.addRequestHeader("X-Gateway-Source", "api-gateway")
                    }
                    .uri(authServiceUrl)
            }
            .build()
    }

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val corsConfig = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            maxAge = 3600L
            addAllowedMethod("*")
            addAllowedHeader("*")
            allowCredentials = true
        }

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", corsConfig)
        }

        return CorsWebFilter(source)
    }
} 