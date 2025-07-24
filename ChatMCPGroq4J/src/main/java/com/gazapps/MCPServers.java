package com.gazapps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class MCPServers implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPServers.class);
    
    public List<MCPService.ServerConfig> mcpServers = new ArrayList<>();
    public final Map<String, McpSyncClient> mcpClients = new HashMap<>();
    private final Map<String, MCPService.ServerStatus> serverStatuses = new HashMap<>();
    private final Map<String, String> toolToServer = new HashMap<>(); 
    private final Map<String, Tool> availableMcpTools = new HashMap<>();
    
    private final MCPService mcpService;
    public int requestTimeoutSeconds = 30;
    
    // Template para configuração de servidores
    private static class ServerTemplate {
        final String name;
        final String urlPattern;
        final String type;
        final String description;
        final int priority;
        final String requirement;
        
        ServerTemplate(String name, String urlPattern, String type, String description, int priority, String requirement) {
            this.name = name;
            this.urlPattern = urlPattern;
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.requirement = requirement;
        }
    }
    
    private static final ServerTemplate[] DEFAULT_SERVERS = {
        new ServerTemplate("filesystem", "npx @modelcontextprotocol/server-filesystem \"%s\"", 
                          "stdio", "Sistema de arquivos - pasta Documents do usuário", 3, "REQUIRES_NODEJS"),
        new ServerTemplate("memory", "npx @modelcontextprotocol/server-memory", 
                          "stdio", "Armazenamento temporário em memória", 1, "REQUIRES_NODEJS"),
        new ServerTemplate("weather-nws", "npx @h1deya/mcp-server-weather", 
                          "stdio", "Previsões meteorológicas via National Weather Service (EUA)", 1, "REQUIRES_NODEJS")
    };
    
    public MCPServers(MCPService mcpService) {
        this.mcpService = mcpService;
    }

    public void loadServers() {
        String documentsPath = System.getProperty("user.home") + "\\Documents";
        
        for (ServerTemplate template : DEFAULT_SERVERS) {
            String url = template.urlPattern.contains("%s") ? 
                String.format(template.urlPattern, documentsPath) : template.urlPattern;
                
            // Criar servidor diretamente sem addServer
            MCPService.ServerConfig server = new MCPService.ServerConfig(template.name, url, template.type, documentsPath);
            server.description = template.description;
            server.priority = MCPService.ServerConfig.ServerPriority.fromValue(template.priority);
            server.enabled = true;
            
            if (template.requirement != null) {
                server.environment.put(template.requirement, "true");
            }
            
            mcpServers.add(server);
        }
    }


    private boolean checkCommand(String... command) {
        try {
            return new ProcessBuilder(command).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void processServerDependencies() {
        boolean isNodeAvailable = checkCommand("cmd.exe", "/c", "npx", "--version");
        boolean isInternetAvailable = checkCommand("ping", "-n", "1", "8.8.8.8");
        boolean isDockerAvailable = checkCommand("docker", "--version");

        System.out.println("🔍 Verificando dependências dos servidores MCP...");
        System.out.println("Node.js: " + (isNodeAvailable ? "✅ Disponível" : "❌ Não encontrado"));
        System.out.println("Internet: " + (isInternetAvailable ? "✅ Conectado" : "❌ Offline"));
        System.out.println("Docker: " + (isDockerAvailable ? "✅ Disponível" : "❌ Não encontrado"));

        for (MCPService.ServerConfig server : mcpServers) {
            processServerDependency(server, isNodeAvailable, isInternetAvailable, isDockerAvailable);
        }

        mcpServers.sort((a, b) -> Integer.compare(a.priority.getValue(), b.priority.getValue()));

        long enabledCount = mcpServers.stream().filter(s -> s.enabled).count();
        System.out.printf("📊 Resultado: %d/%d servidores habilitados\n", enabledCount, mcpServers.size());
    }
    

    private boolean hasRequiredEnvironment(MCPService.ServerConfig server) {
        String requiredEnv = server.environment.get("REQUIRES_ENV");
        if (requiredEnv == null) return true;
        
        String envValue = System.getenv(requiredEnv);
        return envValue != null && !envValue.trim().isEmpty();
    }
    
    private void processServerDependency(MCPService.ServerConfig server, boolean nodeJsAvailable,
            boolean internetAvailable, boolean dockerAvailable) {
        List<String> missingDeps = new ArrayList<>();

        if (server.environment.containsKey("REQUIRES_NODEJS") && !nodeJsAvailable) {
            missingDeps.add("Node.js");
        }

        if (server.environment.containsKey("REQUIRES_ONLINE") && !internetAvailable) {
            missingDeps.add("Internet");
        }
        
        if (server.environment.containsKey("REQUIRES_DOCKER") && !dockerAvailable) {
            missingDeps.add("Docker");
        }

        if (!hasRequiredEnvironment(server)) {
            String requiredEnv = server.environment.get("REQUIRES_ENV");
            missingDeps.add("Env:" + requiredEnv);
        }

        if (!missingDeps.isEmpty()) {
            server.enabled = false;
            System.out.printf("  ❌ %-12s [%s] %s (falta: %s)\n", server.name, server.priority,
                    server.description, String.join(", ", missingDeps));
        } else {
            server.enabled = true;
            System.out.printf("  ✅ %-12s [%s] %s\n", server.name, server.priority, server.description);
        }
    }
    
    public void connectToServers() {
        System.out.println("🔌 Conectando aos servidores MCP...");
        
        for (MCPService.ServerConfig serverConfig : mcpServers) {
            if (!serverConfig.enabled) {
                System.out.printf("⏭️  Pulando %s (dependências não atendidas)\n", serverConfig.name);
                continue;
            }
             
            if (!hasRequiredEnvironment(serverConfig)) {
                String requiredEnv = serverConfig.environment.get("REQUIRES_ENV");
                System.out.printf("⏭️  Pulando %s (variável %s não configurada)\n", 
                    serverConfig.name, requiredEnv);
                serverStatuses.put(serverConfig.name, MCPService.ServerStatus.DISCONNECTED);
                continue;
            }
             
            connectToServer(serverConfig);
        }
    }
    
    private void connectToServer(MCPService.ServerConfig serverConfig) {
        try {
            logger.info("📡 Conectando a {}...", serverConfig.name);
            
            McpSyncClient client = mcpService.connectToServer(serverConfig);
            
            mcpClients.put(serverConfig.name, client);
            serverStatuses.put(serverConfig.name, MCPService.ServerStatus.CONNECTED);
            
            discoverToolsFromServer(serverConfig.name, client);
            
            logger.info(" ✅");
            
        } catch (Exception e) {
            System.err.println(e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
            logger.error("❌ Falha ao conectar a {}: {}", serverConfig.name, errorMessage);
            serverStatuses.put(serverConfig.name, MCPService.ServerStatus.ERROR);
        }
    }
    
    private void discoverToolsFromServer(String serverName, McpSyncClient client) {
        try {
            List<Tool> serverTools = mcpService.discoverServerTools(client);
            
            for (Tool mcpTool : serverTools) {
                String toolName = mcpTool.name();
                String namespacedToolName = serverName + ":" + toolName;
                toolToServer.put(namespacedToolName, serverName);
                availableMcpTools.put(namespacedToolName, mcpTool);
            }
        } catch (Exception e) {
            System.err.println(e);
            logger.error("⚠️ Erro ao descobrir tools: {}", e.getMessage());
        }
    }
    
    // Método base comum para agrupar ferramentas por servidor
    private Map<String, List<Tool>> groupToolsByServer() {
        Map<String, List<Tool>> toolsByServer = new HashMap<>();
        
        for (Map.Entry<String, Tool> entry : availableMcpTools.entrySet()) {
            String namespacedToolName = entry.getKey();
            Tool tool = entry.getValue();

            String[] parts = namespacedToolName.split(":", 2);
            if (parts.length == 2) {
                String serverName = parts[0];
                toolsByServer.computeIfAbsent(serverName, k -> new ArrayList<>()).add(tool);
            }
        }
        
        return toolsByServer;
    }
    
    // Formatação simplificada para servidor
    private String formatServerInfo(MCPService.ServerConfig server) {
        return String.format("""
            ---
            **Nome:** %s
            **Descrição:** %s
            **Caminho:** %s
            **URL/Comando:** %s
            **Prioridade:** %s
            **Dependências:** %s
            """, 
            server.name, 
            server.description,
            server.path != null && !server.path.isEmpty() ? server.path : "N/A",
            server.url,
            server.priority,
            server.environment
        );
    }
    
    public String getServerInfoForPrompt() {
        StringBuilder info = new StringBuilder();
        info.append("Servidores Disponíveis:\n\n");

        for (MCPService.ServerConfig server : mcpServers) {
            info.append(formatServerInfo(server));
        }

        return info.toString();
    }
    
    public String getAvailableToolsWithDetails() {
        StringBuilder description = new StringBuilder();
        description.append("🛠️ Ferramentas Disponíveis e Parâmetros:\n\n");

        for (Map.Entry<String, Tool> entry : availableMcpTools.entrySet()) {
            try {
                String fullToolName = entry.getKey();
                Tool mcpTool = entry.getValue();

                String toolDescription = mcpTool.description() != null ? mcpTool.description() : "Sem descrição";

                io.modelcontextprotocol.spec.McpSchema.JsonSchema inputSchema = mcpTool.inputSchema();
                Map<String, Object> properties = inputSchema != null && inputSchema.properties() != null 
                    ? inputSchema.properties() 
                    : Map.of();
                List<String> requiredParams = inputSchema != null && inputSchema.required() != null 
                    ? inputSchema.required() 
                    : List.of();

                description.append("---\n");
                description.append("**Ferramenta:** ").append(fullToolName).append("\n");
                description.append("**Descrição:** ").append(toolDescription).append("\n");
                description.append("**Parâmetros:**");

                if (properties.isEmpty()) {
                    description.append(" Nenhum\n");
                } else {
                    for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                        String paramName = propEntry.getKey();
                        Object paramDetails = propEntry.getValue();

                        String paramDescription = "Sem descrição";
                        if (paramDetails instanceof Map<?, ?>) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> details = (Map<String, Object>) paramDetails;
                            paramDescription = (String) details.getOrDefault("description", "Sem descrição");
                        }
                        boolean isRequired = requiredParams.contains(paramName);

                        description.append("\n  - **").append(paramName).append("**");
                        description.append(" (obrigatório: ").append(isRequired ? "sim" : "não").append(")");
                        description.append(": ").append(paramDescription);
                    }
                    description.append("\n");
                }
                description.append("\n");
            } catch (Exception e) {
                System.err.println("Erro ao processar ferramenta: " + entry.getKey() + " - " + e.getMessage());
                description.append("---\n");
                description.append("**Ferramenta:** ").append(entry.getKey()).append("\n");
                description.append("**Erro:** ").append(e.getMessage()).append("\n\n");
            }
        }

        return description.toString();
    }
    
    public String getAvailableTools() {
        StringBuilder output = new StringBuilder();
        Map<String, List<Tool>> toolsByServer = groupToolsByServer();

        for (Map.Entry<String, List<Tool>> entry : toolsByServer.entrySet()) {
            String serverName = entry.getKey();
            List<Tool> serverTools = entry.getValue();
            
            output.append(String.format("%s (%d tools):%n", serverName, serverTools.size()));
            for (Tool tool : serverTools) {
                String description = tool.description() != null ? tool.description() : "Sem descrição disponível";
                output.append(String.format("    • %s: %s%n", tool.name(), description));
            }
            output.append(System.lineSeparator()); 
        }

        return output.toString();
    }
    
    public void showAvailableTools() {
        Map<String, List<String>> toolsByServer = new HashMap<>();
        
        for (String namespacedTool : toolToServer.keySet()) {
            String serverName = toolToServer.get(namespacedTool);
            String toolName = namespacedTool.substring(serverName.length() + 1);
            toolsByServer.computeIfAbsent(serverName, k -> new ArrayList<>()).add(toolName);
        }
        
        for (Map.Entry<String, List<String>> entry : toolsByServer.entrySet()) {
            String serverName = entry.getKey();
            List<String> tools = entry.getValue();
            
            System.out.printf("%s (%d tools):%n", serverName, tools.size());            
            for (String toolName : tools) {
                System.out.printf("    • %s%n", toolName);
            }
            System.out.println();
        }
    }
    
    public String getServerForTool(String namespacedToolName) {
        return toolToServer.get(namespacedToolName);
    }
    
    public McpSyncClient getClient(String serverName) {
        return mcpClients.get(serverName);
    }
    
    public List<MCPService.ServerConfig> getConnectedServers() {
        return mcpServers.stream()
                .filter(server -> server.enabled && mcpClients.containsKey(server.name))
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public void close() {
       System.out.println("🔌 Fechando conexões...");
        
        for (Map.Entry<String, McpSyncClient> entry : mcpClients.entrySet()) {
            try {
                entry.getValue().close();
                System.out.printf("✅ %s desconectado\n", entry.getKey());
            } catch (Exception e) {
                System.err.println(e);
                logger.error("⚠️ Erro ao desconectar {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
