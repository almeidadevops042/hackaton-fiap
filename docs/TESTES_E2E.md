# Testes de Ponta a Ponta (E2E) e Performance - FIAP X

Este documento descreve os testes regressivos de ponta a ponta e de performance implementados para o sistema FIAP X.

## Visão Geral

O sistema possui três tipos de testes:

1. **Testes Unitários** - Testes individuais de cada componente
2. **Testes de Ponta a Ponta (E2E)** - Testes do fluxo completo do sistema
3. **Testes de Performance** - Testes de carga e performance

## Testes de Ponta a Ponta (E2E)

### Arquivo: `test-e2e.sh`

Este script testa todo o fluxo do sistema, desde a autenticação até o download do arquivo processado.

#### Pré-requisitos

- Docker e Docker Compose rodando
- `jq` instalado: `sudo apt-get install jq`
- `curl` instalado: `sudo apt-get install curl`

#### Fluxo Testado

1. **Health Checks**
   - Verifica se todos os serviços estão saudáveis
   - Aguarda até 30 tentativas para cada serviço

2. **Autenticação**
   - Registra um novo usuário de teste
   - Faz login e obtém token JWT
   - Valida o token obtido

3. **Upload de Arquivo**
   - Cria um arquivo de vídeo de teste
   - Faz upload via multipart/form-data
   - Valida resposta e obtém file_id

4. **Processamento**
   - Inicia processamento do arquivo
   - Monitora status do job até completar
   - Valida arquivo de saída gerado

5. **Storage e Download**
   - Lista arquivos disponíveis
   - Obtém informações do arquivo processado
   - Verifica disponibilidade do download

6. **Notificações**
   - Lista notificações do usuário
   - Verifica notificações específicas do job

7. **Gateway**
   - Testa roteamento via API Gateway
   - Verifica health checks através do gateway

8. **Cenários de Erro**
   - Upload sem arquivo
   - Acesso sem autenticação
   - Job inexistente

#### Execução

```bash
# Executar teste E2E
make e2e-test

# Ou executar diretamente
./test-e2e.sh
```

#### Saída Esperada

```
[2025-07-15 10:30:00] Iniciando Teste Regressivo de Ponta a Ponta - FIAP X
[2025-07-15 10:30:00] ==================================================
[2025-07-15 10:30:00] Aguardando serviço Gateway estar disponível...
✓ Serviço Gateway está disponível
[2025-07-15 10:30:00] === Testando Autenticação ===
[2025-07-15 10:30:00] Testando: Registro de usuário
✓ Registro de usuário - Status: 200
[2025-07-15 10:30:00] Testando: Login de usuário
✓ Login de usuário - Status: 200
✓ Autenticação realizada com sucesso
...
✓ Teste Regressivo de Ponta a Ponta concluído com sucesso!
```

## Testes de Performance

### Arquivo: `test-performance.sh`

Este script testa a performance do sistema sob diferentes cargas.

#### Testes Realizados

1. **Health Check Performance**
   - 100 requisições para cada serviço
   - Medição de tempo médio de resposta

2. **Upload Único**
   - Upload de arquivo de 1MB
   - Medição de tempo de upload

3. **Uploads Concorrentes**
   - 5 uploads simultâneos de arquivo de 5MB
   - Medição de taxa de sucesso

4. **Processamento**
   - Início de processamento
   - Medição de tempo de resposta

#### Execução

```bash
# Executar teste de performance
make performance-test

# Ou executar diretamente
./test-performance.sh
```

#### Relatório Gerado

O script gera um relatório detalhado em `performance_results/performance_report_YYYYMMDD_HHMMSS.txt`:

```
FIAP X - Relatório de Performance
=================================
Data: Tue Jul 15 10:30:00 BRT 2025
Timestamp: 20250715_103000

=== Upload Performance ===
Total de uploads: 6
Tempo total: 15420ms
Tempo médio: 2570ms
Tempo mínimo: 1200ms
Tempo máximo: 4500ms

=== Health Check Performance ===
Total de health checks: 300
Tempo médio: 45ms

=== Processing Performance ===
Total de processamentos: 1
Tempo médio: 850ms

=== Recomendações ===
- Monitore os tempos de resposta regularmente
- Considere escalar serviços com tempos altos
- Otimize uploads grandes se necessário
- Verifique a configuração do Redis para cache
```

## Execução de Todos os Testes

Para executar todos os tipos de teste:

```bash
# Executar apenas testes unitários (não requer serviços rodando)
make all-tests

# Executar todos os testes incluindo E2E e Performance (requer serviços rodando)
make all-tests-with-services
```

## Configuração

### URLs dos Serviços

Os scripts usam as seguintes URLs padrão:

- **Gateway**: http://localhost:8080
- **Upload**: http://localhost:8081
- **Processing**: http://localhost:8082
- **Storage**: http://localhost:8083
- **Notification**: http://localhost:8084
- **Auth**: http://localhost:8085

### Variáveis de Ambiente

Para alterar as URLs, edite as variáveis no início dos scripts:

```bash
# Em test-e2e.sh e test-performance.sh
BASE_URL="http://localhost:8080"
AUTH_URL="http://localhost:8085"
UPLOAD_URL="http://localhost:8081"
PROCESSING_URL="http://localhost:8082"
```

## Troubleshooting

### Problemas Comuns

1. **Serviços não respondem**
   ```bash
   # Verificar se os serviços estão rodando
   docker compose ps
   
   # Verificar logs
   docker compose logs
   ```

2. **Erro de autenticação**
   ```bash
   # Verificar se o serviço de auth está funcionando
   curl http://localhost:8085/health
   ```

3. **Timeout nos testes**
   ```bash
   # Aumentar timeouts nos scripts
   # Editar as variáveis max_attempts e max_wait
   ```

4. **Arquivo não encontrado**
   ```bash
   # Verificar se os volumes estão montados
   docker compose exec upload-service ls -la /app/uploads
   ```

### Logs Detalhados

Para obter logs mais detalhados, execute os scripts com debug:

```bash
# Adicionar debug aos scripts
bash -x ./test-e2e.sh
```

## Integração com CI/CD

### GitHub Actions

Exemplo de workflow para CI/CD:

```yaml
name: FIAP X Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Start services
      run: |
        make build
        make up
        sleep 30  # Aguardar serviços inicializarem
    
    - name: Run unit tests
      run: make kotlin-test
    
    - name: Run E2E tests
      run: make e2e-test
    
    - name: Run performance tests
      run: make performance-test
    
    - name: Upload test results
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: performance_results/
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'make build'
            }
        }
        
        stage('Start Services') {
            steps {
                sh 'make up'
                sh 'sleep 30'
            }
        }
        
        stage('Unit Tests') {
            steps {
                sh 'make kotlin-test'
            }
        }
        
        stage('E2E Tests') {
            steps {
                sh 'make e2e-test'
            }
        }
        
        stage('Performance Tests') {
            steps {
                sh 'make performance-test'
            }
        }
        
        stage('Cleanup') {
            steps {
                sh 'make down'
            }
        }
    }
    
    post {
        always {
            archiveArtifacts artifacts: 'performance_results/*', fingerprint: true
        }
    }
}
```

## Métricas e Monitoramento

### Métricas Importantes

1. **Tempo de Resposta**
   - Upload: < 5 segundos para arquivos < 10MB
   - Health Check: < 100ms
   - Processamento: < 2 segundos para iniciar

2. **Taxa de Sucesso**
   - Upload: > 95%
   - Processamento: > 90%
   - Health Check: > 99%

3. **Concorrência**
   - Uploads simultâneos: 5-10
   - Processamentos simultâneos: 3-5

### Alertas Recomendados

- Tempo de resposta > 10 segundos
- Taxa de erro > 5%
- Serviços não saudáveis
- Uso de memória > 80%
- Uso de CPU > 90%

## Próximos Passos

1. **Automação Completa**
   - Integrar com sistemas de CI/CD
   - Execução automática em horários específicos
   - Notificações de falhas

2. **Métricas Avançadas**
   - Integração com Prometheus/Grafana
   - Dashboards de performance
   - Alertas automáticos

3. **Testes Adicionais**
   - Testes de segurança
   - Testes de recuperação de falhas
   - Testes de escalabilidade

4. **Otimizações**
   - Cache de dependências
   - Paralelização de testes
   - Redução de tempo de execução 