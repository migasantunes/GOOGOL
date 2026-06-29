package search.web.controllers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import search.PageResult;
import search.web.GatewayClientService;
import search.web.services.GroqService;
import search.web.services.HackerNewsService;

// Controller principal de pesquisa
// Gere todas as funcionalidades relacionadas com busca e indexação de conteúdo
@Controller
public class SearchController {
    
    // Serviço de comunicação com o Gateway RMI para pesquisas
    private final GatewayClientService gatewayService;
    // Serviço para buscar links do HackerNews
    private final HackerNewsService hn;
    // Serviço de IA (Groq) para gerar análises dos resultados
    private final GroqService ai;

    // Construtor com injeção de dependências
    // O Spring injeta automaticamente os três serviços
    public SearchController(GatewayClientService gatewayService, HackerNewsService hn, GroqService ai) {
        this.gatewayService = gatewayService; this.hn = hn; this.ai = ai;
    }

    // Endpoint principal de pesquisa
    // Recebe a query "q" e o número da página (default=1)
    // Retorna resultados paginados com análise de IA
    @GetMapping("/search")
    public String search(@RequestParam("q") String q,
                         @RequestParam(value = "page", defaultValue = "1") int page,
                         Model model) {
        // Adiciona a query e página atual ao modelo para a view
        model.addAttribute("query", q);
        model.addAttribute("page", page);
        try {
            // Faz a pesquisa no índice distribuído via Gateway RMI
            // Retorna lista de PageResult (url, título, snippet)
            List<PageResult> results = gatewayService.gateway().search(q, page);
            model.addAttribute("results", results);
            
            // Extrai os snippets dos primeiros 5 resultados para análise de IA
            List<String> snippets = results.stream().map(r -> r.snippet).limit(5).collect(Collectors.toList());
            // Gera uma análise/resumo dos resultados usando o Groq (LLM)
            String analysis = ai.generateAnalysis(q, snippets);
            model.addAttribute("analysis", analysis);
            
            // Lógica de paginação
            // Há página anterior se não estivermos na primeira
            boolean hasPrev = page > 1;
            // Há próxima página se tivermos 10 resultados (página cheia)
            boolean hasNext = results.size() >= 10;
            model.addAttribute("hasPrev", hasPrev);
            model.addAttribute("hasNext", hasNext);
        } catch (Exception e) {
            // Em caso de erro, mostra mensagem ao utilizador
            model.addAttribute("error", "Error on Search: " + e.getMessage());
        }
        // Renderiza o template search.html
        return "search";
    }

    // Endpoint para iniciar indexação de links do HackerNews
    // Redireciona para página de revisão onde o utilizador escolhe os links
    @PostMapping("/hn-index")
    public String hnIndex(@RequestParam("q") String q) {
        // Codifica a query para URL e redireciona para página de revisão
        return "redirect:/hn-review?q=" + UriUtils.encode(q, StandardCharsets.UTF_8);
    }

    // Página de revisão de links do HackerNews
    // Mostra os links encontrados para o utilizador selecionar quais indexar
    @GetMapping("/hn-review")
    public String hnReview(@RequestParam("q") String q, Model model) {
        model.addAttribute("query", q);
        try {
            // Busca até 100 links do HackerNews que contenham os termos da query
            var links = hn.fetchTopStoryUrlsContainingTerms(q, 100);
            model.addAttribute("hnLinks", links);
        } catch (Exception e) {
            model.addAttribute("error", "HN error: " + e.getMessage());
        }
        // Renderiza template hn-review.html
        return "hn-review";
    }

    // Endpoint REST para indexar um link individual do HackerNews
    // Chamado via AJAX quando o utilizador clica para indexar um link
    @PostMapping("/hn-index-one")
    public ResponseEntity<Void> hnIndexOne(@RequestParam("q") String q, @RequestParam("url") String url) {
        // Submete a URL ao Gateway para indexação
        // Ignora erros silenciosamente
        try { gatewayService.gateway().submitUrl(url); } catch (Exception ignored) { }
        // Retorna HTTP 204 No Content (sucesso sem corpo)
        return ResponseEntity.noContent().build();
    }
}
