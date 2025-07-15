package com.fiapx.auth.config

import com.fiapx.auth.model.User
import com.fiapx.auth.model.UserInfo
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory

        // Use String serializer for keys
        template.keySerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()

        // Use JSON serializer for values
        val jsonSerializer = GenericJackson2JsonRedisSerializer()
        template.valueSerializer = jsonSerializer
        template.hashValueSerializer = jsonSerializer

        template.afterPropertiesSet()
        return template
    }
} 