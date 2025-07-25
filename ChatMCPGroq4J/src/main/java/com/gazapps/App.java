package com.gazapps;

import java.util.Scanner;

public class App {
	
	
       private GroqService service;
       
       public App() throws Exception {
    	   System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "NONE");
    	   System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    	   System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss");
    	   System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
    	   System.setProperty("org.slf4j.simpleLogger.showLogName", "false");
    	   System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
    	   this.service = new GroqService();
    	   
       }

	
	   public static void main(String[] args) throws Exception {
		    App app = new App();
	        Scanner scanner = new Scanner(System.in);
	        
	        System.out.println("Type 'exit' to leave chat");
	        System.out.println("========================");
	        
	        while (true) {
	            System.out.print("You: ");
	            String mensagem = scanner.nextLine();
	            
	            if (mensagem.equalsIgnoreCase("exit")) {
	                System.out.println("Closing chat...");
	                break;
	            }
	            
	            if (mensagem.trim().isEmpty()) {
	                continue;
	            }
	            
	            try {
	                String resposta =  app.handleLLMQuery(mensagem);
	                System.out.println(resposta);
	                System.out.println(); 
	            } catch (Exception e) {
	                System.err.println("Groq Service Error: " + e.getMessage());
	                System.out.println();
	            }
	        }
	        
	        scanner.close();
	        System.out.println("Chat ended!");
	    }

	   private String handleLLMQuery(String query) {	        
	        long startTime = System.currentTimeMillis();
	        String response = service.processQuery(query);
	        long endTime = System.currentTimeMillis();
	        
	        StringBuilder resposta = new StringBuilder();
	        
	        resposta.append("🤖 Assistent: ");
	        resposta.append(response);
	        resposta.append(String.format(" (⏱️  %.2fs)\n", (endTime - startTime) / 1000.0));
	        
	        return resposta.toString();
	    }
}
