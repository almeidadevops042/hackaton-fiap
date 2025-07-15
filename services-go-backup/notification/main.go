package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/go-redis/redis/v8"
	"golang.org/x/net/context"
)

type Config struct {
	RedisURL string
}

type NotificationResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
}

type Notification struct {
	ID        string    `json:"id"`
	Type      string    `json:"type"`      // info, success, warning, error
	Title     string    `json:"title"`
	Message   string    `json:"message"`
	Data      interface{} `json:"data,omitempty"`
	Read      bool      `json:"read"`
	CreatedAt time.Time `json:"created_at"`
	ExpiresAt *time.Time `json:"expires_at,omitempty"`
}

type CreateNotificationRequest struct {
	Type    string      `json:"type" binding:"required"`
	Title   string      `json:"title" binding:"required"`
	Message string      `json:"message" binding:"required"`
	Data    interface{} `json:"data,omitempty"`
	TTL     int         `json:"ttl,omitempty"` // Time to live in seconds
}

type MarkReadRequest struct {
	NotificationIDs []string `json:"notification_ids" binding:"required"`
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

	r := gin.Default()

	// CORS middleware
	r.Use(corsMiddleware())

	// Health check
	r.GET("/health", healthCheck)

	// Notification endpoints
	r.GET("/notifications", getNotifications)
	r.POST("/notifications", createNotification)
	r.GET("/notifications/:id", getNotification)
	r.DELETE("/notifications/:id", deleteNotification)
	r.POST("/notifications/mark-read", markNotificationsAsRead)
	r.POST("/notifications/mark-all-read", markAllNotificationsAsRead)
	r.DELETE("/notifications", deleteAllNotifications)

	// Stats endpoints
	r.GET("/notifications/stats", getNotificationStats)
	r.GET("/notifications/unread-count", getUnreadCount)

	// Cleanup job
	go notificationCleanupJob()

	fmt.Println("Notification Service started on port 8084")
	log.Fatal(r.Run(":8084"))
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
	
	// Get notification stats
	totalNotifications, _ := redisClient.SCard(ctx, "notifications:all").Result()
	unreadNotifications, _ := redisClient.SCard(ctx, "notifications:unread").Result()
	
	c.JSON(200, NotificationResponse{
		Success: redisHealthy,
		Message: "Notification Service health check",
		Data: map[string]interface{}{
			"timestamp":            time.Now().Unix(),
			"redis_healthy":        redisHealthy,
			"total_notifications":  totalNotifications,
			"unread_notifications": unreadNotifications,
			"version":              "1.0.0",
		},
	})
}

func getNotifications(c *gin.Context) {
	ctx := context.Background()
	
	// Get all notification IDs
	notificationIDs, err := redisClient.SMembers(ctx, "notifications:all").Result()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to get notifications: " + err.Error(),
		})
		return
	}

	var notifications []Notification
	for _, id := range notificationIDs {
		notificationJSON, err := redisClient.Get(ctx, "notification:"+id).Result()
		if err != nil {
			continue
		}

		var notification Notification
		if err := json.Unmarshal([]byte(notificationJSON), &notification); err == nil {
			notifications = append(notifications, notification)
		}
	}

	// Sort by creation date (newest first)
	// In production, you might want to use a more efficient sorting mechanism
	
	c.JSON(200, NotificationResponse{
		Success: true,
		Message: "Notifications retrieved successfully",
		Data: map[string]interface{}{
			"notifications": notifications,
			"total":         len(notifications),
		},
	})
}

func createNotification(c *gin.Context) {
	var req CreateNotificationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, NotificationResponse{
			Success: false,
			Error:   "Invalid request: " + err.Error(),
		})
		return
	}

	// Generate notification ID
	notificationID := generateNotificationID()
	
	// Create notification
	notification := Notification{
		ID:        notificationID,
		Type:      req.Type,
		Title:     req.Title,
		Message:   req.Message,
		Data:      req.Data,
		Read:      false,
		CreatedAt: time.Now(),
	}

	// Set expiration if TTL is provided
	if req.TTL > 0 {
		expiresAt := time.Now().Add(time.Duration(req.TTL) * time.Second)
		notification.ExpiresAt = &expiresAt
	}

	// Store notification in Redis
	ctx := context.Background()
	notificationJSON, _ := json.Marshal(notification)
	
	// Set TTL if provided
	var ttl time.Duration
	if req.TTL > 0 {
		ttl = time.Duration(req.TTL) * time.Second
	} else {
		ttl = 24 * time.Hour // Default 24 hours
	}
	
	err := redisClient.Set(ctx, "notification:"+notificationID, notificationJSON, ttl).Err()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to create notification: " + err.Error(),
		})
		return
	}

	// Add to notification sets
	redisClient.SAdd(ctx, "notifications:all", notificationID)
	redisClient.SAdd(ctx, "notifications:unread", notificationID)
	
	// Set expiration for the sets as well
	redisClient.Expire(ctx, "notifications:all", ttl)
	redisClient.Expire(ctx, "notifications:unread", ttl)

	log.Printf("Created notification: %s - %s", req.Type, req.Title)

	c.JSON(201, NotificationResponse{
		Success: true,
		Message: "Notification created successfully",
		Data:    notification,
	})
}

func getNotification(c *gin.Context) {
	notificationID := c.Param("id")
	
	ctx := context.Background()
	notificationJSON, err := redisClient.Get(ctx, "notification:"+notificationID).Result()
	if err == redis.Nil {
		c.JSON(404, NotificationResponse{
			Success: false,
			Error:   "Notification not found",
		})
		return
	} else if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to get notification: " + err.Error(),
		})
		return
	}

	var notification Notification
	err = json.Unmarshal([]byte(notificationJSON), &notification)
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to parse notification: " + err.Error(),
		})
		return
	}

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: "Notification retrieved successfully",
		Data:    notification,
	})
}

func deleteNotification(c *gin.Context) {
	notificationID := c.Param("id")
	
	ctx := context.Background()
	
	// Remove from Redis
	err := redisClient.Del(ctx, "notification:"+notificationID).Err()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to delete notification: " + err.Error(),
		})
		return
	}

	// Remove from sets
	redisClient.SRem(ctx, "notifications:all", notificationID)
	redisClient.SRem(ctx, "notifications:unread", notificationID)

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: "Notification deleted successfully",
	})
}

func markNotificationsAsRead(c *gin.Context) {
	var req MarkReadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, NotificationResponse{
			Success: false,
			Error:   "Invalid request: " + err.Error(),
		})
		return
	}

	ctx := context.Background()
	var updatedCount int

	for _, notificationID := range req.NotificationIDs {
		// Get notification
		notificationJSON, err := redisClient.Get(ctx, "notification:"+notificationID).Result()
		if err != nil {
			continue
		}

		var notification Notification
		if err := json.Unmarshal([]byte(notificationJSON), &notification); err != nil {
			continue
		}

		// Mark as read
		notification.Read = true
		
		// Update in Redis
		updatedJSON, _ := json.Marshal(notification)
		redisClient.Set(ctx, "notification:"+notificationID, updatedJSON, 24*time.Hour)
		
		// Remove from unread set
		redisClient.SRem(ctx, "notifications:unread", notificationID)
		
		updatedCount++
	}

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: fmt.Sprintf("Marked %d notifications as read", updatedCount),
		Data: map[string]interface{}{
			"updated_count": updatedCount,
		},
	})
}

func markAllNotificationsAsRead(c *gin.Context) {
	ctx := context.Background()
	
	// Get all unread notification IDs
	unreadIDs, err := redisClient.SMembers(ctx, "notifications:unread").Result()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to get unread notifications: " + err.Error(),
		})
		return
	}

	var updatedCount int
	for _, notificationID := range unreadIDs {
		// Get notification
		notificationJSON, err := redisClient.Get(ctx, "notification:"+notificationID).Result()
		if err != nil {
			continue
		}

		var notification Notification
		if err := json.Unmarshal([]byte(notificationJSON), &notification); err != nil {
			continue
		}

		// Mark as read
		notification.Read = true
		
		// Update in Redis
		updatedJSON, _ := json.Marshal(notification)
		redisClient.Set(ctx, "notification:"+notificationID, updatedJSON, 24*time.Hour)
		
		updatedCount++
	}

	// Clear unread set
	redisClient.Del(ctx, "notifications:unread")

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: fmt.Sprintf("Marked all %d notifications as read", updatedCount),
		Data: map[string]interface{}{
			"updated_count": updatedCount,
		},
	})
}

func deleteAllNotifications(c *gin.Context) {
	ctx := context.Background()
	
	// Get all notification IDs
	notificationIDs, err := redisClient.SMembers(ctx, "notifications:all").Result()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to get notifications: " + err.Error(),
		})
		return
	}

	// Delete all notifications
	for _, id := range notificationIDs {
		redisClient.Del(ctx, "notification:"+id)
	}

	// Clear sets
	redisClient.Del(ctx, "notifications:all")
	redisClient.Del(ctx, "notifications:unread")

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: fmt.Sprintf("Deleted %d notifications", len(notificationIDs)),
		Data: map[string]interface{}{
			"deleted_count": len(notificationIDs),
		},
	})
}

func getNotificationStats(c *gin.Context) {
	ctx := context.Background()
	
	totalNotifications, _ := redisClient.SCard(ctx, "notifications:all").Result()
	unreadNotifications, _ := redisClient.SCard(ctx, "notifications:unread").Result()
	readNotifications := totalNotifications - unreadNotifications

	// Get type distribution
	typeStats := make(map[string]int)
	notificationIDs, _ := redisClient.SMembers(ctx, "notifications:all").Result()
	
	for _, id := range notificationIDs {
		notificationJSON, err := redisClient.Get(ctx, "notification:"+id).Result()
		if err != nil {
			continue
		}

		var notification Notification
		if err := json.Unmarshal([]byte(notificationJSON), &notification); err == nil {
			typeStats[notification.Type]++
		}
	}

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: "Notification stats retrieved successfully",
		Data: map[string]interface{}{
			"total_notifications":  totalNotifications,
			"unread_notifications": unreadNotifications,
			"read_notifications":   readNotifications,
			"type_distribution":    typeStats,
		},
	})
}

func getUnreadCount(c *gin.Context) {
	ctx := context.Background()
	
	unreadCount, err := redisClient.SCard(ctx, "notifications:unread").Result()
	if err != nil {
		c.JSON(500, NotificationResponse{
			Success: false,
			Error:   "Failed to get unread count: " + err.Error(),
		})
		return
	}

	c.JSON(200, NotificationResponse{
		Success: true,
		Message: "Unread count retrieved successfully",
		Data: map[string]interface{}{
			"unread_count": unreadCount,
		},
	})
}

func generateNotificationID() string {
	return fmt.Sprintf("notif_%d", time.Now().UnixNano())
}

func notificationCleanupJob() {
	ticker := time.NewTicker(30 * time.Minute) // Run every 30 minutes
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			cleanupExpiredNotifications()
		}
	}
}

func cleanupExpiredNotifications() {
	ctx := context.Background()
	
	// Get all notification IDs
	notificationIDs, err := redisClient.SMembers(ctx, "notifications:all").Result()
	if err != nil {
		log.Printf("Failed to get notifications for cleanup: %v", err)
		return
	}

	var expiredCount int
	now := time.Now()

	for _, id := range notificationIDs {
		notificationJSON, err := redisClient.Get(ctx, "notification:"+id).Result()
		if err != nil {
			// Notification doesn't exist, remove from sets
			redisClient.SRem(ctx, "notifications:all", id)
			redisClient.SRem(ctx, "notifications:unread", id)
			expiredCount++
			continue
		}

		var notification Notification
		if err := json.Unmarshal([]byte(notificationJSON), &notification); err != nil {
			continue
		}

		// Check if notification has expired
		if notification.ExpiresAt != nil && now.After(*notification.ExpiresAt) {
			// Delete expired notification
			redisClient.Del(ctx, "notification:"+id)
			redisClient.SRem(ctx, "notifications:all", id)
			redisClient.SRem(ctx, "notifications:unread", id)
			expiredCount++
		}
	}

	if expiredCount > 0 {
		log.Printf("Cleaned up %d expired notifications", expiredCount)
	}
} 