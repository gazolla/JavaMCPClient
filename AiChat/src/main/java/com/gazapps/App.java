package com.gazapps;

import java.util.Scanner;

public class App {

	    public static void main(String[] args) {
	        GeminiClient client = new GeminiClient();
	        Scanner scanner = new Scanner(System.in);
	        
	        System.out.println("=== Chat with Gemini ===");
	        System.out.println("Type 'exit' to finish.");
	        System.out.println("========================");
	        
	        while (true) {
	            System.out.print("you: ");
	            String mensagem = scanner.nextLine();
	            
	            if (mensagem.equalsIgnoreCase("exit")) {
	                System.out.println("closing chat...");
	                break;
	            }
	            
	            if (mensagem.trim().isEmpty()) {
	                continue;
	            }
	            
	            try {
	                String resposta = client.enviarMensagem(mensagem);
	                System.out.println(resposta);
	                System.out.println(); 
	            } catch (Exception e) {
	                System.err.println("Error Gemini: " + e.getMessage());
	                System.out.println();
	            }
	        }
	        
	        scanner.close();
	        System.out.println("Chat ended!");
	    }
}
