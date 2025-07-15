#!/bin/bash

# FIAP X - Teste Regressivo de Ponta a Ponta (E2E)
# Este script testa todo o fluxo do sistema de processamento de vídeos

set -e  # Para o script se qualquer comando falhar

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configurações
BASE_URL="http://localhost:8080"
GATEWAY_URL="http://localhost:8080"
UPLOAD_URL="http://localhost:8081"
PROCESSING_URL="http://localhost:8082"
STORAGE_URL="http://localhost:8083"
NOTIFICATION_URL="http://localhost:8084"
AUTH_URL="http://localhost:8085"

# Variáveis para armazenar dados dos testes
AUTH_TOKEN=""
USER_ID=""
FILE_ID=""
JOB_ID=""
PROCESSED_FILE=""

# Função para log colorido
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

error() {
    echo -e "${RED}✗ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Função para aguardar serviço estar disponível
wait_for_service() {
    local service_name=$1
    local service_url=$2
    local max_attempts=30
    local attempt=1
    
    log "Aguardando serviço $service_name estar disponível..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$service_url/health" > /dev/null 2>&1; then
            success "Serviço $service_name está disponível"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    error "Serviço $service_name não está disponível após $max_attempts tentativas"
    return 1
}

# Função para fazer requisição HTTP e verificar resposta
http_request() {
    local method=$1
    local url=$2
    local data=$3
    local expected_status=$4
    local description=$5
    
    log "Testando: $description"
    
    local response
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $AUTH_TOKEN" \
            -d "$data")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
            -H "Authorization: Bearer $AUTH_TOKEN")
    fi
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" -eq "$expected_status" ]; then
        success "$description - Status: $http_code"
        echo "$body"
    else
        error "$description - Status esperado: $expected_status, recebido: $http_code"
        echo "Resposta: $body"
        return 1
    fi
}

# Função para testar health checks
test_health_checks() {
    log "=== Testando Health Checks ==="
    
    wait_for_service "Gateway" "$GATEWAY_URL"
    wait_for_service "Upload" "$UPLOAD_URL"
    wait_for_service "Processing" "$PROCESSING_URL"
    wait_for_service "Storage" "$STORAGE_URL"
    wait_for_service "Notification" "$NOTIFICATION_URL"
    wait_for_service "Auth" "$AUTH_URL"
    
    success "Todos os serviços estão saudáveis"
}

# Função para testar autenticação
test_authentication() {
    log "=== Testando Autenticação ==="
    
    # Teste 1: Registro de usuário
    local register_data='{
        "username": "testuser_e2e",
        "email": "test_e2e@example.com",
        "password": "password123"
    }'
    
    local register_response=$(http_request "POST" "$AUTH_URL/api/v1/auth/register" "$register_data" 200 "Registro de usuário")
    USER_ID=$(echo "$register_response" | jq -r '.data.id // empty')
    
    # Teste 2: Login
    local login_data='{
        "username": "testuser_e2e",
        "password": "password123"
    }'
    
    local login_response=$(http_request "POST" "$AUTH_URL/api/v1/auth/login" "$login_data" 200 "Login de usuário")
    AUTH_TOKEN=$(echo "$login_response" | jq -r '.token // empty')
    
    if [ -z "$AUTH_TOKEN" ]; then
        error "Token de autenticação não foi obtido"
        return 1
    fi
    
    success "Autenticação realizada com sucesso"
}

# Função para testar upload de arquivo
test_upload() {
    log "=== Testando Upload de Arquivo ==="
    
    # Criar arquivo de teste
    local test_file="test_video_e2e.mp4"
    echo "Test video content for E2E testing" > "$test_file"
    
    # Upload via multipart/form-data
    log "Fazendo upload do arquivo de teste..."
    
    local upload_response=$(curl -s -X POST "$UPLOAD_URL/api/v1/upload" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -F "video=@$test_file" \
        -F "metadata={\"description\":\"Test video for E2E\",\"tags\":[\"test\",\"e2e\"]}")
    
    local upload_success=$(echo "$upload_response" | jq -r '.success // false')
    
    if [ "$upload_success" = "true" ]; then
        FILE_ID=$(echo "$upload_response" | jq -r '.data.file_id // empty')
        success "Upload realizado com sucesso - File ID: $FILE_ID"
    else
        error "Falha no upload: $(echo "$upload_response" | jq -r '.error // .message // "Unknown error"')"
        return 1
    fi
    
    # Limpar arquivo temporário
    rm -f "$test_file"
}

# Função para testar processamento
test_processing() {
    log "=== Testando Processamento ==="
    
    if [ -z "$FILE_ID" ]; then
        error "File ID não disponível para processamento"
        return 1
    fi
    
    local process_data="{
        \"file_id\": \"$FILE_ID\",
        \"options\": {
            \"frame_rate\": 1,
            \"quality\": \"medium\",
            \"format\": \"jpg\"
        }
    }"
    
    local process_response=$(http_request "POST" "$PROCESSING_URL/api/v1/process" "$process_data" 200 "Iniciar processamento")
    JOB_ID=$(echo "$process_response" | jq -r '.data.job_id // empty')
    
    if [ -z "$JOB_ID" ]; then
        error "Job ID não foi obtido"
        return 1
    fi
    
    success "Processamento iniciado - Job ID: $JOB_ID"
    
    # Aguardar processamento completar
    log "Aguardando processamento completar..."
    local max_wait=60
    local wait_count=0
    
    while [ $wait_count -lt $max_wait ]; do
        local status_response=$(curl -s -X GET "$PROCESSING_URL/api/v1/process/$JOB_ID/status" \
            -H "Authorization: Bearer $AUTH_TOKEN")
        
        local job_status=$(echo "$status_response" | jq -r '.data.status // empty')
        local job_progress=$(echo "$status_response" | jq -r '.data.progress // 0')
        
        log "Status: $job_status, Progresso: ${job_progress}%"
        
        if [ "$job_status" = "completed" ]; then
            PROCESSED_FILE=$(echo "$status_response" | jq -r '.data.output_file // empty')
            success "Processamento concluído - Output: $PROCESSED_FILE"
            break
        elif [ "$job_status" = "failed" ]; then
            local error_msg=$(echo "$status_response" | jq -r '.data.error // "Unknown error"')
            error "Processamento falhou: $error_msg"
            return 1
        fi
        
        sleep 5
        wait_count=$((wait_count + 5))
    done
    
    if [ $wait_count -ge $max_wait ]; then
        error "Timeout aguardando processamento completar"
        return 1
    fi
}

# Função para testar storage e download
test_storage() {
    log "=== Testando Storage e Download ==="
    
    # Teste 1: Listar arquivos
    local list_response=$(http_request "GET" "$STORAGE_URL/api/v1/files" "" 200 "Listar arquivos")
    local file_count=$(echo "$list_response" | jq '.data | length')
    
    if [ "$file_count" -gt 0 ]; then
        success "Listagem de arquivos - $file_count arquivo(s) encontrado(s)"
    else
        warning "Nenhum arquivo encontrado na listagem"
    fi
    
    # Teste 2: Obter informações do arquivo processado
    if [ -n "$PROCESSED_FILE" ]; then
        local file_info_response=$(http_request "GET" "$STORAGE_URL/api/v1/files/$PROCESSED_FILE" "" 200 "Obter informações do arquivo")
        local file_size=$(echo "$file_info_response" | jq -r '.data.size // 0')
        
        if [ "$file_size" -gt 0 ]; then
            success "Informações do arquivo obtidas - Tamanho: $file_size bytes"
        else
            warning "Arquivo processado não encontrado ou vazio"
        fi
    fi
    
    # Teste 3: Download do arquivo (verificar se está acessível)
    if [ -n "$PROCESSED_FILE" ]; then
        local download_response=$(curl -s -I "$STORAGE_URL/api/v1/download/$PROCESSED_FILE" \
            -H "Authorization: Bearer $AUTH_TOKEN")
        
        local download_status=$(echo "$download_response" | head -n1 | cut -d' ' -f2)
        
        if [ "$download_status" = "200" ]; then
            success "Download do arquivo está acessível"
        else
            warning "Download do arquivo retornou status: $download_status"
        fi
    fi
}

# Função para testar notificações
test_notifications() {
    log "=== Testando Notificações ==="
    
    # Teste 1: Listar notificações do usuário
    local notifications_response=$(http_request "GET" "$NOTIFICATION_URL/api/v1/notifications/user/$USER_ID" "" 200 "Listar notificações do usuário")
    local notification_count=$(echo "$notifications_response" | jq '.data | length')
    
    if [ "$notification_count" -ge 0 ]; then
        success "Notificações listadas - $notification_count notificação(ões) encontrada(s)"
    else
        warning "Erro ao listar notificações"
    fi
    
    # Teste 2: Verificar notificações específicas do job
    if [ -n "$JOB_ID" ]; then
        local job_notifications_response=$(http_request "GET" "$NOTIFICATION_URL/api/v1/notifications/job/$JOB_ID" "" 200 "Listar notificações do job")
        local job_notification_count=$(echo "$job_notifications_response" | jq '.data | length')
        
        if [ "$job_notification_count" -ge 0 ]; then
            success "Notificações do job listadas - $job_notification_count notificação(ões)"
        else
            warning "Nenhuma notificação específica do job encontrada"
        fi
    fi
}

# Função para testar gateway
test_gateway() {
    log "=== Testando Gateway ==="
    
    # Teste 1: Health check via gateway
    local gateway_health=$(http_request "GET" "$GATEWAY_URL/health" "" 200 "Health check via gateway")
    success "Gateway está funcionando"
    
    # Teste 2: Roteamento para upload service
    local upload_route_response=$(curl -s -X GET "$GATEWAY_URL/api/v1/upload/health" \
        -H "Authorization: Bearer $AUTH_TOKEN")
    
    if echo "$upload_route_response" | grep -q "UP\|healthy"; then
        success "Roteamento para upload service funcionando"
    else
        warning "Roteamento para upload service pode ter problemas"
    fi
    
    # Teste 3: Roteamento para processing service
    local processing_route_response=$(curl -s -X GET "$GATEWAY_URL/api/v1/process/health" \
        -H "Authorization: Bearer $AUTH_TOKEN")
    
    if echo "$processing_route_response" | grep -q "UP\|healthy"; then
        success "Roteamento para processing service funcionando"
    else
        warning "Roteamento para processing service pode ter problemas"
    fi
}

# Função para testar cenários de erro
test_error_scenarios() {
    log "=== Testando Cenários de Erro ==="
    
    # Teste 1: Upload sem arquivo
    local empty_upload_response=$(curl -s -X POST "$UPLOAD_URL/api/v1/upload" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -F "video=")
    
    local empty_upload_success=$(echo "$empty_upload_response" | jq -r '.success // true')
    
    if [ "$empty_upload_success" = "false" ]; then
        success "Upload sem arquivo rejeitado corretamente"
    else
        warning "Upload sem arquivo deveria ter sido rejeitado"
    fi
    
    # Teste 2: Acesso sem autenticação
    local unauthorized_response=$(curl -s -X GET "$UPLOAD_URL/api/v1/upload/health")
    local unauthorized_status=$(echo "$unauthorized_response" | jq -r '.status // "unknown"')
    
    if [ "$unauthorized_status" != "unknown" ]; then
        success "Health check acessível sem autenticação (conforme esperado)"
    else
        warning "Health check pode ter problemas de acesso"
    fi
    
    # Teste 3: Job inexistente
    local invalid_job_response=$(curl -s -X GET "$PROCESSING_URL/api/v1/process/invalid-job-id/status" \
        -H "Authorization: Bearer $AUTH_TOKEN")
    
    local invalid_job_success=$(echo "$invalid_job_response" | jq -r '.success // true')
    
    if [ "$invalid_job_success" = "false" ]; then
        success "Job inexistente rejeitado corretamente"
    else
        warning "Job inexistente deveria ter sido rejeitado"
    fi
}

# Função para limpeza
cleanup() {
    log "=== Limpeza ==="
    
    # Remover arquivos temporários
    rm -f test_video_e2e.mp4
    
    success "Limpeza concluída"
}

# Função para verificar se os serviços estão rodando
check_services_running() {
    log "Verificando se os serviços estão rodando..."
    
    # Verificar se Docker está rodando
    if ! docker info > /dev/null 2>&1; then
        error "Docker não está rodando. Inicie o Docker primeiro."
        exit 1
    fi
    
    # Verificar se os containers estão rodando
    local containers_running=$(docker compose ps --services --filter "status=running" | wc -l)
    
    if [ "$containers_running" -lt 6 ]; then
        warning "Nem todos os serviços estão rodando."
        log "Para iniciar os serviços, execute: make up"
        log "Aguardando 10 segundos para verificar novamente..."
        sleep 10
        
        containers_running=$(docker compose ps --services --filter "status=running" | wc -l)
        
        if [ "$containers_running" -lt 6 ]; then
            error "Serviços não estão disponíveis. Execute 'make up' primeiro."
            exit 1
        fi
    fi
    
    success "Serviços estão rodando ($containers_running containers ativos)"
}

# Função principal
main() {
    log "Iniciando Teste Regressivo de Ponta a Ponta - FIAP X"
    log "=================================================="
    
    # Verificar se jq está instalado
    if ! command -v jq &> /dev/null; then
        error "jq não está instalado. Instale com: sudo apt-get install jq"
        exit 1
    fi
    
    # Verificar se curl está instalado
    if ! command -v curl &> /dev/null; then
        error "curl não está instalado. Instale com: sudo apt-get install curl"
        exit 1
    fi
    
    # Verificar se os serviços estão rodando
    check_services_running
    
    # Executar testes
    test_health_checks
    test_authentication
    test_upload
    test_processing
    test_storage
    test_notifications
    test_gateway
    test_error_scenarios
    cleanup
    
    log "=================================================="
    success "Teste Regressivo de Ponta a Ponta concluído com sucesso!"
    log "Resumo:"
    log "  - File ID: $FILE_ID"
    log "  - Job ID: $JOB_ID"
    log "  - Processed File: $PROCESSED_FILE"
    log "  - User ID: $USER_ID"
}

# Executar função principal
main "$@" 