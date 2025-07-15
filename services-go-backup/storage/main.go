package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/go-redis/redis/v8"
	"golang.org/x/net/context"
)

type Config struct {
	RedisURL string
}

type StorageResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
}

type FileInfo struct {
	Filename    string    `json:"filename"`
	Size        int64     `json:"size"`
	ModTime     time.Time `json:"mod_time"`
	Path        string    `json:"path"`
	Type        string    `json:"type"` // upload, output
	DownloadURL string    `json:"download_url"`
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

	// Ensure directories exist
	os.MkdirAll("uploads", 0755)
	os.MkdirAll("outputs", 0755)

	r := gin.Default()

	// CORS middleware
	r.Use(corsMiddleware())

	// Health check
	r.GET("/health", healthCheck)

	// File management endpoints
	r.GET("/files", listFiles)
	r.GET("/files/:type", listFilesByType)
	r.GET("/download/:filename", downloadFile)
	r.DELETE("/files/:filename", deleteFile)
	r.GET("/files/:filename/info", getFileInfo)

	// Storage management
	r.GET("/storage/stats", getStorageStats)
	r.POST("/storage/cleanup", cleanupOldFiles)

	fmt.Println("Storage Service started on port 8083")
	log.Fatal(r.Run(":8083"))
}

func loadConfig() Config {
	return Config{
		RedisURL: getEnv("REDIS_URL", "redis://localhost:6379"),
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
		c.Header("Access-Control-Allow-Methods", "GET,DELETE,POST,OPTIONS")
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
	
	// Check disk space
	diskStats := getDiskStats()
	
	c.JSON(200, StorageResponse{
		Success: redisHealthy,
		Message: "Storage Service health check",
		Data: map[string]interface{}{
			"timestamp":     time.Now().Unix(),
			"redis_healthy": redisHealthy,
			"disk_stats":    diskStats,
			"version":       "1.0.0",
		},
	})
}

func listFiles(c *gin.Context) {
	var allFiles []FileInfo
	
	// Get upload files
	uploadFiles, err := getFilesFromDirectory("uploads", "upload")
	if err != nil {
		log.Printf("Error getting upload files: %v", err)
	} else {
		allFiles = append(allFiles, uploadFiles...)
	}
	
	// Get output files
	outputFiles, err := getFilesFromDirectory("outputs", "output")
	if err != nil {
		log.Printf("Error getting output files: %v", err)
	} else {
		allFiles = append(allFiles, outputFiles...)
	}

	c.JSON(200, StorageResponse{
		Success: true,
		Message: "Files listed successfully",
		Data: map[string]interface{}{
			"files": allFiles,
			"total": len(allFiles),
		},
	})
}

func listFilesByType(c *gin.Context) {
	fileType := c.Param("type")
	
	var directory string
	switch fileType {
	case "upload", "uploads":
		directory = "uploads"
		fileType = "upload"
	case "output", "outputs":
		directory = "outputs"
		fileType = "output"
	default:
		c.JSON(400, StorageResponse{
			Success: false,
			Error:   "Invalid file type. Use 'upload' or 'output'",
		})
		return
	}

	files, err := getFilesFromDirectory(directory, fileType)
	if err != nil {
		c.JSON(500, StorageResponse{
			Success: false,
			Error:   "Failed to list files: " + err.Error(),
		})
		return
	}

	c.JSON(200, StorageResponse{
		Success: true,
		Message: fmt.Sprintf("%s files listed successfully", fileType),
		Data: map[string]interface{}{
			"files": files,
			"total": len(files),
			"type":  fileType,
		},
	})
}

func downloadFile(c *gin.Context) {
	filename := c.Param("filename")
	
	// Try to find file in both directories
	var filePath string
	var found bool
	
	// Check outputs first (processed files)
	outputPath := filepath.Join("outputs", filename)
	if _, err := os.Stat(outputPath); err == nil {
		filePath = outputPath
		found = true
	}
	
	// Check uploads if not found in outputs
	if !found {
		uploadPath := filepath.Join("uploads", filename)
		if _, err := os.Stat(uploadPath); err == nil {
			filePath = uploadPath
			found = true
		}
	}
	
	if !found {
		c.JSON(404, StorageResponse{
			Success: false,
			Error:   "File not found",
		})
		return
	}

	// Get file info
	fileInfo, err := os.Stat(filePath)
	if err != nil {
		c.JSON(500, StorageResponse{
			Success: false,
			Error:   "Failed to get file info: " + err.Error(),
		})
		return
	}

	// Set appropriate headers
	c.Header("Content-Description", "File Transfer")
	c.Header("Content-Transfer-Encoding", "binary")
	c.Header("Content-Disposition", "attachment; filename="+filename)
	c.Header("Content-Length", fmt.Sprintf("%d", fileInfo.Size()))
	
	// Set content type based on file extension
	ext := strings.ToLower(filepath.Ext(filename))
	switch ext {
	case ".zip":
		c.Header("Content-Type", "application/zip")
	case ".mp4":
		c.Header("Content-Type", "video/mp4")
	case ".avi":
		c.Header("Content-Type", "video/x-msvideo")
	case ".mov":
		c.Header("Content-Type", "video/quicktime")
	default:
		c.Header("Content-Type", "application/octet-stream")
	}

	// Log download
	log.Printf("File downloaded: %s (%d bytes)", filename, fileInfo.Size())
	
	// Update download stats in Redis
	ctx := context.Background()
	redisClient.Incr(ctx, "downloads:"+filename)
	redisClient.Incr(ctx, "downloads:total")

	c.File(filePath)
}

func deleteFile(c *gin.Context) {
	filename := c.Param("filename")
	
	var deletedPaths []string
	var errors []string
	
	// Try to delete from both directories
	paths := []string{
		filepath.Join("uploads", filename),
		filepath.Join("outputs", filename),
	}
	
	for _, path := range paths {
		if _, err := os.Stat(path); err == nil {
			if err := os.Remove(path); err == nil {
				deletedPaths = append(deletedPaths, path)
			} else {
				errors = append(errors, err.Error())
			}
		}
	}
	
	if len(deletedPaths) == 0 {
		c.JSON(404, StorageResponse{
			Success: false,
			Error:   "File not found",
		})
		return
	}
	
	// Log deletion
	log.Printf("File deleted: %s from paths: %v", filename, deletedPaths)

	result := map[string]interface{}{
		"deleted_paths": deletedPaths,
		"filename":      filename,
	}
	
	if len(errors) > 0 {
		result["errors"] = errors
	}

	c.JSON(200, StorageResponse{
		Success: true,
		Message: "File deleted successfully",
		Data:    result,
	})
}

func getFileInfo(c *gin.Context) {
	filename := c.Param("filename")
	
	// Try to find file in both directories
	var fileInfo FileInfo
	var found bool
	
	paths := map[string]string{
		"outputs": filepath.Join("outputs", filename),
		"uploads": filepath.Join("uploads", filename),
	}
	
	for fileType, path := range paths {
		if stat, err := os.Stat(path); err == nil {
			fileInfo = FileInfo{
				Filename:    filename,
				Size:        stat.Size(),
				ModTime:     stat.ModTime(),
				Path:        path,
				Type:        fileType[:len(fileType)-1], // Remove 's' from end
				DownloadURL: "/download/" + filename,
			}
			found = true
			break
		}
	}
	
	if !found {
		c.JSON(404, StorageResponse{
			Success: false,
			Error:   "File not found",
		})
		return
	}

	// Get download stats from Redis
	ctx := context.Background()
	downloads, _ := redisClient.Get(ctx, "downloads:"+filename).Int()
	
	c.JSON(200, StorageResponse{
		Success: true,
		Message: "File info retrieved successfully",
		Data: map[string]interface{}{
			"file_info": fileInfo,
			"downloads": downloads,
		},
	})
}

func getStorageStats(c *gin.Context) {
	uploadStats := getDirectoryStats("uploads")
	outputStats := getDirectoryStats("outputs")
	diskStats := getDiskStats()

	c.JSON(200, StorageResponse{
		Success: true,
		Message: "Storage stats retrieved successfully",
		Data: map[string]interface{}{
			"upload_files": uploadStats,
			"output_files": outputStats,
			"disk_usage":   diskStats,
			"total_files":  uploadStats["count"].(int) + outputStats["count"].(int),
			"total_size":   uploadStats["size"].(int64) + outputStats["size"].(int64),
		},
	})
}

func cleanupOldFiles(c *gin.Context) {
	// Default to files older than 24 hours
	maxAge := 24 * time.Hour
	
	var deletedFiles []string
	var totalSize int64
	
	directories := []string{"uploads", "outputs"}
	
	for _, dir := range directories {
		files, err := filepath.Glob(filepath.Join(dir, "*"))
		if err != nil {
			continue
		}
		
		for _, file := range files {
			stat, err := os.Stat(file)
			if err != nil {
				continue
			}
			
			if time.Since(stat.ModTime()) > maxAge {
				totalSize += stat.Size()
				if err := os.Remove(file); err == nil {
					deletedFiles = append(deletedFiles, filepath.Base(file))
				}
			}
		}
	}

	log.Printf("Cleanup completed: deleted %d files (%d bytes)", len(deletedFiles), totalSize)

	c.JSON(200, StorageResponse{
		Success: true,
		Message: "Cleanup completed successfully",
		Data: map[string]interface{}{
			"deleted_files": deletedFiles,
			"files_count":   len(deletedFiles),
			"bytes_freed":   totalSize,
		},
	})
}

func getFilesFromDirectory(directory, fileType string) ([]FileInfo, error) {
	var files []FileInfo
	
	err := filepath.Walk(directory, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		
		if !info.IsDir() {
			files = append(files, FileInfo{
				Filename:    info.Name(),
				Size:        info.Size(),
				ModTime:     info.ModTime(),
				Path:        path,
				Type:        fileType,
				DownloadURL: "/download/" + info.Name(),
			})
		}
		
		return nil
	})
	
	return files, err
}

func getDirectoryStats(directory string) map[string]interface{} {
	var totalSize int64
	var fileCount int
	
	filepath.Walk(directory, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		
		if !info.IsDir() {
			totalSize += info.Size()
			fileCount++
		}
		
		return nil
	})
	
	return map[string]interface{}{
		"count": fileCount,
		"size":  totalSize,
	}
}

func getDiskStats() map[string]interface{} {
	// Simple disk usage check (this would be more sophisticated in production)
	return map[string]interface{}{
		"available": "N/A", // Would use syscall or external library
		"used":      "N/A",
		"total":     "N/A",
	}
} 