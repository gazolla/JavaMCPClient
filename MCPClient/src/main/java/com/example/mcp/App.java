package com.example.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class App {


	public static void main(String[] args) {
    
	    int requestTimeoutSeconds = 30;
	    String basePath = System.getProperty("user.home") + "/Documents";
	    
	    StdioClientTransport stdioTransport = new StdioClientTransport(ServerParameters
                .builder("cmd.exe")
                .args("/c", "npx @modelcontextprotocol/server-filesystem " + basePath)
                .build());
	    
	    McpSyncClient client = McpClient.sync(stdioTransport)
	                                    .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
	                                    .build();
	    client.initialize();
	    
	    
	    try {
	        ListToolsResult toolsResult = client.listTools();
	        List<Tool> availableTools = toolsResult.tools();
	        System.out.println("Ferramentas disponíveis: ");
	        availableTools.forEach(tool -> System.out.println("- " + tool.name()));
	    } catch (io.modelcontextprotocol.spec.McpError e) {
	        System.err.println("Erro ao listar ferramentas: " + e.getMessage());
	    }
	    
	    try {
	        ListResourcesResult resourcesResult = client.listResources();
	        List<Resource> availableResources = resourcesResult.resources();
	        System.out.println("Recursos disponíveis: ");
	        availableResources.forEach(resource -> System.out.println("- " + resource.name()));
	    } catch (io.modelcontextprotocol.spec.McpError e) {
	        System.err.println("Erro ao listar recursos: " + e.getMessage());
	    }
	    
	    try {
	        ListPromptsResult promptsResult = client.listPrompts();
	        List<Prompt> availablePrompts = promptsResult.prompts();
	        System.out.println("Prompts disponíveis: ");
	        availablePrompts.forEach(prompt -> System.out.println("- " + prompt.name()));
	    } catch (io.modelcontextprotocol.spec.McpError e) {
	        System.err.println("Erro ao listar prompts: " + e.getMessage());
	    }
	    
	    
	    CallToolRequest request = new CallToolRequest("write_file", Map.of(
	    	    "path", basePath + "\\teste.txt",
	    	    "content", "Olá!\n\nEste é um exemplo de uso do Model Context Protocol."
	    	));
	    
	    CallToolResult result = client.callTool(request);
	    
	    System.out.println(result.toString());	
	}
	    
}
