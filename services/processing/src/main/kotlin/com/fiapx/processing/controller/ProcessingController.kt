package com.fiapx.processing.controller

import com.fiapx.processing.model.ProcessRequest
import com.fiapx.processing.model.ProcessingResponse
import com.fiapx.processing.service.ProcessingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/process")
@CrossOrigin(origins = ["*"])
class ProcessingController(
    private val processingService: ProcessingService
) {

    @PostMapping
    fun startProcessing(@RequestBody request: ProcessRequest): ResponseEntity<ProcessingResponse> {
        return try {
            val job = processingService.startProcessing(request.fileId)
            ResponseEntity.ok(
                ProcessingResponse(
                    success = true,
                    message = "Processing started successfully",
                    data = job
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                ProcessingResponse(
                    success = false,
                    message = "Failed to start processing",
                    error = e.message
                )
            )
        }
    }

    @GetMapping("/{id}/status")
    fun getProcessingStatus(@PathVariable id: String): ResponseEntity<ProcessingResponse> {
        val job = processingService.getProcessingStatus(id)
        return if (job != null) {
            ResponseEntity.ok(
                ProcessingResponse(
                    success = true,
                    message = "Processing status retrieved",
                    data = job
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/jobs")
    fun listJobs(): ResponseEntity<ProcessingResponse> {
        val jobs = processingService.listJobs()
        return ResponseEntity.ok(
            ProcessingResponse(
                success = true,
                message = "Jobs listed successfully",
                data = mapOf(
                    "jobs" to jobs,
                    "total" to jobs.size
                )
            )
        )
    }

    @DeleteMapping("/{id}")
    fun cancelProcessing(@PathVariable id: String): ResponseEntity<ProcessingResponse> {
        val cancelled = processingService.cancelProcessing(id)
        return if (cancelled) {
            ResponseEntity.ok(
                ProcessingResponse(
                    success = true,
                    message = "Processing cancelled successfully"
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                ProcessingResponse(
                    success = false,
                    message = "Failed to cancel processing",
                    error = "Job not found or cannot be cancelled"
                )
            )
        }
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Processing Service is healthy",
                "data" to mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "version" to "1.0.0",
                    "service" to "processing"
                )
            )
        )
    }
} 