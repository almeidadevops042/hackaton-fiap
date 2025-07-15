#!/bin/bash

# FIAP X - Teste de Performance e Carga
# Este script testa a performance do sistema sob carga

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configurações
BASE_URL="http://localhost:8080"
AUTH_URL="http://localhost:8085"
UPLOAD_URL="http://localhost:8081"
PROCESSING_URL="http://localhost:8082"

# Variáveis
AUTH_TOKEN=""
RESULTS_DIR="performance_results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Função para log
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

error() {
    echo -e "${RED}✗ $1${NC}"
}

# Função para obter token de autenticação
get_auth_token() {
    log "Obtendo token de autenticação..."
    
    # Criar usuário de teste para performance
    local register_data='{
        "username": "perfuser",
        "email": "perf@test.com",
        "password": "password123"
    }'
    
    curl -s -X POST "$AUTH_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -d "$register_data" > /dev/null
    
    # Fazer login
    local login_data='{
        "username": "perfuser",
        "password": "password123"
    }'
    
    local login_response=$(curl -s -X POST "$AUTH_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "$login_data")
    
    AUTH_TOKEN=$(echo "$login_response" | jq -r '.token // empty')
    
    if [ -n "$AUTH_TOKEN" ]; then
        success "Token obtido com sucesso"
    else
        error "Falha ao obter token"
        exit 1
    fi
}

# Função para criar arquivo de teste
create_test_file() {
    local filename=$1
    local size_mb=$2
    
    log "Criando arquivo de teste: $filename (${size_mb}MB)"
    
    # Criar arquivo com dados aleatórios
    dd if=/dev/urandom of="$filename" bs=1M count="$size_mb" 2>/dev/null
    
    success "Arquivo criado: $filename"
}

# Função para teste de upload único
test_single_upload() {
    local filename=$1
    local expected_time=$2
    
    log "Testando upload único: $filename"
    
    local start_time=$(date +%s%3N)
    
    local response=$(curl -s -X POST "$UPLOAD_URL/api/v1/upload" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -F "video=@$filename" \
        -F "metadata={\"description\":\"Performance test\",\"tags\":[\"perf\"]}")
    
    local end_time=$(date +%s%3N)
    local duration=$((end_time - start_time))
    
    local success=$(echo "$response" | jq -r '.success // false')
    
    if [ "$success" = "true" ]; then
        success "Upload concluído em ${duration}ms"
        echo "$duration" >> "$RESULTS_DIR/upload_times.txt"
        return 0
    else
        error "Upload falhou em ${duration}ms"
        return 1
    fi
}

# Função para teste de upload concorrente
test_concurrent_uploads() {
    local num_uploads=$1
    local filename=$2
    
    log "Testando $num_uploads uploads concorrentes..."
    
    local pids=()
    local success_count=0
    local fail_count=0
    
    # Iniciar uploads em paralelo
    for i in $(seq 1 $num_uploads); do
        (
            if test_single_upload "$filename" 30; then
                echo "success" >> "$RESULTS_DIR/upload_results.txt"
            else
                echo "fail" >> "$RESULTS_DIR/upload_results.txt"
            fi
        ) &
        pids+=($!)
    done
    
    # Aguardar todos os uploads terminarem
    for pid in "${pids[@]}"; do
        wait "$pid"
    done
    
    # Contar resultados
    success_count=$(grep -c "success" "$RESULTS_DIR/upload_results.txt" 2>/dev/null || echo 0)
    fail_count=$(grep -c "fail" "$RESULTS_DIR/upload_results.txt" 2>/dev/null || echo 0)
    
    log "Resultados dos uploads concorrentes:"
    log "  Sucessos: $success_count"
    log "  Falhas: $fail_count"
    log "  Taxa de sucesso: $((success_count * 100 / num_uploads))%"
}

# Função para teste de health checks
test_health_check_performance() {
    local num_requests=$1
    local endpoint=$2
    local description=$3
    
    log "Testando performance de $num_requests health checks em $description"
    
    local start_time=$(date +%s%3N)
    
    for i in $(seq 1 $num_requests); do
        curl -s -f "$endpoint/health" > /dev/null
    done
    
    local end_time=$(date +%s%3N)
    local total_time=$((end_time - start_time))
    local avg_time=$((total_time / num_requests))
    
    success "$description: ${total_time}ms total, ${avg_time}ms média"
    echo "$avg_time" >> "$RESULTS_DIR/health_check_times.txt"
}

# Função para teste de processamento
test_processing_performance() {
    local file_id=$1
    
    if [ -z "$file_id" ]; then
        log "Nenhum file_id fornecido para teste de processamento"
        return
    fi
    
    log "Testando performance de processamento para file_id: $file_id"
    
    local start_time=$(date +%s%3N)
    
    local process_data="{
        \"file_id\": \"$file_id\",
        \"options\": {
            \"frame_rate\": 1,
            \"quality\": \"low\",
            \"format\": \"jpg\"
        }
    }"
    
    local response=$(curl -s -X POST "$PROCESSING_URL/api/v1/process" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -d "$process_data")
    
    local end_time=$(date +%s%3N)
    local duration=$((end_time - start_time))
    
    local success=$(echo "$response" | jq -r '.success // false')
    
    if [ "$success" = "true" ]; then
        local job_id=$(echo "$response" | jq -r '.data.job_id // empty')
        success "Processamento iniciado em ${duration}ms - Job ID: $job_id"
        echo "$duration" >> "$RESULTS_DIR/processing_times.txt"
    else
        error "Falha no processamento em ${duration}ms"
    fi
}

# Função para gerar relatório
generate_report() {
    log "Gerando relatório de performance..."
    
    local report_file="$RESULTS_DIR/performance_report_$TIMESTAMP.txt"
    
    {
        echo "FIAP X - Relatório de Performance"
        echo "================================="
        echo "Data: $(date)"
        echo "Timestamp: $TIMESTAMP"
        echo ""
        
        echo "=== Upload Performance ==="
        if [ -f "$RESULTS_DIR/upload_times.txt" ]; then
            local upload_times=$(cat "$RESULTS_DIR/upload_times.txt")
            local upload_count=$(echo "$upload_times" | wc -l)
            local upload_total=$(echo "$upload_times" | awk '{sum+=$1} END {print sum}')
            local upload_avg=$(echo "$upload_times" | awk '{sum+=$1} END {print sum/NR}')
            local upload_min=$(echo "$upload_times" | sort -n | head -1)
            local upload_max=$(echo "$upload_times" | sort -n | tail -1)
            
            echo "Total de uploads: $upload_count"
            echo "Tempo total: ${upload_total}ms"
            echo "Tempo médio: ${upload_avg}ms"
            echo "Tempo mínimo: ${upload_min}ms"
            echo "Tempo máximo: ${upload_max}ms"
        else
            echo "Nenhum dado de upload disponível"
        fi
        echo ""
        
        echo "=== Health Check Performance ==="
        if [ -f "$RESULTS_DIR/health_check_times.txt" ]; then
            local health_times=$(cat "$RESULTS_DIR/health_check_times.txt")
            local health_count=$(echo "$health_times" | wc -l)
            local health_avg=$(echo "$health_times" | awk '{sum+=$1} END {print sum/NR}')
            
            echo "Total de health checks: $health_count"
            echo "Tempo médio: ${health_avg}ms"
        else
            echo "Nenhum dado de health check disponível"
        fi
        echo ""
        
        echo "=== Processing Performance ==="
        if [ -f "$RESULTS_DIR/processing_times.txt" ]; then
            local processing_times=$(cat "$RESULTS_DIR/processing_times.txt")
            local processing_count=$(echo "$processing_times" | wc -l)
            local processing_avg=$(echo "$processing_times" | awk '{sum+=$1} END {print sum/NR}')
            
            echo "Total de processamentos: $processing_count"
            echo "Tempo médio: ${processing_avg}ms"
        else
            echo "Nenhum dado de processamento disponível"
        fi
        echo ""
        
        echo "=== Recomendações ==="
        echo "- Monitore os tempos de resposta regularmente"
        echo "- Considere escalar serviços com tempos altos"
        echo "- Otimize uploads grandes se necessário"
        echo "- Verifique a configuração do Redis para cache"
        
    } > "$report_file"
    
    success "Relatório gerado: $report_file"
}

# Função para limpeza
cleanup() {
    log "Limpando arquivos temporários..."
    
    rm -f test_video_*.mp4
    rm -f "$RESULTS_DIR"/*.txt
    
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
    log "Iniciando Teste de Performance e Carga - FIAP X"
    log "=============================================="
    
    # Verificar dependências
    if ! command -v jq &> /dev/null; then
        error "jq não está instalado"
        exit 1
    fi
    
    if ! command -v curl &> /dev/null; then
        error "curl não está instalado"
        exit 1
    fi
    
    if ! command -v dd &> /dev/null; then
        error "dd não está instalado"
        exit 1
    fi
    
    # Verificar se os serviços estão rodando
    check_services_running
    
    # Criar diretório de resultados
    mkdir -p "$RESULTS_DIR"
    
    # Obter token de autenticação
    get_auth_token
    
    # Teste 1: Health checks de performance
    log "=== Teste 1: Health Checks ==="
    test_health_check_performance 100 "$UPLOAD_URL" "Upload Service"
    test_health_check_performance 100 "$PROCESSING_URL" "Processing Service"
    test_health_check_performance 100 "$BASE_URL" "Gateway"
    
    # Teste 2: Upload único
    log "=== Teste 2: Upload Único ==="
    create_test_file "test_video_small.mp4" 1
    test_single_upload "test_video_small.mp4" 10
    
    # Teste 3: Uploads concorrentes
    log "=== Teste 3: Uploads Concorrentes ==="
    create_test_file "test_video_medium.mp4" 5
    test_concurrent_uploads 5 "test_video_medium.mp4"
    
    # Teste 4: Processamento
    log "=== Teste 4: Processamento ==="
    # Usar o file_id do upload anterior se disponível
    local file_id=$(curl -s -X GET "$UPLOAD_URL/api/v1/upload/files" \
        -H "Authorization: Bearer $AUTH_TOKEN" | jq -r '.data[0].id // empty')
    
    if [ -n "$file_id" ]; then
        test_processing_performance "$file_id"
    else
        log "Nenhum arquivo disponível para teste de processamento"
    fi
    
    # Gerar relatório
    generate_report
    
    # Limpeza
    cleanup
    
    log "=============================================="
    success "Teste de Performance concluído!"
    log "Verifique o relatório em: $RESULTS_DIR/"
}

# Executar função principal
main "$@" 