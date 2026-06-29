package search.web.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import search.web.model.HnLink;

// Serviço para obter links do Hacker News via API Firebase
@Service
public class HackerNewsService {
    // Cliente HTTP configurado com URL base da API do Hacker News
    private final WebClient web = WebClient.builder()
            .baseUrl("https://hacker-news.firebaseio.com/v0")
            .build();

    // Obtém URLs das top stories que contêm os termos de pesquisa
    public List<HnLink> fetchTopStoryUrlsContainingTerms(String query, int max) {
        try {
            // Busca lista de IDs das top stories
            List<Integer> ids = web.get().uri("/topstories.json")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(List.class)
                    .map(this::castIntegerList)
                    .defaultIfEmpty(List.of())
                    .blockOptional().orElse(List.of());

            // Divide a query em termos para pesquisa
            String[] terms = query == null ? new String[0] : query.toLowerCase().split("\\s+");

            // Busca items em paralelo com timeout e recolhe até max resultados
            List<HnLink> out = new ArrayList<>(Math.min(max, 100));
            Flux.fromIterable(ids)
                .flatMap(id -> fetchItem(id)
                        .timeout(Duration.ofSeconds(2))
                        .onErrorResume(ex -> Mono.empty()),
                         16) // Limite de concorrência
                .takeUntil(item -> out.size() >= max)
                .doOnNext(item -> {
                    try {
                        String titleRaw = item.getOrDefault("title", "").toString();
                        String title = titleRaw.toLowerCase();
                        String text = item.getOrDefault("text", "").toString().toLowerCase();
                        String url = (String) item.get("url");
                        if (url == null || url.isBlank()) return;
                        // Verifica se todos os termos estão presentes no título ou texto
                        boolean match = true;
                        for (String t : terms) {
                            if (t.isBlank()) continue;
                            if (!title.contains(t) && !text.contains(t)) { match = false; break; }
                        }
                        if (match && out.size() < max) out.add(new HnLink(url, titleRaw));
                    } catch (Exception ignore) { }
                })
                .then()
                .block();
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // Enfileira top stories para indexação assíncrona no Gateway
    public void enqueueTopStoriesAsync(String query, int max, search.Gateway gateway) {
        new Thread(() -> {
            try {
                List<HnLink> links = fetchTopStoryUrlsContainingTerms(query, max);
                // Submete cada URL encontrado ao Gateway
                for (HnLink l : links) {
                    try { gateway.submitUrl(l.url); } catch (Exception ignore) { }
                }
            } catch (Exception ignore) { }
        }, "hn-indexer").start();
    }

    // Busca detalhes de um item específico pelo ID
    private Mono<Map> fetchItem(Integer id) {
        return web.get().uri("/item/" + id + ".json")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .filter(m -> m != null);
    }

    // Converte objeto para lista de inteiros de forma segura
    @SuppressWarnings("unchecked")
    private List<Integer> castIntegerList(Object o) {
        try { return (List<Integer>) o; } catch (Exception e) { return List.of(); }
    }
}
