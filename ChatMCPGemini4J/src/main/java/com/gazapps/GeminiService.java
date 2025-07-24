package com.gazapps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private static final String API_KEY =  System.getenv("GEMINI_API_KEY"); 
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MCPService mcpService;
    private final MCPServers mcpServers;
    private final List<FunctionDeclaration> availableMcpTools;

    public GeminiService() throws Exception {
        this.mcpService = new MCPService();
        this.mcpServers = new MCPServers(mcpService);
        mcpServers.loadServers();
        mcpServers.processServerDependencies();
        mcpServers.connectToServers();
        this.availableMcpTools = initializeAvailableMcpTools();
    }

    public List<FunctionDeclaration> initializeAvailableMcpTools() {
        List<FunctionDeclaration> mcpFunctions = new ArrayList<>();
        for (Map.Entry<String, McpSyncClient> entry : mcpServers.mcpClients.entrySet()) {
            String serverName = entry.getKey();
            McpSyncClient client = entry.getValue();
            ListToolsResult toolsResult = client.listTools();
            List<io.modelcontextprotocol.spec.McpSchema.Tool> serverTools = toolsResult.tools();
            for (io.modelcontextprotocol.spec.McpSchema.Tool mcpTool : serverTools) {
                String toolName = mcpTool.name();
                String namespacedToolName = serverName + "." + toolName;
                FunctionDeclaration geminiFunction = convertMcpToolToGeminiFunction(mcpTool, namespacedToolName);
                mcpFunctions.add(geminiFunction);
            }
        }
        return mcpFunctions;
    }

    public FunctionDeclaration convertMcpToolToGeminiFunction(io.modelcontextprotocol.spec.McpSchema.Tool mcpTool, String namespacedName) {
        String description = mcpTool.description() != null ? mcpTool.description() : "Sem descrição disponível";
        JsonNode inputSchemaNode = mcpTool.inputSchema() != null ? objectMapper.valueToTree(mcpTool.inputSchema()) : objectMapper.createObjectNode();

        Map<String, ParameterDetails> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        if (inputSchemaNode.has("properties")) {
            JsonNode propertiesNode = inputSchemaNode.get("properties");
            propertiesNode.fields().forEachRemaining(entry -> {
                String paramName = entry.getKey();
                JsonNode paramSchema = entry.getValue();
                String paramType = paramSchema.has("type") ? paramSchema.get("type").asText() : "string";
                String paramDescription = paramSchema.has("description") ? paramSchema.get("description").asText() : "Sem descrição";
                properties.put(paramName, new ParameterDetails(paramType, paramDescription));
            });
        }

        if (inputSchemaNode.has("required")) {
            JsonNode requiredNode = inputSchemaNode.get("required");
            requiredNode.forEach(node -> required.add(node.asText()));
        }

        FunctionParameters parameters = new FunctionParameters("object", properties, required);
        return new FunctionDeclaration(namespacedName, description, parameters);
    }

    public HttpRequest buildRequest(String prompt, FunctionDeclaration[] tools) throws Exception {
    	 String systemContext = buildGenericSystemPrompt();
    		    
    	String fullPrompt = systemContext + prompt;

    	GeminiRequestWithTools requestObject = new GeminiRequestWithTools(fullPrompt, tools);
        String requestBody = objectMapper.writeValueAsString(requestObject);
     //   System.out.println("JSON de Requisição: " + requestBody);

        return HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }
    
    public String processQuery(String prompt) throws Exception {
    	FunctionDeclaration[] tools = availableMcpTools.toArray(new FunctionDeclaration[0]);

        HttpRequest request = buildRequest(prompt, tools);
        HttpResponse<String> response = sendMessage(request);
        FunctionCallResult result =extractAnswer(response);
        
        if (result != null) {
            if (result.isFunctionCall()) {
                executeFunction(result.getFunctionName(), result.getArguments());
                return "";
            } else {
                return result.getText();
            }
        }
        return "";
    }

	private String buildGenericSystemPrompt() {
		StringBuilder prompt = new StringBuilder();

		prompt.append(
				"""
			    You are an AI assistant designed to use MCP (Model Context Protocol) tools.
			    Your primary function is to analyze user queries and invoke available tools to build complete and useful responses.
			    
			    KNOWLEDGE USAGE:
			    - Use your built-in knowledge to fill in missing parameters when possible
			    - For common information (city coordinates, country codes, etc.), apply your existing knowledge
			    - Only ask for clarification when you genuinely don't know the required information
			    - Combine your knowledge with tool capabilities for complete responses
			    
			    Always use tools when appropriate. If the user's intention is unclear, ask clarifying questions.

						""");

		// Informações sobre servidores conectados
		List<MCPService.ServerConfig> connectedServers = mcpServers.mcpServers.stream()
				.filter(server -> server.enabled && mcpServers.mcpClients.containsKey(server.name))
				.collect(Collectors.toList());

		// Seção de coordenação multi-MCP (apenas se múltiplos servidores)
		if (connectedServers.size() > 1) {
			prompt.append("""
					            TOOL EXECUTION METHODOLOGY:
					1. ANALYZE the user query to identify required operations
					2. PLAN the sequence of tools needed
					3. EXECUTE tools in order, using real results as inputs for subsequent tools
					4. BUILD final response using actual data from tool executions

					TOOL CHAINING RULES:
					- Execute ONE tool at a time
					- Wait for each tool to complete and return actual results
					- Use the REAL OUTPUT from Tool N as input parameter for Tool N+1
					- Continue chaining until the complete workflow is finished
					- Your final response must incorporate ACTUAL DATA from tool executions

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

					MULTI-MCP COORDINATION:
					- Plan complete workflows before execution
					- Chain tools logically: READ → PROCESS → TRANSFORM → STORE
					- Each tool receives real output from previous tool as input
					- Never save query parameters - save actual results
					- If a tool fails, inform user and ask if they want to continue

					DATA FLOW PRINCIPLE:
					Real Data → Tool A → Real Result A → Tool B → Real Result B → Tool C → Final Output

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

    public HttpResponse<String> sendMessage(HttpRequest request) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
     //   System.out.println("\nStatus Code: " + response.statusCode());
    //    System.out.println("JSON de Resposta: " + response.body());
        return response;
    }

    public FunctionCallResult extractAnswer(HttpResponse<String> response) throws Exception {
        GeminiResponseWithFunctions responseObject = objectMapper.readValue(response.body(), GeminiResponseWithFunctions.class);
        return responseObject.parseResult();
    }

    public void executeFunction(String functionName, Map<String, Object> args) {
        try {
            String serverName = mcpServers.getServerForTool(functionName);
            McpSyncClient client = mcpServers.getClient(serverName);
            String originalToolName = functionName.substring(serverName.length() + 1);
            String result = mcpService.executeToolByName(client, originalToolName, args);
            System.out.println("\n--- Resultado da Ferramenta MCP ---");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("Erro ao executar a ferramenta: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


@JsonInclude(JsonInclude.Include.NON_NULL)
class GeminiRequestWithTools {
    public Content[] contents;
    public Tools tools;

    public GeminiRequestWithTools(String prompt, FunctionDeclaration[] tools) {
        this.contents = new Content[]{new Content(prompt)};
        this.tools = new Tools(tools);
    }

    private static class Content {
        @SuppressWarnings("unused")
		public String role;
        @SuppressWarnings("unused")
		public Part[] parts;
        public Content(String prompt) {
            this.role = "user";
            this.parts = new Part[]{new Part(prompt)};
        }
    }

    private static class Part {
        @SuppressWarnings("unused")
		public String text;
        public Part(String text) {
            this.text = text;
        }
    }

    private static class Tools {
        @SuppressWarnings("unused")
		public FunctionDeclaration[] functionDeclarations;
        public Tools(FunctionDeclaration[] functionDeclarations) {
            this.functionDeclarations = functionDeclarations;
        }
    }
}


// --- CLASSE DE RESPOSTA E SUAS INNER CLASSES ---
@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiResponseWithFunctions {
    public Candidate[] candidates;
    
    public FunctionCallResult parseResult() {
        if (this.candidates != null && this.candidates.length > 0) {
            Candidate candidate = this.candidates[0];
            if (candidate.content != null && candidate.content.parts != null && candidate.content.parts.length > 0) {
                PartResponse part = candidate.content.parts[0];
                if (part.functionCall != null) {
                    return new FunctionCallResult(part.functionCall.name, part.functionCall.args);
                } else if (part.text != null) {
                    return new FunctionCallResult(part.text);
                }
            }
        }
        return null;
    }

    private static class Candidate {
        public ContentWithFunctions content;
        @SuppressWarnings("unused")
		public String finishReason;   
        @SuppressWarnings("unused")
		public Double avgLogprobs;
    }

    private static class ContentWithFunctions {
        public PartResponse[] parts;
        @SuppressWarnings("unused")
		public String role;  
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class PartResponse {
        public String text;
        public FunctionCall functionCall;
    }

    private static class FunctionCall {
        public String name;
        public Map<String, Object> args;
    }
    public static class ErrorResponse {
        public int code;
        public String message;
        public String status;
    }
}


// --- CLASSES DE USO GERAL ---

class FunctionDeclaration {
    public String name;
    public String description;
    public FunctionParameters parameters;

    public FunctionDeclaration(String name, String description, FunctionParameters parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }
}

class FunctionParameters {
    public String type;
    public Map<String, ParameterDetails> properties;
    public Object required;

    public FunctionParameters(String type, Map<String, ParameterDetails> properties, Object required) {
        this.type = type;
        this.properties = properties;
        this.required = required;
    }
}

class ParameterDetails {
    public String type;
    public String description;
    public Map<String, Object> items; // ADICIONAR ESTA LINHA

    public ParameterDetails(String type, String description) {
        this.type = type;
        this.description = description;
        
        // Se for array, adicionar items automaticamente
        if ("array".equals(type)) {
            this.items = new HashMap<>();
            this.items.put("type", "object"); // Padrão para objetos
        }
    }
}

class FunctionCallResult {
    private String text;
    private String functionName;
    private Map<String, Object> arguments;

    public FunctionCallResult(String text) {
        this.text = text;
    }

    public FunctionCallResult(String functionName, Map<String, Object> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public boolean isFunctionCall() {
        return this.functionName != null;
    }

    public String getText() {
        return text;
    }

    public String getFunctionName() {
        return functionName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}