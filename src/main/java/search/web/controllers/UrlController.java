package search.web.controllers;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import search.web.GatewayClientService;

// Controller responsável pela submissão de URLs para indexação
// Permite aos utilizadores adicionar novas URLs à fila de crawling
// do sistema de pesquisa distribuído
@Controller
public class UrlController {

    // Serviço que fornece acesso ao Gateway RMI
    // O Gateway é o ponto central que distribui URLs para os Downloaders
    private final GatewayClientService gatewayService;

// Construtor com injeção de dependência
    // O Spring injeta automaticamente o GatewayClientService
    public UrlController(GatewayClientService gatewayService) {
        this.gatewayService = gatewayService;
    }

// Endpoint POST para submeter uma URL para indexação
    // Recebe a URL do formulário HTML e envia para o sistema distribuído
    // Fluxo: Browser -> Spring -> Gateway (RMI) -> Downloaders
    @PostMapping("/submit")
    public String submit(@RequestParam("url") String url) {
        // Tenta submeter a URL ao Gateway 
        // Em caso de erro, ignora para não perturbar a UX do utilizador
        try { gatewayService.gateway().submitUrl(url); } catch (Exception ignored) { }
        String msg = UriUtils.encode("URL queued", StandardCharsets.UTF_8);
        return "redirect:/?msg=" + msg;
    }
}
