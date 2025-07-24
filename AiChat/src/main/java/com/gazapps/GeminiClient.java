package com.gazapps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GeminiClient {
    private static final String API_KEY = System.getenv("GEMINI_API_KEY"); 
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }


    private String criarPrompt(String mensagem) throws Exception {
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", new Object[]{Map.of("text", mensagem)}
        );
        Map<String, Object> requestBody = Map.of("contents", new Object[]{content});
        return objectMapper.writeValueAsString(requestBody);
    }


    @SuppressWarnings("unchecked")
    private String extrairTextoResposta(String responseBody) throws Exception {
        // Log da resposta bruta para depuração
        //System.out.println("Resposta JSON bruta: " + responseBody);

        Map<String, Object> jsonResponse = objectMapper.readValue(responseBody, Map.class);


        if (jsonResponse.containsKey("error")) {
            throw new RuntimeException("Erro da API: " + jsonResponse.get("error"));
        }

        List<Object> candidates = (List<Object>) jsonResponse.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("A chave 'candidates' está ausente ou vazia na resposta: " + responseBody);
        }

        Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
        if (candidate == null) {
            throw new RuntimeException("O primeiro candidato está nulo na resposta: " + responseBody);
        }

        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        if (content == null) {
            throw new RuntimeException("A chave 'content' está ausente no candidato: " + responseBody);
        }

        // Log do conteúdo para depuração
        //System.out.println("Conteúdo do candidato: " + content);

        List<Object> parts = (List<Object>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("A chave 'parts' está ausente ou vazia no content: " + responseBody);
        }

        Map<String, Object> part = (Map<String, Object>) parts.get(0);
        if (part == null) {
            throw new RuntimeException("O primeiro elemento de 'parts' está nulo na resposta: " + responseBody);
        }

        String text = (String) part.get("text");
        if (text == null) {
            throw new RuntimeException("A chave 'text' está ausente na resposta: " + responseBody);
        }

        return text;
    }

    public String enviarMensagem(String mensagem) throws Exception {
        String jsonBody = criarPrompt(mensagem);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + "?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        if (response.statusCode() == 200) {
            return extrairTextoResposta(response.body());
        } else {
            throw new RuntimeException("Erro na requisição: " + response.statusCode() + " - " + response.body());
        }
    }    
}