package com.fiapx.storage

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class StorageApplication

fun main(args: Array<String>) {
    runApplication<StorageApplication>(*args)
} 