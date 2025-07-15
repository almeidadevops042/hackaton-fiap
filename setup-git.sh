#!/bin/bash

# Script para configurar Git e fazer push inicial
# Uso: ./setup-git.sh <URL_DO_REPOSITORIO>

set -e

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

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

# Verificar se a URL foi fornecida
if [ $# -eq 0 ]; then
    error "URL do repositório não fornecida!"
    echo "Uso: $0 <URL_DO_REPOSITORIO>"
    echo ""
    echo "Exemplos:"
    echo "  $0 https://github.com/seu-usuario/projeto-fiapx-refactoring.git"
    echo "  $0 git@github.com:seu-usuario/projeto-fiapx-refactoring.git"
    exit 1
fi

REPO_URL=$1

log "Configurando repositório Git para FIAP X..."

# Verificar se Git está instalado
if ! command -v git &> /dev/null; then
    error "Git não está instalado!"
    exit 1
fi

# Verificar se estamos em um repositório Git
if [ ! -d ".git" ]; then
    error "Diretório atual não é um repositório Git!"
    exit 1
fi

# Verificar se já existe remote
if git remote -v | grep -q "origin"; then
    warning "Remote 'origin' já existe!"
    echo "Remotes atuais:"
    git remote -v
    echo ""
    read -p "Deseja sobrescrever? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "Operação cancelada."
        exit 0
    fi
    git remote remove origin
fi

# Adicionar remote
log "Adicionando remote origin: $REPO_URL"
git remote add origin "$REPO_URL"
success "Remote adicionado com sucesso!"

# Adicionar todos os arquivos
log "Adicionando arquivos ao staging..."
git add .
success "Arquivos adicionados!"

# Verificar se há mudanças para commit
if git diff --cached --quiet; then
    log "Nenhuma mudança para commitar."
else
    # Fazer commit inicial
    log "Fazendo commit inicial..."
    git commit -m "Initial commit: FIAP X Microservices Refactoring

- Refatoração completa para Kotlin/Spring Boot
- Arquitetura de microserviços
- Testes automatizados (Unit, E2E, Performance)
- Docker e Docker Compose
- Documentação completa
- Scripts de automação"
    success "Commit realizado!"
fi

# Fazer push
log "Fazendo push para o repositório remoto..."
if git push -u origin master; then
    success "Push realizado com sucesso!"
    log "Repositório configurado e sincronizado!"
else
    error "Erro ao fazer push!"
    log "Verifique se:"
    log "  1. A URL do repositório está correta"
    log "  2. Você tem permissão para fazer push"
    log "  3. O repositório remoto existe"
    exit 1
fi

echo ""
success "Configuração do Git concluída!"
log "Próximos passos:"
log "  1. Verifique o repositório remoto"
log "  2. Configure branch protection se necessário"
log "  3. Configure CI/CD se desejar" 