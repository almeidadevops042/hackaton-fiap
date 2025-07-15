# Documentação do Projeto FIAP X

Esta pasta contém toda a documentação do projeto **FIAP X - Microserviços para Processamento de Vídeos**.

## 📋 Índice da Documentação

### 📖 Documentação Principal
- **[README.md](./README.md)** - Documentação principal do projeto
- **[README_KOTLIN_REFACTOR.md](./README_KOTLIN_REFACTOR.md)** - Guia de refatoração para Kotlin

### 🏗️ Arquitetura e Estrutura
- **[ARQUITETURA_ANALISE.md](./ARQUITETURA_ANALISE.md)** - Análise detalhada da arquitetura
- **[MICROSERVICES_STRUCTURE.md](./MICROSERVICES_STRUCTURE.md)** - Estrutura dos microserviços

### 🧪 Testes e Qualidade
- **[TESTES_AUTOMATIZADOS.md](./TESTES_AUTOMATIZADOS.md)** - Guia completo de testes automatizados

## 🚀 Como Usar Esta Documentação

### Para Desenvolvedores
1. **Comece pelo [README.md](./README.md)** - Visão geral do projeto
2. **Leia [ARQUITETURA_ANALISE.md](./ARQUITETURA_ANALISE.md)** - Entenda a arquitetura
3. **Consulte [MICROSERVICES_STRUCTURE.md](./MICROSERVICES_STRUCTURE.md)** - Estrutura dos serviços
4. **Implemente testes seguindo [TESTES_AUTOMATIZADOS.md](./TESTES_AUTOMATIZADOS.md)**

### Para Refatoração
1. **Siga o [README_KOTLIN_REFACTOR.md](./README_KOTLIN_REFACTOR.md)** - Guia de migração para Kotlin
2. **Mantenha a arquitetura documentada** - Atualize conforme necessário

### Para Qualidade
1. **Execute os testes** - Use [TESTES_AUTOMATIZADOS.md](./TESTES_AUTOMATIZADOS.md)
2. **Mantenha cobertura alta** - > 80% para services, > 90% para controllers

## 📁 Estrutura do Projeto

```
projeto-fiapx-refactoring/
├── docs/                           # 📚 Documentação (esta pasta)
│   ├── README.md                   # Índice da documentação
│   ├── README.md                   # Documentação principal
│   ├── README_KOTLIN_REFACTOR.md   # Guia de refatoração
│   ├── ARQUITETURA_ANALISE.md      # Análise de arquitetura
│   ├── MICROSERVICES_STRUCTURE.md  # Estrutura dos microserviços
│   └── TESTES_AUTOMATIZADOS.md     # Guia de testes
├── services/                       # 🏗️ Microserviços Kotlin
│   ├── gateway/                    # API Gateway
│   ├── upload/                     # Serviço de Upload
│   ├── processing/                 # Serviço de Processamento
│   ├── storage/                    # Serviço de Armazenamento
│   ├── notification/               # Serviço de Notificações
│   └── auth/                       # Serviço de Autenticação
├── services-go-backup/             # 🔄 Backup dos serviços Go
├── docker-compose.yml              # 🐳 Configuração Docker
├── Makefile                        # 🔧 Comandos de automação
└── README.md                       # 📖 README principal (na raiz)
```

## 🔄 Manutenção da Documentação

### Atualizações Necessárias
- **Após mudanças na arquitetura** - Atualize `ARQUITETURA_ANALISE.md`
- **Novos microserviços** - Atualize `MICROSERVICES_STRUCTURE.md`
- **Novos testes** - Atualize `TESTES_AUTOMATIZADOS.md`
- **Mudanças no projeto** - Atualize `README.md`

### Padrões de Documentação
- **Use Markdown** - Formato padrão para documentação
- **Mantenha atualizado** - Documentação deve refletir o código atual
- **Seja claro e conciso** - Documentação deve ser fácil de entender
- **Inclua exemplos** - Código e comandos práticos

## 📞 Suporte

Para dúvidas sobre a documentação ou o projeto:

1. **Consulte os arquivos nesta pasta**
2. **Verifique o código fonte** em `services/`
3. **Execute os testes** para validar funcionalidades
4. **Use o Makefile** para comandos automatizados

---

**Última atualização**: Janeiro 2024  
**Versão**: 1.0.0  
**Status**: ✅ Completo e organizado 