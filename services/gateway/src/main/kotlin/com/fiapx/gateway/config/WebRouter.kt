package com.fiapx.gateway.config

import com.fiapx.gateway.controller.WebController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@Configuration
class WebRouter(private val webController: WebController) {

    @Bean
    fun webRoutes(): RouterFunction<ServerResponse> = router {
        GET("/") { webController.getIndexPage(it) }
        GET("/health") { webController.health(it) }
    }
} 