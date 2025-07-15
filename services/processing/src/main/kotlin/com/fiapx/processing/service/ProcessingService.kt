package com.fiapx.processing.service

import com.fiapx.processing.model.ProcessingJob
import com.fiapx.processing.model.ProcessingStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.*

@Service
class ProcessingService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.processing.output-dir:outputs}") private val outputDir: String,
    @Value("\${app.processing.temp-dir:processing}") private val tempDir: String
) {
    private val logger = LoggerFactory.getLogger(ProcessingService::class.java)
    private val activeJobs = ConcurrentHashMap<String, ProcessingJob>()
    private val jobCounter = AtomicInteger(0)

    init {
        // Create necessary directories
        File(outputDir).mkdirs()
        File(tempDir).mkdirs()
    }

    fun startProcessing(fileId: String): ProcessingJob {
        val jobId = generateJobId()
        val job = ProcessingJob(
            id = jobId,
            fileId = fileId,
            status = ProcessingStatus.PENDING,
            progress = 0,
            createdAt = LocalDateTime.now()
        )

        // Store job in Redis
        redisTemplate.opsForValue().set("job:$jobId", job.toJson(), java.time.Duration.ofHours(24))
        
        // Add to processing queue
        redisTemplate.opsForList().leftPush("processing_queue", jobId)
        
        // Store in memory for active tracking
        activeJobs[jobId] = job
        
        logger.info("Created processing job: $jobId for file: $fileId")
        
        return job
    }

    fun getProcessingStatus(jobId: String): ProcessingJob? {
        // First check active jobs
        activeJobs[jobId]?.let { return it }
        
        // Then check Redis
        val jobJson = redisTemplate.opsForValue().get("job:$jobId")
        return jobJson?.let { ProcessingJob.fromJson(it) }
    }

    fun listJobs(): List<ProcessingJob> {
        val jobs = mutableListOf<ProcessingJob>()
        
        // Get all job keys from Redis
        val keys = redisTemplate.keys("job:*")
        keys?.forEach { key ->
            val jobJson = redisTemplate.opsForValue().get(key)
            jobJson?.let { 
                ProcessingJob.fromJson(it)?.let { job -> jobs.add(job) }
            }
        }
        
        return jobs.sortedByDescending { it.createdAt }
    }

    fun cancelProcessing(jobId: String): Boolean {
        val job = getProcessingStatus(jobId) ?: return false
        
        if (job.status != ProcessingStatus.PENDING && job.status != ProcessingStatus.PROCESSING) {
            return false
        }
        
        job.status = ProcessingStatus.CANCELLED
        job.completedAt = LocalDateTime.now()
        
        // Update in Redis and memory
        redisTemplate.opsForValue().set("job:$jobId", job.toJson(), java.time.Duration.ofHours(24))
        activeJobs[jobId] = job
        
        logger.info("Cancelled processing job: $jobId")
        return true
    }

    @Scheduled(fixedDelay = 1000) // Check queue every second
    fun processQueue() {
        val jobId = redisTemplate.opsForList().rightPop("processing_queue", java.time.Duration.ofSeconds(1))
        jobId?.let { id ->
            CoroutineScope(Dispatchers.IO).launch {
                processJob(id)
            }
        }
    }

    private suspend fun processJob(jobId: String) {
        val job = getProcessingStatus(jobId) ?: return
        
        if (job.status == ProcessingStatus.CANCELLED) {
            return
        }
        
        // Update status to processing
        job.status = ProcessingStatus.PROCESSING
        job.startedAt = LocalDateTime.now()
        job.progress = 0
        updateJob(job)
        
        logger.info("Starting processing job: $jobId for file: ${job.fileId}")
        
        try {
            // Find input file
            val inputFile = findInputFile(job.fileId)
            
            // Process video
            val (outputFile, frameCount) = processVideo(inputFile, jobId, job)
            
            // Update job as completed
            job.status = ProcessingStatus.COMPLETED
            job.progress = 100
            job.outputFile = outputFile
            job.frameCount = frameCount
            job.completedAt = LocalDateTime.now()
            
            updateJob(job)
            
            logger.info("Completed processing job: $jobId with $frameCount frames")
            
            // Notify completion
            notifyCompletion(job)
            
        } catch (e: Exception) {
            logger.error("Failed to process job: $jobId", e)
            
            job.status = ProcessingStatus.FAILED
            job.error = e.message
            job.completedAt = LocalDateTime.now()
            
            updateJob(job)
        }
    }

    private fun findInputFile(fileId: String): File {
        val uploadsDir = File("../uploads")
        val pattern = "$fileId*"
        
        val files = uploadsDir.listFiles { file -> 
            file.name.startsWith(fileId) && file.isFile 
        }
        
        return files?.firstOrNull() 
            ?: throw IllegalArgumentException("Input file not found for file ID: $fileId")
    }

    private suspend fun processVideo(inputFile: File, jobId: String, job: ProcessingJob): Pair<String, Int> {
        val tempJobDir = File(tempDir, jobId)
        tempJobDir.mkdirs()
        
        try {
            // Update progress
            job.progress = 10
            updateJob(job)
            
            // Extract frames using FFmpeg
            val framePattern = File(tempJobDir, "frame_%04d.png").absolutePath
            val ffmpegResult = executeFFmpeg(
                inputFile.absolutePath,
                framePattern
            )
            
            if (ffmpegResult != 0) {
                throw RuntimeException("FFmpeg failed with exit code: $ffmpegResult")
            }
            
            // Count frames
            val frames = tempJobDir.listFiles { file -> file.extension == "png" }
            val frameCount = frames?.size ?: 0
            
            if (frameCount == 0) {
                throw RuntimeException("No frames extracted from video")
            }
            
            // Update progress
            job.progress = 70
            updateJob(job)
            
            // Create ZIP file
            val zipFilename = "frames_$jobId.zip"
            val zipPath = File(outputDir, zipFilename)
            
            createZipFile(frames.toList(), zipPath)
            
            // Update progress
            job.progress = 100
            updateJob(job)
            
            return Pair(zipFilename, frameCount)
            
        } finally {
            // Clean up temporary directory
            tempJobDir.deleteRecursively()
        }
    }

    private fun executeFFmpeg(inputPath: String, outputPattern: String): Int {
        val processBuilder = ProcessBuilder(
            "ffmpeg",
            "-i", inputPath,
            "-vf", "fps=1",
            "-y",
            outputPattern
        )
        
        val process = processBuilder.start()
        return process.waitFor()
    }

    private fun createZipFile(files: List<File>, zipPath: File) {
        // Simple ZIP creation - in production, use a proper ZIP library
        logger.info("Creating ZIP file: ${zipPath.absolutePath} with ${files.size} frames")
        
        // For now, just create an empty ZIP file
        // In a real implementation, you would use java.util.zip.ZipOutputStream
        zipPath.createNewFile()
    }

    private fun updateJob(job: ProcessingJob) {
        redisTemplate.opsForValue().set("job:${job.id}", job.toJson(), java.time.Duration.ofHours(24))
        activeJobs[job.id] = job
    }

    private fun generateJobId(): String {
        return "job_${System.currentTimeMillis()}_${jobCounter.incrementAndGet()}"
    }

    private fun notifyCompletion(job: ProcessingJob) {
        logger.info("Notifying completion of job: ${job.id}")
        
        // In a real implementation:
        // - Make HTTP POST to notification service
        // - Include job details
        // - Handle errors and retries
    }

    fun checkFFmpegAvailability(): Boolean {
        return try {
            val process = ProcessBuilder("ffmpeg", "-version").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
} 