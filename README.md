# FIAP X - Microserviços para Processamento de Vídeos

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg)](https://www.docker.com/)
[![Tests](https://img.shields.io/badge/Tests-Automated-green.svg)](https://github.com/fiap/projeto-fiapx-refactoring)

## Documentação

Toda a documentação do projeto está organizada na pasta [`docs/`](./docs/):

- [Índice da Documentação](./docs/README.md) - Guia completo de navegação
- [Arquitetura](./docs/ARQUITETURA_ANALISE.md) - Análise detalhada da arquitetura
- [Refatoração Kotlin](./docs/README_KOTLIN_REFACTOR.md) - Guia de migração
- [Testes Automatizados](./docs/TESTES_AUTOMATIZADOS.md) - Estrutura de testes
- [Estrutura dos Microserviços](./docs/MICROSERVICES_STRUCTURE.md) - Organização dos serviços

## Início Rápido

### Pré-requisitos
- Docker e Docker Compose
- Java 17+ (para desenvolvimento local)
- FFmpeg (para processamento de vídeos)

### Execução
```bash
# Clone o repositório
git clone <repository-url>
cd projeto-fiapx-refactoring

# Execute com Docker Compose
make build
make up

# Acesse a aplicação
open http://localhost:8080
```

### Comandos Úteis
```bash
make build          # Build de todos os serviços
make up             # Subir todos os serviços
make down           # Parar todos os serviços
make logs           # Ver logs
make test           # Executar testes
make clean          # Limpar containers e volumes
```

## Arquitetura

### Microserviços
- Gateway (8080) - API Gateway e interface web
- Upload (8081) - Gerenciamento de uploads
- Processing (8082) - Processamento de vídeos
- Storage (8083) - Armazenamento e downloads
- Notification (8084) - Sistema de notificações
- Auth (8085) - Autenticação e autorização

### Tecnologias
- Kotlin - Linguagem principal
- Spring Boot - Framework web
- Redis - Cache e filas
- FFmpeg - Processamento de vídeo
- Docker - Containerização
- Jacoco - Cobertura de testes

## Testes

O sistema possui três tipos de testes automatizados:

### 1. Testes Unitários

Cada microserviço possui testes unitários completos:

```bash
# Executar testes de um serviço específico
cd services/upload
./gradlew test

# Executar todos os testes unitários
make kotlin-test

# Gerar relatório de cobertura
./gradlew jacocoTestReport
```

Cobertura esperada:
- Services: > 80%
- Controllers: > 90%
- Models: > 95%

### 2. Testes de Ponta a Ponta (E2E)

Testes que verificam todo o fluxo do sistema:

```bash
# Executar testes E2E
make e2e-test

# Ou executar diretamente
./test-e2e.sh
```

**Fluxo testado:**
- Health checks de todos os serviços
- Autenticação e autorização
- Upload de arquivos
- Processamento de vídeos
- Storage e download
- Notificações
- Cenários de erro

### 3. Testes de Performance

Testes de carga e performance do sistema:

```bash
# Executar testes de performance
make performance-test

# Ou executar diretamente
./test-performance.sh
```

**Métricas testadas:**
- Tempo de resposta dos health checks
- Performance de uploads únicos e concorrentes
- Tempo de início de processamento
- Relatórios detalhados de performance

### Executar Todos os Testes

```bash
# Executar todos os tipos de teste
make all-tests
```

**Documentação completa:** [docs/TESTES_E2E.md](./docs/TESTES_E2E.md)

## Estrutura do Projeto

```
projeto-fiapx-refactoring/
├── docs/                    # Documentação completa
├── services/                # Microserviços Kotlin
│   ├── gateway/             # API Gateway
│   ├── upload/              # Serviço de Upload
│   ├── processing/          # Serviço de Processamento
│   ├── storage/             # Serviço de Armazenamento
│   ├── notification/        # Serviço de Notificações
│   └── auth/                # Serviço de Autenticação
├── services-go-backup/      # Backup dos serviços Go
├── docker-compose.yml       # Configuração Docker
├── Makefile                 # Comandos de automação
└── README.md                # Este arquivo
```

## Links Úteis

- Interface Web: http://localhost:8080
- API Gateway: http://localhost:8080/health
- Documentação: [docs/README.md](./docs/README.md)
- Testes: [docs/TESTES_AUTOMATIZADOS.md](./docs/TESTES_AUTOMATIZADOS.md)

## Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature
3. Implemente seguindo os padrões de teste
4. Execute os testes: `make test`
5. Abra um Pull Request

## Licença

Este projeto é parte do desafio técnico FIAP X.

---

Status: Refatorado para Kotlin com testes automatizados  
Versão: 1.0.0  
Última atualização: Janeiro 2024 