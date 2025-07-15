package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/go-redis/redis/v8"
	"golang.org/x/net/context"
)

type Config struct {
	RedisURL             string
	StorageServiceURL    string
	NotificationServiceURL string
}

type ProcessingResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
}

type ProcessingJob struct {
	ID            string    `json:"id"`
	FileID        string    `json:"file_id"`
	Status        string    `json:"status"` // pending, processing, completed, failed
	Progress      int       `json:"progress"`
	CreatedAt     time.Time `json:"created_at"`
	StartedAt     *time.Time `json:"started_at,omitempty"`
	CompletedAt   *time.Time `json:"completed_at,omitempty"`
	OutputFile    string    `json:"output_file,omitempty"`
	FrameCount    int       `json:"frame_count,omitempty"`
	Error         string    `json:"error,omitempty"`
}

type ProcessRequest struct {
	FileID string `json:"file_id" binding:"required"`
}

var (
	redisClient *redis.Client
	config      Config
)

func main() {
	config = loadConfig()
	
	// Initialize Redis connection
	redisClient = initRedis()
	defer redisClient.Close()

	// Create processing directories
	os.MkdirAll("processing", 0755)
	os.MkdirAll("outputs", 0755)

	r := gin.Default()

	// CORS middleware
	r.Use(corsMiddleware())

	// Health check
	r.GET("/health", healthCheck)

	// Processing endpoints
	r.POST("/process", startProcessing)
	r.GET("/process/:id/status", getProcessingStatus)
	r.GET("/jobs", listJobs)
	r.DELETE("/process/:id", cancelProcessing)

	// Start background job processor
	go jobProcessor()

	fmt.Println("Processing Service started on port 8082")
	log.Fatal(r.Run(":8082"))
}

func loadConfig() Config {
	return Config{
		RedisURL:             getEnv("REDIS_URL", "redis://localhost:6379"),
		StorageServiceURL:    getEnv("STORAGE_SERVICE_URL", "http://localhost:8083"),
		NotificationServiceURL: getEnv("NOTIFICATION_SERVICE_URL", "http://localhost:8084"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func initRedis() *redis.Client {
	opts, err := redis.ParseURL(config.RedisURL)
	if err != nil {
		log.Fatalf("Failed to parse Redis URL: %v", err)
	}

	client := redis.NewClient(opts)
	
	// Test connection
	ctx := context.Background()
	_, err = client.Ping(ctx).Result()
	if err != nil {
		log.Fatalf("Failed to connect to Redis: %v", err)
	}

	fmt.Println("Connected to Redis")
	return client
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Request-ID")
		
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		
		c.Next()
	}
}

func healthCheck(c *gin.Context) {
	ctx := context.Background()
	
	// Test Redis connection
	_, err := redisClient.Ping(ctx).Result()
	redisHealthy := err == nil
	
	// Test FFmpeg availability
	_, err = exec.LookPath("ffmpeg")
	ffmpegAvailable := err == nil
	
	c.JSON(200, ProcessingResponse{
		Success: redisHealthy && ffmpegAvailable,
		Message: "Processing Service health check",
		Data: map[string]interface{}{
			"timestamp":        time.Now().Unix(),
			"redis_healthy":    redisHealthy,
			"ffmpeg_available": ffmpegAvailable,
			"version":          "1.0.0",
		},
	})
}

func startProcessing(c *gin.Context) {
	var req ProcessRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, ProcessingResponse{
			Success: false,
			Error:   "Invalid request: " + err.Error(),
		})
		return
	}

	// Create processing job
	jobID := generateJobID()
	job := ProcessingJob{
		ID:        jobID,
		FileID:    req.FileID,
		Status:    "pending",
		Progress:  0,
		CreatedAt: time.Now(),
	}

	// Store job in Redis
	ctx := context.Background()
	jobJSON, _ := json.Marshal(job)
	err := redisClient.Set(ctx, "job:"+jobID, jobJSON, 24*time.Hour).Err()
	if err != nil {
		c.JSON(500, ProcessingResponse{
			Success: false,
			Error:   "Failed to create job: " + err.Error(),
		})
		return
	}

	// Add job to processing queue
	err = redisClient.LPush(ctx, "processing_queue", jobID).Err()
	if err != nil {
		c.JSON(500, ProcessingResponse{
			Success: false,
			Error:   "Failed to queue job: " + err.Error(),
		})
		return
	}

	c.JSON(200, ProcessingResponse{
		Success: true,
		Message: "Processing job created successfully",
		Data: map[string]interface{}{
			"job_id":     jobID,
			"file_id":    req.FileID,
			"status":     "pending",
			"created_at": job.CreatedAt,
		},
	})
}

func getProcessingStatus(c *gin.Context) {
	jobID := c.Param("id")
	
	ctx := context.Background()
	jobJSON, err := redisClient.Get(ctx, "job:"+jobID).Result()
	if err == redis.Nil {
		c.JSON(404, ProcessingResponse{
			Success: false,
			Error:   "Job not found",
		})
		return
	} else if err != nil {
		c.JSON(500, ProcessingResponse{
			Success: false,
			Error:   "Failed to get job status: " + err.Error(),
		})
		return
	}

	var job ProcessingJob
	err = json.Unmarshal([]byte(jobJSON), &job)
	if err != nil {
		c.JSON(500, ProcessingResponse{
			Success: false,
			Error:   "Failed to parse job data: " + err.Error(),
		})
		return
	}

	c.JSON(200, ProcessingResponse{
		Success: true,
		Message: "Job status retrieved",
		Data:    job,
	})
}

func listJobs(c *gin.Context) {
	ctx := context.Background()
	
	keys, err := redisClient.Keys(ctx, "job:*").Result()
	if err != nil {
		c.JSON(500, ProcessingResponse{
			Success: false,
			Error:   "Failed to list jobs: " + err.Error(),
		})
		return
	}

	var jobs []ProcessingJob
	for _, key := range keys {
		jobJSON, err := redisClient.Get(ctx, key).Result()
		if err != nil {
			continue
		}

		var job ProcessingJob
		if err := json.Unmarshal([]byte(jobJSON), &job); err == nil {
			jobs = append(jobs, job)
		}
	}

	c.JSON(200, ProcessingResponse{
		Success: true,
		Message: "Jobs listed successfully",
		Data: map[string]interface{}{
			"jobs":  jobs,
			"total": len(jobs),
		},
	})
}

func cancelProcessing(c *gin.Context) {
	jobID := c.Param("id")
	
	ctx := context.Background()
	
	// Get job
	jobJSON, err := redisClient.Get(ctx, "job:"+jobID).Result()
	if err == redis.Nil {
		c.JSON(404, ProcessingResponse{
			Success: false,
			Error:   "Job not found",
		})
		return
	}

	var job ProcessingJob
	json.Unmarshal([]byte(jobJSON), &job)
	
	// Only allow cancellation of pending or processing jobs
	if job.Status != "pending" && job.Status != "processing" {
		c.JSON(400, ProcessingResponse{
			Success: false,
			Error:   "Cannot cancel job with status: " + job.Status,
		})
		return
	}

	// Update job status
	job.Status = "cancelled"
	now := time.Now()
	job.CompletedAt = &now
	
	jobJSON, _ = json.Marshal(job)
	redisClient.Set(ctx, "job:"+jobID, jobJSON, 24*time.Hour)

	c.JSON(200, ProcessingResponse{
		Success: true,
		Message: "Job cancelled successfully",
	})
}

func jobProcessor() {
	ctx := context.Background()
	
	for {
		// Block until a job is available
		result, err := redisClient.BRPop(ctx, 0, "processing_queue").Result()
		if err != nil {
			log.Printf("Error getting job from queue: %v", err)
			time.Sleep(5 * time.Second)
			continue
		}

		jobID := result[1]
		go processJob(jobID)
	}
}

func processJob(jobID string) {
	ctx := context.Background()
	
	// Get job details
	jobJSON, err := redisClient.Get(ctx, "job:"+jobID).Result()
	if err != nil {
		log.Printf("Failed to get job %s: %v", jobID, err)
		return
	}

	var job ProcessingJob
	err = json.Unmarshal([]byte(jobJSON), &job)
	if err != nil {
		log.Printf("Failed to parse job %s: %v", jobID, err)
		return
	}

	// Check if job was cancelled
	if job.Status == "cancelled" {
		return
	}

	// Update job status to processing
	job.Status = "processing"
	job.Progress = 0
	now := time.Now()
	job.StartedAt = &now
	
	updateJob(job)

	log.Printf("Starting processing job: %s for file: %s", jobID, job.FileID)

	// Find the input file
	inputFile, err := findInputFile(job.FileID)
	if err != nil {
		job.Status = "failed"
		job.Error = err.Error()
		job.CompletedAt = &now
		updateJob(job)
		return
	}

	// Process the video
	outputFile, frameCount, err := processVideo(inputFile, jobID, &job)
	if err != nil {
		job.Status = "failed"
		job.Error = err.Error()
		job.CompletedAt = &now
		updateJob(job)
		return
	}

	// Update job as completed
	job.Status = "completed"
	job.Progress = 100
	job.OutputFile = outputFile
	job.FrameCount = frameCount
	completedAt := time.Now()
	job.CompletedAt = &completedAt
	
	updateJob(job)

	log.Printf("Completed processing job: %s", jobID)

	// Notify notification service
	go notifyCompletion(job)
}

func findInputFile(fileID string) (string, error) {
	// Look for the file in uploads directory
	pattern := filepath.Join("../uploads", fileID+"_*")
	files, err := filepath.Glob(pattern)
	if err != nil || len(files) == 0 {
		return "", fmt.Errorf("input file not found for file ID: %s", fileID)
	}
	return files[0], nil
}

func processVideo(inputFile, jobID string, job *ProcessingJob) (string, int, error) {
	// Create temporary directory for frames
	tempDir := filepath.Join("processing", jobID)
	os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	// Update progress
	job.Progress = 10
	updateJob(*job)

	// Extract frames using FFmpeg
	framePattern := filepath.Join(tempDir, "frame_%04d.png")
	
	cmd := exec.Command("ffmpeg",
		"-i", inputFile,
		"-vf", "fps=1",
		"-y",
		framePattern,
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return "", 0, fmt.Errorf("ffmpeg error: %s\nOutput: %s", err.Error(), string(output))
	}

	// Update progress
	job.Progress = 70
	updateJob(*job)

	// Count frames
	frames, err := filepath.Glob(filepath.Join(tempDir, "*.png"))
	if err != nil || len(frames) == 0 {
		return "", 0, fmt.Errorf("no frames extracted from video")
	}

	// Create ZIP file
	zipFilename := fmt.Sprintf("frames_%s.zip", jobID)
	zipPath := filepath.Join("outputs", zipFilename)

	err = createZipFile(frames, zipPath)
	if err != nil {
		return "", 0, fmt.Errorf("failed to create ZIP file: %s", err.Error())
	}

	// Update progress
	job.Progress = 100
	updateJob(*job)

	return zipFilename, len(frames), nil
}

func createZipFile(files []string, zipPath string) error {
	// Implementation would be similar to the original main.go
	// For brevity, showing simplified version
	log.Printf("Creating ZIP file: %s with %d frames", zipPath, len(files))
	
	// In real implementation, use archive/zip package
	// to create the ZIP file properly
	
	return nil
}

func updateJob(job ProcessingJob) {
	ctx := context.Background()
	jobJSON, _ := json.Marshal(job)
	redisClient.Set(ctx, "job:"+job.ID, jobJSON, 24*time.Hour)
}

func generateJobID() string {
	return fmt.Sprintf("job_%d", time.Now().UnixNano())
}

func notifyCompletion(job ProcessingJob) {
	log.Printf("Notifying completion of job: %s", job.ID)
	
	// In real implementation:
	// - Make HTTP POST to notification service
	// - Include job details
	// - Handle errors and retries
} 