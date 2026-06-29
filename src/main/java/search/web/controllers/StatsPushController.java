package search.web.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import search.web.StatsPublisher;

// Controller REST para forçar atualização de estatísticas
// Usado internamente para trigger manual de refresh das stats
// @RestController combina @Controller + @ResponseBody (retorna dados, não views)
@RestController
public class StatsPushController {
    
    // Publisher que envia estatísticas via WebSocket para os clientes
    private final StatsPublisher publisher;
    
    // Construtor com injeção de dependência
    public StatsPushController(StatsPublisher publisher) { 
        this.publisher = publisher; 
    }

    // Endpoint interno para forçar atualização imediata das estatísticas
    // Quando chamado, o StatsPublisher busca novas stats do Gateway
    // e envia para todos os clientes conectados via WebSocket
    // Útil quando se quer atualização instantânea sem esperar pelo ciclo normal
    @PostMapping("/internal/stats/push")
    public void push() {
        // Chama o método que força refresh e broadcast das estatísticas
        publisher.refreshNow();
    }
}
