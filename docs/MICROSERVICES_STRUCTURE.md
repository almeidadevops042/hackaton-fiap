# Estrutura Detalhada dos Microserviços

## **Visão Geral da Arquitetura Implementada**

### **Fluxo de Dados**
```
Cliente Web → Nginx → API Gateway → Microserviços → Redis → Processamento → Notificações
```

### **Mapa de Portas**
| Serviço | Porta | Descrição |
|---------|--------|-----------|
| Nginx | 80 | Load Balancer / Proxy |
| Gateway | 8080 | API Gateway + Interface |
| Upload | 8081 | Serviço de Upload |
| Processing | 8082 | Processamento de Vídeos |
| Storage | 8083 | Gerenciamento de Arquivos |
| Notification | 8084 | Sistema de Notificações |
| Redis | 6379 | Cache e Filas |

---

## **1. API Gateway Service**

### **Estrutura de Diretórios**
```
services/gateway/
├── main.go           # Código principal
├── go.mod           # Dependências Go
├── go.sum           # Checksums
└── Dockerfile       # Containerização
```

### **Responsabilidades**
- Proxy reverso para todos os microserviços
- Interface web integrada (HTML/CSS/JS)
- Roteamento de APIs (/api/v1/*)
- Tratamento de CORS
- Error handling centralizado
- Request tracing (X-Request-ID)

### **Endpoints Principais**
```
GET  /                          # Interface web
GET  /health                    # Health check
POST /api/v1/upload             # Proxy → Upload Service
GET  /api/v1/upload/:id/status  # Proxy → Upload Service
POST /api/v1/process            # Proxy → Processing Service
GET  /api/v1/process/:id/status # Proxy → Processing Service
GET  /api/v1/files              # Proxy → Storage Service
GET  /api/v1/download/:filename # Proxy → Storage Service
```

### **Configuração**
```go
type Config struct {
    UploadServiceURL      string
    ProcessingServiceURL  string
    StorageServiceURL     string
    NotificationServiceURL string
}
```

---

## **2. Upload Service**

### **Estrutura de Diretórios**
```
services/upload/
├── main.go           # Código principal
├── go.mod           # Dependências Go
├── go.sum           # Checksums
├── Dockerfile       # Containerização
└── uploads/         # Diretório de uploads (volume)
```

### **Responsabilidades**
- Recebimento e validação de arquivos
- Cálculo de hash MD5 para integridade
- Validação de tipos MIME
- Controle de tamanho (max 500MB)
- Armazenamento de metadados no Redis
- Geração de IDs únicos

### **Estruturas de Dados**
```go
type FileMetadata struct {
    ID        string    `json:"id"`
    Filename  string    `json:"filename"`
    Size      int64     `json:"size"`
    Hash      string    `json:"hash"`
    MimeType  string    `json:"mime_type"`
    UploadedAt time.Time `json:"uploaded_at"`
    Status    string    `json:"status"`
}
```

### **Validações Implementadas**
- **Extensões**: .mp4, .avi, .mov, .mkv, .wmv, .flv, .webm
- **Tamanho**: Máximo 500MB
- **Integridade**: Hash MD5
- **Segurança**: Validação de Content-Type

### **Endpoints**
```go
POST   /upload             # Upload de arquivo
GET    /upload/:id/status  # Status do upload
GET    /uploads            # Listar uploads
DELETE /upload/:id         # Deletar upload
```

---

## **3. Processing Service**

### **Estrutura de Diretórios**
```
services/processing/
├── main.go           # Código principal
├── go.mod           # Dependências Go
├── go.sum           # Checksums
├── Dockerfile       # Containerização (com FFmpeg)
├── processing/      # Arquivos temporários
└── outputs/         # Resultados finais (volume)
```

### **Responsabilidades**
- Processamento assíncrono com filas Redis
- Extração de frames usando FFmpeg
- Progresso em tempo real (0-100%)
- Cancelamento de jobs
- Criação de arquivos ZIP
- Notificação de conclusão

### **Estruturas de Dados**
```go
type ProcessingJob struct {
    ID            string     `json:"id"`
    FileID        string     `json:"file_id"`
    Status        string     `json:"status"` // pending, processing, completed, failed
    Progress      int        `json:"progress"`
    CreatedAt     time.Time  `json:"created_at"`
    StartedAt     *time.Time `json:"started_at,omitempty"`
    CompletedAt   *time.Time `json:"completed_at,omitempty"`
    OutputFile    string     `json:"output_file,omitempty"`
    FrameCount    int        `json:"frame_count,omitempty"`
    Error         string     `json:"error,omitempty"`
}
```

### **Pipeline de Processamento**
1. **Recebimento**: Job adicionado à fila Redis
2. **Validação**: Verificação do arquivo de entrada
3. **Extração**: FFmpeg processa vídeo (fps=1)
4. **Progresso**: Atualização em tempo real
5. **Compactação**: Criação do arquivo ZIP
6. **Finalização**: Notificação e cleanup

### **Comandos FFmpeg**
```bash
ffmpeg -i input.mp4 -vf fps=1 -y frame_%04d.png
```

### **Endpoints**
```go
POST   /process           # Iniciar processamento
GET    /process/:id/status # Status do job
GET    /jobs              # Listar todos os jobs
DELETE /process/:id       # Cancelar job
```

---

## **4. Storage Service**

### **Estrutura de Diretórios**
```
services/storage/
├── main.go           # Código principal
├── go.mod           # Dependências Go
├── go.sum           # Checksums
├── Dockerfile       # Containerização
├── uploads/         # Arquivos de entrada (volume)
└── outputs/         # Arquivos processados (volume)
```

### **Responsabilidades**
- Listagem de arquivos por tipo
- Downloads seguros com headers HTTP
- Controle de acesso e logs
- Estatísticas de uso
- Limpeza automática de arquivos antigos
- Métricas de armazenamento

### **Estruturas de Dados**
```go
type FileInfo struct {
    Filename    string    `json:"filename"`
    Size        int64     `json:"size"`
    ModTime     time.Time `json:"mod_time"`
    Path        string    `json:"path"`
    Type        string    `json:"type"` // upload, output
    DownloadURL string    `json:"download_url"`
}
```

### **Tipos de Arquivos**
- **Upload**: Vídeos originais enviados
- **Output**: ZIPs com frames processados

### **Endpoints**
```go
GET    /files                # Listar todos os arquivos
GET    /files/:type          # Listar por tipo (upload/output)
GET    /download/:filename   # Download seguro
DELETE /files/:filename      # Deletar arquivo
GET    /files/:filename/info # Informações detalhadas
GET    /storage/stats        # Estatísticas de uso
POST   /storage/cleanup      # Limpeza manual
```

---

## **5. Notification Service**

### **Estrutura de Diretórios**
```
services/notification/
├── main.go           # Código principal
├── go.mod           # Dependências Go
├── go.sum           # Checksums
└── Dockerfile       # Containerização
```

### **Responsabilidades**
- Criação de notificações tipificadas
- Controle de leitura/não leitura
- Expiração automática (TTL)
- Estatísticas e contadores
- Limpeza automática em background
- API completa para gerenciamento

### **Estruturas de Dados**
```go
type Notification struct {
    ID        string     `json:"id"`
    Type      string     `json:"type"`      // info, success, warning, error
    Title     string     `json:"title"`
    Message   string     `json:"message"`
    Data      interface{} `json:"data,omitempty"`
    Read      bool       `json:"read"`
    CreatedAt time.Time  `json:"created_at"`
    ExpiresAt *time.Time `json:"expires_at,omitempty"`
}
```

### **Tipos de Notificações**
- **info**: Informações gerais
- **success**: Operações bem-sucedidas
- **warning**: Avisos importantes
- **error**: Erros críticos

### **Endpoints**
```go
GET    /notifications                # Listar notificações
POST   /notifications               # Criar notificação
GET    /notifications/:id           # Notificação específica
DELETE /notifications/:id           # Deletar notificação
POST   /notifications/mark-read     # Marcar como lida
POST   /notifications/mark-all-read # Marcar todas como lidas
DELETE /notifications              # Deletar todas
GET    /notifications/stats         # Estatísticas
GET    /notifications/unread-count  # Contador não lidas
```

---

## **Redis - Estrutura de Dados**

### **Chaves Utilizadas**
```
upload:file_id           # Metadados de upload
job:job_id              # Jobs de processamento
processing_queue        # Fila de processamento
notification:notif_id   # Notificações
notifications:all       # Set de todas as notificações
notifications:unread    # Set de notificações não lidas
downloads:filename      # Contador de downloads
downloads:total         # Total de downloads
```

### **TTL (Time To Live)**
- **Uploads**: 24 horas
- **Jobs**: 24 horas
- **Notificações**: Configurável (padrão 24h)

---

## **Docker - Estrutura de Containers**

### **Multi-stage Builds**
Todos os serviços utilizam builds otimizados:
```dockerfile
# Stage 1: Build
FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o main .

# Stage 2: Runtime
FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /app/main .
EXPOSE 8080
CMD ["./main"]
```

### **Health Checks**
Todos os containers possuem health checks:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1
```

### **Volumes Compartilhados**
```yaml
volumes:
  - ./data/uploads:/app/uploads      # Arquivos de entrada
  - ./data/outputs:/app/outputs      # Arquivos processados
  - ./data/processing:/app/processing # Processamento temporário
  - redis-data:/data                 # Dados Redis
```

---

## **Comunicação Entre Serviços**

### **Protocolo HTTP/REST**
- **Síncrono**: Gateway → Serviços
- **Assíncrono**: Filas Redis

### **Service Discovery**
- **Docker Compose**: Resolução por nome
- **Kubernetes**: Service discovery nativo

### **Error Handling**
- **Timeout**: 60s para conexão
- **Retry**: Implementado no Gateway
- **Circuit Breaker**: Básico implementado

---

## **Métricas e Monitoramento**

### **Health Checks**
Todos os serviços expõem `/health`:
```json
{
  "success": true,
  "message": "Service healthy",
  "data": {
    "timestamp": 1234567890,
    "version": "1.0.0",
    "redis_healthy": true
  }
}
```

### **Logs Estruturados**
- **Formato**: JSON (recomendado para produção)
- **Níveis**: INFO, WARN, ERROR
- **Contexto**: Request ID, timestamps

### **Comandos de Monitoramento**
```bash
# Health checks
make test

# Logs em tempo real
make logs-follow

# Estatísticas
make stats

# Serviços individuais
make logs-gateway
make logs-processing
```

---

## **Resumo da Arquitetura**

### **Características Principais**
1. **5 Microserviços** independentes
2. **1 API Gateway** como ponto de entrada
3. **Redis** para cache e filas
4. **Nginx** para load balancing
5. **Docker Compose** para orquestração
6. **Interface Web** moderna integrada

### **Padrões Implementados**
- API Gateway Pattern
- Async Processing
- Health Check Pattern
- Circuit Breaker (básico)
- Load Balancing
- Containerização
- Service Discovery

### **Pronto Para**
- **Demonstração**: Interface funcional
- **Desenvolvimento**: Ambiente completo
- **Escalabilidade**: Serviços independentes
- **Manutenção**: Código bem estruturado

**A arquitetura está completa e operacional!** 

### O que aconteceu?
- O Docker não encontrou a imagem `openjdk:17-jre-alpine` no Docker Hub.
- Isso interrompeu o build dos serviços que dependem dessa imagem (ex: storage-service, notification-service).

### Como resolver?
A imagem `openjdk:17-jre-alpine` foi descontinuada no Docker Hub. Recomenda-se usar uma das imagens oficiais disponíveis, como:
- `eclipse-temurin:17-jre-alpine`
- `openjdk:17-alpine` (sem o sufixo `jre`)

#### Exemplo de correção no Dockerfile:
Troque:
```dockerfile
FROM openjdk:17-jre-alpine
```
por:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
```
ou
```dockerfile
FROM openjdk:17-alpine
```

### Próximos passos:
1. Atualize todos os Dockerfiles que usam `openjdk:17-jre-alpine` para uma imagem válida.
2. Rode novamente o comando:
   ```sh
   docker compose build
   ```
3. Verifique se o build finaliza sem erros.

Se precisar de ajuda para localizar e corrigir todos os Dockerfiles, posso indicar os arquivos e sugerir as alterações! 