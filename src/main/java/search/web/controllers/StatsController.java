package search.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Controller simples para a página de estatísticas
// Apenas renderiza a view - os dados são enviados via WebSocket
@Controller
public class StatsController {
    
    // Endpoint GET para a página de estatísticas do sistema
    // Mostra métricas como: downloaders ativos, URLs processadas, etc.
    // Os dados dinâmicos são atualizados em tempo real via WebSocket
    // (gerido pelo StatsPublisher e StatsPushController)
    @GetMapping("/stats")
    public String stats() { 
        // Retorna o template stats.html
        // A página usa JavaScript para receber atualizações via WebSocket
        return "stats"; 
    }
}
