package com.gazapps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groq.api.client.GroqApiClient;
import com.groq.api.client.GroqClientFactory;
import com.groq.api.models.Function;
import com.groq.api.models.Tool;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;


public class GroqService {
	private static final Logger logger = LoggerFactory.getLogger(GroqService.class);
	private final List<Function> availableGroqFunctions;
	private final MCPService mcpService;
	private final MCPServers mcpServers;
	private final GroqApiClient groqClient = GroqClientFactory
			.createClient(System.getenv("GROQ_API_KEY"));
	private final String groqModel = "meta-llama/llama-4-scout-17b-16e-instruct";
	// private final String groqModel = "qwen/qwen3-32b";

	private ObjectMapper objectMapper = new ObjectMapper();

	public GroqService() throws Exception {
		this.mcpService = new MCPService();
		this.mcpServers = new MCPServers(mcpService);
		mcpServers.loadServers();
		mcpServers.processServerDependencies();
		mcpServers.connectToServers();
		this.availableGroqFunctions = initializeAvailableGroqFunctions();
		String groqFunctions = availableGroqFunctions.stream().map(Function::name).collect(Collectors.joining("\n"));
		System.out.println("Available Groq functions:\n" + groqFunctions);
	}

	private List<Function> initializeAvailableGroqFunctions() {
		List<Function> groqFunctions = new ArrayList<>();
		for (Map.Entry<String, McpSyncClient> entry : mcpServers.mcpClients.entrySet()) {
			String serverName = entry.getKey();
			McpSyncClient client = entry.getValue();
			ListToolsResult toolsResult = client.listTools();
			List<io.modelcontextprotocol.spec.McpSchema.Tool> serverTools = toolsResult.tools();
			for (io.modelcontextprotocol.spec.McpSchema.Tool mcpTool : serverTools) {
				String toolName = mcpTool.name();
				String namespacedToolName = serverName + ":" + toolName;
				logger.debug("Converting MCP tool to Groq Function: {}", namespacedToolName);
				Function groqFunction = convertMcpToolToGroqFunction(mcpTool, namespacedToolName);
				groqFunctions.add(groqFunction);
			}
		}
		return groqFunctions;
	}
	
	public String processQuery(String userInput) {
		if (availableGroqFunctions.isEmpty()) {
			return "❌ Nenhuma ferramenta disponível. Verifique as conexões dos servidores.";
		}

		try {
			List<Tool> groqTools = availableGroqFunctions.stream().map(Tool::functionTool).collect(Collectors.toList());
			logger.debug("{}", groqTools.toString());
			String systemPrompt = buildGenericSystemPrompt();
			String response = groqClient.runConversationWithTools(userInput, groqTools, groqModel, systemPrompt)
					.get(mcpServers.requestTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
			return response;

		} catch (Exception e) {
			System.err.println(e);
			return "❌ Erro ao processar consulta: " + e.getMessage();
		}
	}

	private String buildGenericSystemPrompt() {
		StringBuilder prompt = new StringBuilder();

		prompt.append(
				"""
						You are an AI assistant designed to use MCP (Model Context Protocol) tools.
						Your primary function is to analyze user queries and invoke available tools to build complete and useful responses.
						NEVER respond that you cannot do something if there is an available tool for the task.
						Always use tools when appropriate. If the user's intention is unclear, ask clarifying questions.

						""");

		// Informações sobre servidores conectados
		List<MCPService.ServerConfig> connectedServers = mcpServers.mcpServers.stream()
				.filter(server -> server.enabled && mcpServers.mcpClients.containsKey(server.name))
				.collect(Collectors.toList());

		// Seção de coordenação multi-MCP (apenas se múltiplos servidores)
		if (connectedServers.size() > 1) {
			prompt.append("""
					TOOL CHAINING PROCESS:
					1. ANALYZE query → identify operations needed
					2. EXECUTE tools sequentially using real outputs as inputs
					3. EXTRACT specific values from results (e.g., {"price": 100} → use 100)
					4. BUILD response with actual data
					5. Flow: Data → Tool A → Result A → Tool B → Result B → Tool C → Final Output
					6. On failure: inform user and continue
					
					RULES:
					- One tool at a time
					- Use Tool A output as Tool B parameter  
					- Continue until workflow complete
					- Include real data in final response
					
					CRITICAL: NEVER use placeholder text like ${tool_name} in your responses.
					Always use the real data returned by tools.

					WORKFLOW EXAMPLE:
					User: "Get weather data and save to file"
					Step 1: Execute weather_get_forecast → receives actual JSON weather data
					Step 2: Execute filesystem_write_file with the REAL weather JSON as content
					Step 3: Respond with summary of what was actually saved

					CHAINING EXAMPLE:
					User: "Read memory, process the data, and save to database"
					Step 1: Execute memory_read_graph → get actual JSON: {"entities": [...]}
					Step 2: Execute data_process with the real JSON from step 1
					Step 3: Execute database_save with processed results from step 2
					Step 4: Respond with what was actually accomplished

					
					            """);
		}

		// Seção de servidores disponíveis
		if (!connectedServers.isEmpty()) {
			prompt.append("AVAILABLE MCP SERVERS:\n");

			for (MCPService.ServerConfig server : connectedServers) {
				prompt.append("- ").append(server.name.toUpperCase()).append(": ").append(server.description);

				// Informações genéricas baseadas nas propriedades do servidor
				if (!server.path.isEmpty()) {
					prompt.append("\n  Base path: ").append(server.path);

					// Instruções genéricas para servidores com path
					if (server.type.equals("stdio")) {
						prompt.append("\n  IMPORTANT: Always use complete absolute paths starting with: ")
								.append(server.path);
						prompt.append("\n  Correct example: '").append(server.path);
						if (System.getProperty("os.name").toLowerCase().contains("win")) {
							prompt.append("\\filename.ext'");
						} else {
							prompt.append("/filename.ext'");
						}
						prompt.append("\n  NEVER use relative paths");
					}
				}

				// Instruções genéricas baseadas no tipo de transporte
				if (server.type.equals("http")) {
					prompt.append("\n  Transport: HTTP - Online service for external data and operations");
				} else if (server.type.equals("stdio")) {
					prompt.append("\n  Transport: STDIO - Local process for system operations");
				}

				// Informações sobre variáveis de ambiente (se houver configurações especiais)
				if (!server.environment.isEmpty()) {
					long configCount = server.environment.entrySet().stream()
							.filter(entry -> !entry.getKey().startsWith("REQUIRES_")).count();
					if (configCount > 0) {
						prompt.append("\n  Configured with environment variables for enhanced functionality");
					}
				}

				prompt.append("\n\n");
			}

			// Instruções sobre nomenclatura das ferramentas e boas práticas
			prompt.append("""
					TOOL NAMING CONVENTION:
					Tools follow the pattern: {server}_{original_function}
					Examples: 'filesystem_read_file', 'git_status', 'weather_get_forecast'

					BEST PRACTICES:
					- Always use complete absolute paths for file operations
					- Verify paths are within the allowed server scope
					- For multiple operations, combine tools intelligently
					- Always provide clear feedback about executed operations
					- If an operation fails, explain why and suggest alternatives

					""");
		}

		// Instruções específicas do sistema operacional
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			prompt.append("""
					SYSTEM: Windows
					- Use backslashes (\\) in paths
					- Absolute paths start with drive letter (e.g., C:\\)

					""");
		} else {
			prompt.append("""
					SYSTEM: Unix/Linux
					- Use forward slashes (/) in paths
					- Absolute paths start with forward slash (/)

					""");
		}

		logger.debug("{}", prompt.toString());

		return prompt.toString();
	}
	
	private Function convertMcpToolToGroqFunction(io.modelcontextprotocol.spec.McpSchema.Tool mcpTool, String namespacedName) {
	    try {
	        logger.debug("In convertMcpToolToGroqFunction for {}", namespacedName);

	        String description = mcpTool.description() != null ? mcpTool.description() : "Sem descrição disponível";
	        logger.debug("Retrieved description: {}", description);

	        Object inputSchema = mcpTool.inputSchema();
	        JsonNode inputSchemaNode = objectMapper.valueToTree(inputSchema);
	        logger.debug("Converted inputSchema to JsonNode.");

	        return new Function(namespacedName, description, inputSchemaNode, args -> {
	            try {
	                Map<String, Object> argsMap = objectMapper.readValue((String) args,
	                        new TypeReference<Map<String, Object>>() {});
	                String serverName = mcpServers.getServerForTool(namespacedName);
	                McpSyncClient client = mcpServers.getClient(serverName);
	                String originalToolName = namespacedName.substring(serverName.length() + 1);
	                String result = mcpService.executeToolByName(client, originalToolName, argsMap);
	                return CompletableFuture.completedFuture(result);
	            } catch (Exception e) {
	                System.err.println(e);
	                logger.error("{}", e.getMessage());
	                return CompletableFuture.completedFuture("Erro ao processar argumentos: " + e.getMessage());
	            }
	        });
	    } catch (Exception e) {
	        System.err.println(e);
	        logger.error("Erro ao converter tool {}: {}", mcpTool.name(), e.getMessage());
	        return new Function(namespacedName, "Tool with conversion error", objectMapper.createObjectNode(),
	                args -> CompletableFuture.completedFuture("Erro na conversão da ferramenta"));
	    }
	}

}