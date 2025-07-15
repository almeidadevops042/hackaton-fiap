package com.fiapx.processing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ProcessingApplication

fun main(args: Array<String>) {
    runApplication<ProcessingApplication>(*args)
} 