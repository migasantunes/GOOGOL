package search.web.services;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

// Serviço para comunicação com a API de IA da Groq
@Service
public class GroqService {
    @Value("${groq.api.key:}")
    private String apiKey; // Chave de API da Groq

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model; // Modelo de IA a utilizar

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Cria cliente HTTP com autenticação
    private WebClient client() {
        return WebClient.builder()
                .baseUrl(GROQ_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    // Gera análise/resumo com base na query e excertos de texto
    public String generateAnalysis(String query, List<String> snippets) {
        // Valida se a chave API está configurada corretamente
        if (apiKey == null || apiKey.isBlank() || !apiKey.startsWith("gsk_")) {
            return "Error: Configure the Groq key in application.properties.";
        }

        try {
            String prompt;
            // Se existem excertos, pede resumo dos mesmos
            if (snippets != null && !snippets.isEmpty()) {
                prompt = String.format(
                    "Act as a search engine summary tool. The user searched for: '%s'. " +
                    "Summarize the following text excerpts in English (US)." + 
                    "Incase you don't find any relevant information related to '%s', just explain briefly what '%s' is based on general knowledge. " +
                    "And dont start with any introductory phrases go straight to the point." +
                    "Max 100 words. Be direct.\n\nExcerpts:\n%s",
                    query, query, query,
                    String.join("\n", snippets)
                );
            } else {
                // Sem excertos, pede explicação geral do termo
                prompt = String.format(
                    "Explain briefly what '%s' is based on general knowledge. " +
                    "Respond in English (US). Max 100 words.",
                    query
                );
            }

            return callGroq(prompt);

        } catch (Exception e) {
            return "Error in AI: " + e.getMessage();
        }
    }

    // Faz chamada à API da Groq com o prompt fornecido
    private String callGroq(String prompt) {
        // Garante que o modelo não é nulo (fallback hardcoded caso a injeção falhe)
        String actualModel = (model == null || model.isBlank()) ? "llama-3.3-70b-versatile" : model;

        // Monta payload da requisição
        Map<String, Object> payload = Map.of(
            "model", actualModel,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.5
        );

        return client().post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            // Captura erros 4xx específicos da API
            .onStatus(status -> status.is4xxClientError(), response -> 
                response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Groq API Error 400: " + body))
            )
            .bodyToMono(Map.class)
            .map(res -> {
                try {
                    // Extrai conteúdo da resposta
                    List choices = (List) res.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map firstChoice = (Map) choices.get(0);
                        Map message = (Map) firstChoice.get("message");
                        Object content = message.get("content");
                        return content == null ? "No content generated." : content.toString();
                    }
                } catch (Exception e) {
                    return "Error parsing Groq response.";
                }
                return "No content generated.";
            })
            // Trata erros de conexão ou erros 4xx lançados acima
            .onErrorResume(e -> {
                return reactor.core.publisher.Mono.just("AI Connection Error: " + e.getMessage());
            })
            .block();
    }
}
