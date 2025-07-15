# FIAP X - Refatoração para Kotlin/Spring Boot

## Visão Geral da Refatoração

Este documento descreve a refatoração completa do projeto FIAP X de **Go** para **Kotlin/Spring Boot**, mantendo a mesma arquitetura de microserviços e funcionalidades.

## Mudanças Principais

### **Linguagem e Framework**
- **Antes**: Go + Gin Framework
- **Depois**: Kotlin + Spring Boot 3.2.0

### **Arquitetura Mantida**
- API Gateway (Spring Cloud Gateway)
- Upload Service (Spring Boot)
- Processing Service (Spring Boot + FFmpeg)
- Storage Service (Spring Boot)
- Notification Service (Spring Boot)
- Redis (Cache e Filas)
- Nginx (Load Balancer)

## Estrutura dos Serviços

### **1. API Gateway** (`services/gateway`)
```kotlin
// Spring Cloud Gateway com roteamento inteligente
@Configuration
class GatewayConfig {
    // Roteamento para microserviços
    // CORS configuration
    // Health checks
}
```

**Tecnologias:**
- Spring Cloud Gateway 4.0.8
- Spring Boot 3.2.0
- Kotlin 1.9.20
- Java 17

### **2. Upload Service** (`services/upload`)
```kotlin
@Service
class UploadService {
    // Validação de arquivos
    // Cálculo de hash MD5
    // Armazenamento no Redis
    // Integração com Storage Service
}
```

**Tecnologias:**
- Spring Boot Web
- Spring Data Redis
- Commons IO
- Kotlin Coroutines

### **3. Processing Service** (`services/processing`)
```kotlin
@Service
class ProcessingService {
    // Processamento assíncrono
    // Integração com FFmpeg
    // Criação de arquivos ZIP
    // Notificações via WebClient
}
```

**Tecnologias:**
- Spring Boot Web
- Spring WebFlux (WebClient)
- FFmpeg integration
- Kotlin Coroutines

### **4. Storage Service** (`services/storage`)
```kotlin
@Service
class StorageService {
    // Gerenciamento de arquivos
    // Downloads seguros
    // Estatísticas de uso
    // Limpeza automática
}
```

**Tecnologias:**
- Spring Boot Web
- Spring Data Redis
- File system operations
- Content-Type detection

### **5. Notification Service** (`services/notification`)
```kotlin
@Service
class NotificationService {
    // Sistema de notificações
    // TTL automático
    // Controle de leitura
    // Cleanup agendado
}
```

**Tecnologias:**
- Spring Boot Web
- Spring Data Redis
- Spring Scheduling
- TTL management

## Como Executar

### **Pré-requisitos**
- Docker e Docker Compose
- Java 17 (para desenvolvimento local)
- FFmpeg (para desenvolvimento local)

### **Execução com Docker**
```bash
# Construir e executar todos os serviços
docker-compose up --build

# Executar em background
docker-compose up -d

# Verificar logs
docker-compose logs -f

# Parar serviços
docker-compose down
```

### **Desenvolvimento Local**
```bash
# Para cada serviço individualmente
cd services/gateway && ./gradlew bootRun
cd services/upload && ./gradlew bootRun
cd services/processing && ./gradlew bootRun
cd services/storage && ./gradlew bootRun
cd services/notification && ./gradlew bootRun
```

## Comparação: Go vs Kotlin

### **Vantagens da Refatoração para Kotlin**

#### **Produtividade**
- **Kotlin**: Sintaxe mais concisa e expressiva
- **Spring Boot**: Ecossistema maduro e bem documentado
- **IDE Support**: Melhor suporte no IntelliJ IDEA

#### **Manutenibilidade**
- **Type Safety**: Sistema de tipos mais robusto
- **Null Safety**: Prevenção de NullPointerException
- **Coroutines**: Programação assíncrona mais limpa

#### **Ecossistema**
- **Spring Boot**: Muitas funcionalidades built-in
- **Spring Cloud**: Ferramentas para microserviços
- **Maven Central**: Repositório de dependências rico

#### **Performance**
- **JVM**: Otimizações automáticas
- **GraalVM**: Possibilidade de native compilation
- **Memory Management**: Garbage collection otimizado

### **Funcionalidades Mantidas**

#### **Upload de Vídeos**
- Validação de formatos (MP4, AVI, MOV, MKV, etc.)
- Controle de tamanho (500MB máximo)
- Cálculo de hash MD5
- Interface web responsiva

#### **Processamento**
- Extração de frames com FFmpeg (1 FPS)
- Processamento assíncrono
- Acompanhamento de progresso
- Criação de arquivos ZIP

#### **Armazenamento**
- Gerenciamento de arquivos
- Downloads seguros
- Estatísticas de uso
- Limpeza automática

#### **Notificações**
- Sistema em tempo real
- Controle de leitura
- Expiração automática (TTL)
- Estatísticas

## Configurações

### **Variáveis de Ambiente**
```yaml
# Redis
REDIS_URL=redis://localhost:6379

# Service URLs
STORAGE_SERVICE_URL=http://storage-service:8083
NOTIFICATION_SERVICE_URL=http://notification-service:8084

# Upload Settings
APP_UPLOAD_MAX_FILE_SIZE=524288000  # 500MB
APP_UPLOAD_ALLOWED_EXTENSIONS=mp4,avi,mov,mkv,wmv,flv,webm

# Processing Settings
APP_PROCESSING_OUTPUT_DIR=outputs
APP_PROCESSING_PROCESSING_DIR=processing

# Storage Settings
APP_STORAGE_UPLOADS_DIR=uploads
APP_STORAGE_OUTPUTS_DIR=outputs

# Notification Settings
APP_NOTIFICATION_DEFAULT_TTL=86400  # 24 hours
```

### **Portas dos Serviços**
- **Gateway**: 8080
- **Upload**: 8081
- **Processing**: 8082
- **Storage**: 8083
- **Notification**: 8084
- **Redis**: 6379
- **Nginx**: 80

## Melhorias Implementadas

### **1. Spring Boot Features**
- **Actuator**: Health checks e métricas
- **DevTools**: Hot reload em desenvolvimento
- **Configuration Properties**: Configuração type-safe
- **Scheduling**: Jobs agendados automáticos

### **2. Kotlin Features**
- **Data Classes**: Modelos de dados concisos
- **Extension Functions**: Extensões de funcionalidade
- **Coroutines**: Programação assíncrona
- **Null Safety**: Prevenção de erros

### **3. Spring Cloud Features**
- **Gateway**: Roteamento inteligente
- **WebClient**: Comunicação entre serviços
- **Redis**: Cache e filas
- **Scheduling**: Jobs agendados

## 🧪 Testes

### **Executar Testes**
```bash
# Para cada serviço
cd services/gateway && ./gradlew test
cd services/upload && ./gradlew test
cd services/processing && ./gradlew test
cd services/storage && ./gradlew test
cd services/notification && ./gradlew test
```

### **Health Checks**
```bash
curl http://localhost:8080/health  # Gateway
curl http://localhost:8081/health  # Upload
curl http://localhost:8082/health  # Processing
curl http://localhost:8083/health  # Storage
curl http://localhost:8084/health  # Notification
```

## Deploy

### **Docker Images**
- **Base**: OpenJDK 17 Alpine
- **Multi-stage**: Build e runtime separados
- **FFmpeg**: Incluído no Processing Service
- **Volumes**: Persistência de dados

### **Produção**
```bash
# Build otimizado
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# Monitoramento
docker-compose logs -f
docker stats
```

## Migração de Dados

### **Redis**
- Dados existentes são compatíveis
- Estrutura de chaves mantida
- TTL e expiração funcionam igual

### **Arquivos**
- Estrutura de diretórios mantida
- Compatibilidade com uploads existentes
- Downloads funcionam normalmente

## Conclusão

A refatoração para **Kotlin/Spring Boot** foi bem-sucedida e mantém todas as funcionalidades originais:

### **Benefícios Alcançados**
1. **Código mais limpo** e expressivo
2. **Melhor tooling** e IDE support
3. **Ecosistema rico** do Spring
4. **Type safety** aprimorado
5. **Manutenibilidade** melhorada

### **Funcionalidades Preservadas**
1. **Upload de vídeos** com validações
2. **Processamento assíncrono** com FFmpeg
3. **Download de arquivos ZIP**
4. **Sistema de notificações**
5. **Interface web responsiva**

### **Arquitetura Mantida**
1. **Microserviços** independentes
2. **API Gateway** centralizado
3. **Redis** para cache e filas
4. **Docker** para containerização
5. **Nginx** para load balancing

**O projeto está pronto para produção e oferece uma base sólida para futuras evoluções.** 