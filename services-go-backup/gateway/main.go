package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

type Config struct {
	UploadServiceURL      string
	ProcessingServiceURL  string
	StorageServiceURL     string
	NotificationServiceURL string
}

type GatewayResponse struct {
	Success bool        `json:"success"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
}

func main() {
	config := loadConfig()
	
	r := gin.Default()

	// CORS middleware
	r.Use(corsMiddleware())
	
	// Health check
	r.GET("/health", healthCheck)
	
	// Static files (frontend)
	r.GET("/", serveIndex)
	
	// API routes
	api := r.Group("/api/v1")
	{
		// Upload routes
		api.POST("/upload", proxyToService(config.UploadServiceURL, "/upload"))
		api.GET("/upload/:id/status", proxyToService(config.UploadServiceURL, "/upload/:id/status"))
		
		// Processing routes
		api.POST("/process", proxyToService(config.ProcessingServiceURL, "/process"))
		api.GET("/process/:id/status", proxyToService(config.ProcessingServiceURL, "/process/:id/status"))
		
		// Storage routes
		api.GET("/files", proxyToService(config.StorageServiceURL, "/files"))
		api.GET("/download/:filename", proxyToService(config.StorageServiceURL, "/download/:filename"))
		api.DELETE("/files/:filename", proxyToService(config.StorageServiceURL, "/files/:filename"))
		
		// Notification routes
		api.GET("/notifications", proxyToService(config.NotificationServiceURL, "/notifications"))
		api.POST("/notifications/mark-read", proxyToService(config.NotificationServiceURL, "/notifications/mark-read"))
	}

	fmt.Println("API Gateway started on port 8080")
	log.Fatal(r.Run(":8080"))
}

func loadConfig() Config {
	return Config{
		UploadServiceURL:      getEnv("UPLOAD_SERVICE_URL", "http://localhost:8081"),
		ProcessingServiceURL:  getEnv("PROCESSING_SERVICE_URL", "http://localhost:8082"),
		StorageServiceURL:     getEnv("STORAGE_SERVICE_URL", "http://localhost:8083"),
		NotificationServiceURL: getEnv("NOTIFICATION_SERVICE_URL", "http://localhost:8084"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Request-ID")
		
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		
		c.Next()
	}
}

func healthCheck(c *gin.Context) {
	c.JSON(200, GatewayResponse{
		Success: true,
		Message: "API Gateway is healthy",
		Data: map[string]interface{}{
			"timestamp": time.Now().Unix(),
			"version":   "1.0.0",
		},
	})
}

func proxyToService(baseURL, path string) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Parse the target URL
		target, err := url.Parse(baseURL)
		if err != nil {
			c.JSON(500, GatewayResponse{
				Success: false,
				Error:   "Invalid service URL: " + err.Error(),
			})
			return
		}

		// Create reverse proxy
		proxy := httputil.NewSingleHostReverseProxy(target)
		
		// Modify the request
		originalDirector := proxy.Director
		proxy.Director = func(req *http.Request) {
			originalDirector(req)
			
			// Replace path parameters
			newPath := path
			for _, param := range c.Params {
				newPath = strings.Replace(newPath, ":"+param.Key, param.Value, -1)
			}
			req.URL.Path = newPath
			
			// Add request ID header for tracing
			req.Header.Set("X-Request-ID", generateRequestID())
			req.Header.Set("X-Gateway-Source", "api-gateway")
		}

		// Error handling
		proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
			log.Printf("Proxy error: %v", err)
			
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadGateway)
			
			response := GatewayResponse{
				Success: false,
				Error:   "Service temporarily unavailable",
			}
			json.NewEncoder(w).Encode(response)
		}

		proxy.ServeHTTP(c.Writer, c.Request)
	}
}

func generateRequestID() string {
	return fmt.Sprintf("req_%d", time.Now().UnixNano())
}

func serveIndex(c *gin.Context) {
	c.Header("Content-Type", "text/html")
	c.String(200, getIndexHTML())
}

func getIndexHTML() string {
	return `
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FIAP X - Microservices Video Processor</title>
    <style>
        body { 
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 0;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        .header {
            text-align: center;
            color: white;
            margin-bottom: 40px;
        }
        .header h1 {
            font-size: 2.5rem;
            margin-bottom: 10px;
        }
        .header p {
            font-size: 1.2rem;
            opacity: 0.9;
        }
        .services-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }
        .service-card {
            background: white;
            border-radius: 10px;
            padding: 30px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1);
            text-align: center;
            transition: transform 0.3s ease;
        }
        .service-card:hover {
            transform: translateY(-5px);
        }
        .service-icon {
            font-size: 3rem;
            margin-bottom: 20px;
        }
        .service-title {
            font-size: 1.5rem;
            font-weight: bold;
            margin-bottom: 10px;
            color: #333;
        }
        .service-description {
            color: #666;
            line-height: 1.6;
        }
        .upload-section {
            background: white;
            border-radius: 10px;
            padding: 40px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1);
        }
        .upload-form {
            border: 2px dashed #ddd;
            padding: 40px;
            text-align: center;
            border-radius: 10px;
            margin: 20px 0;
        }
        input[type="file"] {
            margin: 20px 0;
            padding: 10px;
        }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 30px;
            border: none;
            border-radius: 5px;
            cursor: pointer;
            font-size: 16px;
            font-weight: bold;
        }
        button:hover { 
            opacity: 0.9;
            transform: translateY(-2px);
        }
        .status {
            margin-top: 20px;
            padding: 15px;
            border-radius: 5px;
            display: none;
        }
        .success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .loading { background: #d1ecf1; color: #0c5460; border: 1px solid #bee5eb; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            		<h1>FIAP X</h1>
            <p>Arquitetura de Microserviços para Processamento de Vídeos</p>
        </div>
        
        <div class="services-grid">
            <div class="service-card">
                				<div class="service-icon">Gateway</div>
                <div class="service-title">API Gateway</div>
                <div class="service-description">
                    Ponto de entrada único que roteia requisições para os microserviços apropriados
                </div>
            </div>
            
            <div class="service-card">
                				<div class="service-icon">Upload</div>
                <div class="service-title">Upload Service</div>
                <div class="service-description">
                    Gerencia uploads de vídeos com validação e armazenamento seguro
                </div>
            </div>
            
            <div class="service-card">
                				<div class="service-icon">Processing</div>
                <div class="service-title">Processing Service</div>
                <div class="service-description">
                    Processa vídeos para extrair frames usando FFmpeg
                </div>
            </div>
            
            <div class="service-card">
                				<div class="service-icon">Storage</div>
                <div class="service-title">Storage Service</div>
                <div class="service-description">
                    Gerencia arquivos processados e disponibiliza downloads
                </div>
            </div>
            
            <div class="service-card">
                				<div class="service-icon">Notification</div>
                <div class="service-title">Notification Service</div>
                <div class="service-description">
                    Sistema de notificações em tempo real sobre o status dos processamentos
                </div>
            </div>
        </div>
        
        <div class="upload-section">
            			<h2 style="text-align: center; margin-bottom: 30px;">Processar Vídeo</h2>
            
            <form id="uploadForm" class="upload-form">
                <p><strong>Selecione um arquivo de vídeo:</strong></p>
                <input type="file" id="videoFile" accept="video/*" required>
                <br>
                <button type="submit">Processar Vídeo</button>
            </form>
            
            <div class="status" id="status"></div>
        </div>
    </div>

    <script>
        document.getElementById('uploadForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const fileInput = document.getElementById('videoFile');
            const file = fileInput.files[0];
            
            if (!file) {
                showStatus('Selecione um arquivo de vídeo!', 'error');
                return;
            }
            
            const formData = new FormData();
            formData.append('video', file);
            
            showStatus('⏳ Fazendo upload...', 'loading');
            
            try {
                // Step 1: Upload
                const uploadResponse = await fetch('/api/v1/upload', {
                    method: 'POST',
                    body: formData
                });
                
                const uploadResult = await uploadResponse.json();
                
                if (uploadResult.success) {
                    				showStatus('Upload concluído! Iniciando processamento...', 'loading');
                    
                    // Step 2: Process
                    const processResponse = await fetch('/api/v1/process', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            file_id: uploadResult.data.file_id
                        })
                    });
                    
                    const processResult = await processResponse.json();
                    
                    if (processResult.success) {
                        showStatus('🎉 Processamento iniciado! ID: ' + processResult.data.job_id, 'success');
                        pollJobStatus(processResult.data.job_id);
                    } else {
                        					showStatus('Erro no processamento: ' + processResult.error, 'error');
                    }
                } else {
                    					showStatus('Erro no upload: ' + uploadResult.error, 'error');
                }
            } catch (error) {
                				showStatus('Erro de conexão: ' + error.message, 'error');
            }
        });
        
        function showStatus(message, type) {
            const status = document.getElementById('status');
            status.innerHTML = message;
            status.className = 'status ' + type;
            status.style.display = 'block';
        }
        
        async function pollJobStatus(jobId) {
            try {
                const response = await fetch('/api/v1/process/' + jobId + '/status');
                const result = await response.json();
                
                if (result.success) {
                    const job = result.data;
                    
                    if (job.status === 'completed') {
                        					showStatus('Processamento concluído! ' + 
                                 '<a href="/api/v1/download/' + job.output_file + 
                                 '" style="color: #007bff;">Download ZIP</a>', 'success');
                    } else if (job.status === 'failed') {
                        					showStatus('Processamento falhou: ' + job.error, 'error');
                    } else {
                        showStatus('Status: ' + job.status + ' - ' + job.progress + '%', 'loading');
                        setTimeout(() => pollJobStatus(jobId), 2000);
                    }
                }
            } catch (error) {
                console.error('Error polling status:', error);
            }
        }
    </script>
</body>
</html>
	`
} 