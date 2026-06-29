package search.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import search.web.GatewayClientService;

// Controller para visualizar inlinks (backlinks) de uma página
// Inlinks são URLs de páginas que apontam para a URL especificada
// Útil para entender a estrutura de links e popularidade de páginas
@Controller
public class InlinksController {
    
    // Serviço de comunicação com o Gateway RMI
    private final GatewayClientService gatewayService;

    // Construtor com injeção de dependência
    public InlinksController(GatewayClientService gatewayService) {
        this.gatewayService = gatewayService;
    }

    // Endpoint para obter os inlinks de uma URL específica
    // Parâmetros:
    // - url: a URL da qual queremos ver os inlinks (obrigatório)
    // - q: a query de pesquisa original (opcional, para navegação)
    // - page: página atual da pesquisa (opcional, para navegação)
    @GetMapping("/inlinks")
    public String inlinks(
            @RequestParam("url") String url,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            Model model) {
        try { 
            // Consulta o Gateway para obter a lista de páginas que linkam para esta URL
            // O Gateway consulta os Index Barrels que mantêm esta informação
            model.addAttribute("inlinks", gatewayService.gateway().getInlinks(url)); 
        }
        catch (Exception e) { 
            // Em caso de erro (ex: Gateway indisponível), mostra mensagem
            model.addAttribute("error", e.getMessage()); 
        }
        // Adiciona a URL ao modelo para exibir na página
        model.addAttribute("url", url);
        // Se veio de uma pesquisa, mantém a query para permitir voltar
        if (query != null) model.addAttribute("query", query);
        // Mantém a página para navegação de retorno
        model.addAttribute("page", page);
        // Renderiza o template inlinks.html
        return "inlinks";
    }
}
