# Testes Automatizados - Microserviços Kotlin

Este documento descreve a estrutura de testes automatizados implementada para todos os microserviços do projeto FIAP X.

## Estrutura de Testes

Cada microserviço possui a seguinte estrutura de testes:

```
services/{microserviço}/
├── src/
│   └── test/
│       └── kotlin/
│           └── com/fiapx/{microserviço}/
│               ├── service/
│               │   └── {Microserviço}ServiceTest.kt
│               └── controller/
│                   └── {Microserviço}ControllerTest.kt
```

## Microserviços com Testes

### 1. Upload Service (`services/upload/`)
- **Testes Unitários**: `UploadServiceTest.kt`
  - Upload de arquivo válido
  - Validação de arquivo inválido
  - Geração de hash MD5
  - Armazenamento no Redis

- **Testes de Integração**: `UploadControllerTest.kt`
  - Endpoint POST `/upload`
  - Validação de resposta JSON
  - Tratamento de erros

### 2. Processing Service (`services/processing/`)
- **Testes Unitários**: `ProcessingServiceTest.kt`
  - Criação de job de processamento
  - Consulta de status do job
  - Cancelamento de job
  - Verificação de disponibilidade do FFmpeg

- **Testes de Integração**: `ProcessingControllerTest.kt`
  - Endpoint POST `/process`
  - Endpoint GET `/process/{id}/status`
  - Endpoint DELETE `/process/{id}`
  - Endpoint GET `/process/jobs`

### 3. Storage Service (`services/storage/`)
- **Testes Unitários**: `StorageServiceTest.kt`
  - Listagem de arquivos
  - Download de arquivos
  - Deleção de arquivos
  - Estatísticas de storage
  - Contadores de download

- **Testes de Integração**: `StorageControllerTest.kt`
  - Endpoint GET `/files`
  - Endpoint GET `/files/download/{filename}`
  - Endpoint DELETE `/files/{filename}`
  - Endpoint GET `/files/stats`
  - Endpoint GET `/files/health`

### 4. Notification Service (`services/notification/`)
- **Testes Unitários**: `NotificationServiceTest.kt`
  - Criação de notificações
  - Consulta de notificação por ID
  - Listagem de notificações por usuário
  - Marcação como lida
  - Deleção de notificações
  - Estatísticas de notificações

- **Testes de Integração**: `NotificationControllerTest.kt`
  - Endpoint POST `/notifications`
  - Endpoint GET `/notifications`
  - Endpoint GET `/notifications/{id}`
  - Endpoint POST `/notifications/{id}/read`
  - Endpoint POST `/notifications/mark-all-read`
  - Endpoint DELETE `/notifications/{id}`
  - Endpoint GET `/notifications/stats`

### 5. Gateway Service (`services/gateway/`)
- **Testes de Integração**: `WebControllerTest.kt`
  - Endpoint GET `/` (página inicial)
  - Endpoint GET `/health`

- **Testes de Configuração**: `GatewayConfigTest.kt`
  - Configuração de rotas
  - Validação de rotas principais

## Tecnologias Utilizadas

### Dependências de Teste
- **JUnit 5**: Framework de testes
- **MockK**: Biblioteca de mocking para Kotlin
- **Spring Boot Test**: Suporte a testes Spring Boot
- **MockMvc**: Testes de controllers REST
- **Jacoco**: Cobertura de código

### Configuração Jacoco
Cada microserviço possui configuração Jacoco no `build.gradle.kts`:

```kotlin
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

## Como Executar os Testes

### Executar Todos os Testes de um Microserviço
```bash
cd services/{microserviço}
./gradlew test
```

### Executar Testes com Cobertura
```bash
cd services/{microserviço}
./gradlew jacocoTestReport
```

### Executar Testes Específicos
```bash
cd services/{microserviço}
./gradlew test --tests "*ServiceTest"
./gradlew test --tests "*ControllerTest"
```

### Executar Todos os Testes do Projeto
```bash
# Na raiz do projeto
find services -name "build.gradle.kts" -execdir ./gradlew test \;
```

## Visualizar Relatórios de Cobertura

Após executar `./gradlew jacocoTestReport`, os relatórios estarão disponíveis em:

```
services/{microserviço}/build/reports/jacoco/test/html/index.html
```

### Cobertura Esperada
- **Services**: > 80% de cobertura
- **Controllers**: > 90% de cobertura
- **Models**: > 95% de cobertura

## Estratégias de Teste

### Testes Unitários
- **Foco**: Lógica de negócio isolada
- **Mocks**: RedisTemplate, WebClient, dependências externas
- **Cobertura**: Métodos principais dos services

### Testes de Integração
- **Foco**: Endpoints REST e fluxos completos
- **Mocks**: Services dependentes
- **Cobertura**: Controllers e configurações

### Testes de Configuração
- **Foco**: Configurações Spring Boot
- **Contexto**: Aplicação completa
- **Cobertura**: Beans e configurações

## Padrões de Nomenclatura

### Classes de Teste
- `{Classe}Test.kt` para testes unitários
- `{Classe}IntegrationTest.kt` para testes de integração

### Métodos de Teste
- Nomes descritivos em português
- Formato: `deve {ação} quando {condição}`
- Exemplo: `deve retornar sucesso no upload`

### Organização
- Um arquivo de teste por classe principal
- Métodos de teste agrupados por funcionalidade
- Setup e teardown quando necessário

## Boas Práticas Implementadas

### 1. Isolamento
- Cada teste é independente
- Mocks para dependências externas
- Cleanup automático de recursos

### 2. Legibilidade
- Nomes descritivos em português
- Comentários explicativos quando necessário
- Estrutura clara e organizada

### 3. Manutenibilidade
- Reutilização de código de teste
- Configurações centralizadas
- Padrões consistentes

### 4. Cobertura
- Testes para casos de sucesso e erro
- Validação de edge cases
- Cobertura de branches importantes

## Exemplo de Execução

```bash
# Executar testes do upload service
cd services/upload
./gradlew test

# Verificar cobertura
./gradlew jacocoTestReport

# Abrir relatório no navegador
open build/reports/jacoco/test/html/index.html
```

## Próximos Passos

1. **Testes de Performance**: Implementar testes de carga
2. **Testes E2E**: Cenários completos de usuário
3. **Testes de Segurança**: Validação de autenticação/autorização
4. **Testes de Resiliência**: Circuit breakers e fallbacks
5. **CI/CD**: Integração com pipeline de build

## Troubleshooting

### Problemas Comuns

1. **Timeout em Downloads**: Aumentar timeout do Gradle
2. **Mocks não funcionando**: Verificar imports do MockK
3. **Contexto não carregando**: Verificar anotações Spring Boot Test
4. **Cobertura baixa**: Adicionar testes para branches não cobertos

### Logs de Debug
```bash
./gradlew test --info
./gradlew test --debug
```

---

**Nota**: Esta estrutura de testes garante uma cobertura satisfatória e facilita a manutenção e evolução dos microserviços. 