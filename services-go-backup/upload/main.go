package main

import (
	"crypto/md5"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/go-redis/redis/v8"
	"golang.org/x/net/context"
)

type Config struct {
	RedisURL         string
	StorageServiceURL string
	MaxFileSize      int64
	AllowedExtensions []string
}

type UploadResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
}

type FileMetadata struct {
	ID        string    `json:"id"`
	Filename  string    `json:"filename"`
	Size      int64     `json:"size"`
	Hash      string    `json:"hash"`
	MimeType  string    `json:"mime_type"`
	UploadedAt time.Time `json:"uploaded_at"`
	Status    string    `json:"status"`
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

	// Create upload directory
	os.MkdirAll("uploads", 0755)

	r := gin.Default()

	// CORS middleware
	r.Use(corsMiddleware())

	// Health check
	r.GET("/health", healthCheck)

	// Upload endpoints
	r.POST("/upload", handleUpload)
	r.GET("/upload/:id/status", getUploadStatus)
	r.GET("/uploads", listUploads)
	r.DELETE("/upload/:id", deleteUpload)

	fmt.Println("Upload Service started on port 8081")
	log.Fatal(r.Run(":8081"))
}

func loadConfig() Config {
	return Config{
		RedisURL:         getEnv("REDIS_URL", "redis://localhost:6379"),
		StorageServiceURL: getEnv("STORAGE_SERVICE_URL", "http://localhost:8083"),
		MaxFileSize:      500 * 1024 * 1024, // 500MB
		AllowedExtensions: []string{".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm"},
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
	
	c.JSON(200, UploadResponse{
		Success: redisHealthy,
		Message: "Upload Service health check",
		Data: map[string]interface{}{
			"timestamp":     time.Now().Unix(),
			"redis_healthy": redisHealthy,
			"version":       "1.0.0",
		},
	})
}

func handleUpload(c *gin.Context) {
	// Check file size
	c.Request.ParseMultipartForm(config.MaxFileSize)
	
	file, header, err := c.Request.FormFile("video")
	if err != nil {
		c.JSON(400, UploadResponse{
			Success: false,
			Error:   "Failed to get file: " + err.Error(),
		})
		return
	}
	defer file.Close()

	// Validate file extension
	if !isValidVideoFile(header.Filename) {
		c.JSON(400, UploadResponse{
			Success: false,
			Error:   "Invalid file format. Allowed: " + strings.Join(config.AllowedExtensions, ", "),
		})
		return
	}

	// Validate file size
	if header.Size > config.MaxFileSize {
		c.JSON(400, UploadResponse{
			Success: false,
			Error:   fmt.Sprintf("File too large. Max size: %.2f MB", float64(config.MaxFileSize)/(1024*1024)),
		})
		return
	}

	// Generate unique file ID
	fileID := generateFileID()
	timestamp := time.Now().Format("20060102_150405")
	filename := fmt.Sprintf("%s_%s_%s", fileID, timestamp, header.Filename)
	filepath := filepath.Join("uploads", filename)

	// Calculate file hash
	hash, err := calculateFileHash(file)
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to calculate file hash: " + err.Error(),
		})
		return
	}

	// Reset file pointer
	file.Seek(0, 0)

	// Save file
	out, err := os.Create(filepath)
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to save file: " + err.Error(),
		})
		return
	}
	defer out.Close()

	_, err = io.Copy(out, file)
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to save file: " + err.Error(),
		})
		return
	}

	// Create file metadata
	metadata := FileMetadata{
		ID:        fileID,
		Filename:  header.Filename,
		Size:      header.Size,
		Hash:      hash,
		MimeType:  header.Header.Get("Content-Type"),
		UploadedAt: time.Now(),
		Status:    "uploaded",
	}

	// Store metadata in Redis
	ctx := context.Background()
	metadataJSON, _ := json.Marshal(metadata)
	err = redisClient.Set(ctx, "upload:"+fileID, metadataJSON, 24*time.Hour).Err()
	if err != nil {
		log.Printf("Failed to store metadata in Redis: %v", err)
	}

	// Notify storage service
	go notifyStorageService(fileID, filepath, metadata)

	c.JSON(200, UploadResponse{
		Success: true,
		Message: "File uploaded successfully",
		Data: map[string]interface{}{
			"file_id":     fileID,
			"filename":    header.Filename,
			"size":        header.Size,
			"hash":        hash,
			"uploaded_at": metadata.UploadedAt,
		},
	})
}

func getUploadStatus(c *gin.Context) {
	fileID := c.Param("id")
	
	ctx := context.Background()
	metadataJSON, err := redisClient.Get(ctx, "upload:"+fileID).Result()
	if err == redis.Nil {
		c.JSON(404, UploadResponse{
			Success: false,
			Error:   "Upload not found",
		})
		return
	} else if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to get upload status: " + err.Error(),
		})
		return
	}

	var metadata FileMetadata
	err = json.Unmarshal([]byte(metadataJSON), &metadata)
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to parse metadata: " + err.Error(),
		})
		return
	}

	c.JSON(200, UploadResponse{
		Success: true,
		Message: "Upload status retrieved",
		Data:    metadata,
	})
}

func listUploads(c *gin.Context) {
	ctx := context.Background()
	
	keys, err := redisClient.Keys(ctx, "upload:*").Result()
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to list uploads: " + err.Error(),
		})
		return
	}

	var uploads []FileMetadata
	for _, key := range keys {
		metadataJSON, err := redisClient.Get(ctx, key).Result()
		if err != nil {
			continue
		}

		var metadata FileMetadata
		if err := json.Unmarshal([]byte(metadataJSON), &metadata); err == nil {
			uploads = append(uploads, metadata)
		}
	}

	c.JSON(200, UploadResponse{
		Success: true,
		Message: "Uploads listed successfully",
		Data: map[string]interface{}{
			"uploads": uploads,
			"total":   len(uploads),
		},
	})
}

func deleteUpload(c *gin.Context) {
	fileID := c.Param("id")
	
	ctx := context.Background()
	
	// Get metadata first
	metadataJSON, err := redisClient.Get(ctx, "upload:"+fileID).Result()
	if err == redis.Nil {
		c.JSON(404, UploadResponse{
			Success: false,
			Error:   "Upload not found",
		})
		return
	}

	// Delete from Redis
	err = redisClient.Del(ctx, "upload:"+fileID).Err()
	if err != nil {
		c.JSON(500, UploadResponse{
			Success: false,
			Error:   "Failed to delete upload metadata: " + err.Error(),
		})
		return
	}

	// Delete physical file
	var metadata FileMetadata
	json.Unmarshal([]byte(metadataJSON), &metadata)
	
	// Find and delete the physical file
	files, _ := filepath.Glob(filepath.Join("uploads", fileID+"_*"))
	for _, file := range files {
		os.Remove(file)
	}

	c.JSON(200, UploadResponse{
		Success: true,
		Message: "Upload deleted successfully",
	})
}

func isValidVideoFile(filename string) bool {
	ext := strings.ToLower(filepath.Ext(filename))
	
	for _, validExt := range config.AllowedExtensions {
		if ext == validExt {
			return true
		}
	}
	return false
}

func generateFileID() string {
	return fmt.Sprintf("file_%d", time.Now().UnixNano())
}

func calculateFileHash(file io.Reader) (string, error) {
	hash := md5.New()
	_, err := io.Copy(hash, file)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%x", hash.Sum(nil)), nil
}

func notifyStorageService(fileID, filePath string, metadata FileMetadata) {
	// This would normally make an HTTP request to the storage service
	// For now, we'll just log it
	log.Printf("Notifying storage service about file: %s at %s", fileID, filePath)
	
	// In a real implementation:
	// - Make HTTP POST to storage service
	// - Include file metadata
	// - Handle errors and retries
} 