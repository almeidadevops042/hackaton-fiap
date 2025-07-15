package com.fiapx.upload.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val jwtInterceptor: JwtInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/upload/**", "/uploads/**")
            .excludePathPatterns("/health")
    }
} 