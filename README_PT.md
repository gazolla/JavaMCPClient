# 🇧🇷 Cliente MCP em Java

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![MCP](https://img.shields.io/badge/MCP-Latest-blue.svg)](https://modelcontextprotocol.io/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)](https://maven.apache.org/)

Este repositório demonstra como criar aplicações Java com capacidades de **Cliente Agente** usando o protocolo **MCP (Model Context Protocol)**. O projeto inclui exemplos práticos de integração com diferentes LLMs e estratégias para seleção automática de ferramentas.

## 📋 Índice

- [O que é MCP?](#o-que-é-mcp)
- [Projetos Incluídos](#projetos-incluídos)
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Instalação](#instalação)
- [Configuração](#configuração)
- [Uso](#uso)
- [Recursos Externos](#recursos-externos)

## 🤖 O que é MCP?

O **Model Context Protocol (MCP)** é um protocolo que define um padrão de conexão de modelos de linguagem a contextos e ferramentas. Suas principais características incluem:

## 📦 Projetos Incluídos

### 1. AiChat

**Descrição:** Cliente simples para interação com Google Gemini  
**Características:**

- Integração direta com API Gemini
- Interface de console interativa
- Tratamento de erros robusto

### 2. ChatMCPGemini4J

**Descrição:** Cliente MCP completo integrado com Gemini  
**Características:**

- Suporte a múltiplos servidores MCP
- Seleção automática de ferramentas
- Function calling com Gemini
- System prompts inteligentes

### 3. ChatMCPGroq4J ⭐

**Descrição:** Cliente MCP avançado com Groq LLM  
**Características:**

- Integração com Groq para inferência rápida
- Workflow completo: Query → Tool Selection → MCP Execution
- Chaining de múltiplas ferramentas
- Tempo médio de execução: 2-3 segundos

### 4. MCPClient

**Descrição:** Cliente MCP básico para demonstração  
**Características:**

- Exemplo simples de uso do Java MCP SDK
- Conexão com servidor filesystem
- Operações básicas de arquivo

## 🏗️ Arquitetura

```mermaid
graph TD
    A[Console UI] <--> B[LLM Service]
    B <--> C[MCP Servers]
    C --> D[FileSystem]
    C --> E[Memory]
    C --> F[Weather]
    C --> G[Internet APIs]

    B --> H[MCP Service]
    H --> I[Tool Execution]

    style A fill:#a8e6a3
    style B fill:#ffc085
    style C fill:#85c1ff
```

### Fluxo de Execução:

1. **USER INPUT** - Captura query via console
2. **GROQ SERVICE** - Análise + tools disponíveis
3. **TOOL SELECTION** - LLM decide ferramentas via function calling
4. **MCP EXECUTION** - Executa tool via cliente MCP síncrono
5. **RESULT PROCESSING** - Formata resposta + tempo execução
6. **CONSOLE OUTPUT** - Exibe resultado ao usuário

## 🔧 Pré-requisitos

- **Java 17+**
- **Maven 3.6+**
- **Node.js** (para servidores MCP)
- **API Keys:**
  - Groq API Key (recomendado)
  - Google Gemini API Key

## 📥 Instalação

1. **Clone o repositório:**
   
   ```bash
   git clone <repository-url>
   cd WRKGRPMCP
   ```

2. **Instale dependências Maven:**
   
   ```bash
   mvn clean install
   ```

3. **Instale servidores MCP:**
   
   ```bash
   # Servidor filesystem
   npm install -g @modelcontextprotocol/server-filesystem
   
   ```

# Servidor weather (exemplo)

npm install -g @modelcontextprotocol/server-weather

```

## ⚙️ Configuração

### 1. Variáveis de Ambiente

Crie as seguintes variáveis de ambiente:

```bash
# Para Groq
export GROQ_API_KEY= <groq api key>

# Para Gemini
export GEMINI_API_KEY= <api key here>
```

### 2. Configuração de Servidores MCP

Os projetos estão configurados para conectar automaticamente aos servidores MCP disponíveis. Os servidores são descobertos e inicializados dinamicamente.

## 🚀 Uso

### ChatMCPGroq4J (Recomendado)

```bash
cd ChatMCPGroq4J
mvn clean compile exec:java
```

**Comandos de exemplo:**

- `"Liste os arquivos da pasta Documents"`
- `"Qual a previsão do tempo para hoje?"`
- `"Salve as informações do tempo em um arquivo"`
- `"Leia o arquivo que acabei de criar"`

### ChatMCPGemini4J

```bash
cd ChatMCPGemini4J
mvn clean compile exec:java
```

### AiChat

```bash
cd AiChat
mvn clean compile exec:java
```

## 🌐 Recursos Externos

- **[Groq.com](https://groq.com/)** - LLM rápido para inferência
- **[MCP Servers](https://mcpservers.org)** - Catálogo de servidores MCP
- **[Java MCP SDK](https://github.com/modelcontextprotocol/java-sdk)** - SDK oficial Anthropic
- **[Spring AI](https://spring.io/projects/spring-ai)** - Framework colaborador

## 🤝 Contribuindo

1. Fork o projeto
2. Crie sua feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📝 Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para detalhes.
