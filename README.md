# MCP Client em Java / MCP Client in Java

Este repositório contém exemplos práticos de implementação de clientes MCP (Model Context Protocol) em Java, incluindo integração com diferentes LLMs e estratégias de seleção automática de ferramentas.

*This repository contains practical examples of MCP (Model Context Protocol) client implementations in Java, including integration with different LLMs and automatic tool selection strategies.*

## 📚 Documentação / Documentation

- **[🇧🇷 Português](README_PT.md)** - Documentação completa em português
- **[🇺🇸 English](README_EN.md)** - Complete documentation in English

## 🚀 Projetos Incluídos / Projects Included

| Projeto             | Descrição              | Description            |
| ------------------- | ---------------------- | ---------------------- |
| **AiChat**          | Cliente simples Gemini | Simple Gemini client   |
| **ChatMCPGemini4J** | Cliente MCP com Gemini | MCP client with Gemini |
| **ChatMCPGroq4J**   | Cliente MCP com Groq   | MCP client with Groq   |
| **MCPClient**       | Cliente MCP básico     | Basic MCP client       |

## 🎯 Começar Rapidamente / Quick Start

```bash
# Clone o repositório
git clone <repository-url>

# Navegue para um projeto
cd ChatMCPGroq4J

# Configure as variáveis de ambiente
export GROQ_API_KEY=your_api_key_here

# Execute o projeto
mvn clean compile exec:java
```

## 🔐 Segurança / Security

✅ **Todas as API keys são externalizadas via variáveis de ambiente**  
✅ **All API keys are externalized via environment variables**

---

**Desenvolvido por / Developed by:** [Seu Nome]  
