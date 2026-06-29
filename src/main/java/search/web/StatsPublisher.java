package search.web;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import search.SystemStats;

// Componente responsável por publicar estatísticas do sistema via WebSocket
// Só envia atualizações quando os dados mudam (baseado em hash)
@Component
public class StatsPublisher {
    // Serviço para comunicar com o Gateway via RMI
    private final GatewayClientService gatewayService;
    // Template para enviar mensagens para os clientes WebSocket subscritos
    private final SimpMessagingTemplate template;

    // Guarda o hash das últimas estatísticas enviadas para evitar envios duplicados
    // volatile garante visibilidade entre threads
    private volatile long lastHash = 0L;

    // Injeção de dependências via construtor (padrão Spring)
    public StatsPublisher(GatewayClientService gatewayService, SimpMessagingTemplate template) {
        this.gatewayService = gatewayService;
        this.template = template;
    }

    // Método público para forçar atualização imediata das estatísticas
    // Obtém stats do Gateway e só envia se houve alterações
    public void refreshNow() {
        try {
            // Obtém estatísticas atuais do Gateway via RMI
            SystemStats stats = gatewayService.gateway().getSystemStats();
            // Calcula hash dos dados para detetar mudanças
            long h = computeHash(stats);
            // Só envia se os dados mudaram desde a última vez
            if (h != lastHash) {
                lastHash = h;
                // Envia para todos os clientes subscritos em "/topic/stats"
                template.convertAndSend("/topic/stats", stats);
            }
        } catch (Exception ignored) { 
            // Ignora erros silenciosamente (ex: Gateway indisponível)
        }
    }

    // Calcula um hash simples das estatísticas para detetar mudanças
    // Combina queries e informação dos barrels
    private static long computeHash(SystemStats s) {
        if (s == null) return 0;
        // Inicializa com um valor constante (p. ex., 1L), não com a timestamp
        long h = 1L; 
        
        // Inclui dados das top queries no hash
        for (var q : s.topQueries) {
            h = 31*h + (q.query==null?0:q.query.hashCode());
            h = 31*h + q.count;
        }
        // Inclui dados dos barrels no hash
        for (var b : s.barrels) {
            h = 31*h + (b.label==null?0:b.label.hashCode());
            h = 31*h + (b.active?1:0);
            h = 31*h + b.indexSize;
            h = 31*h + b.avgResponseDeci;
        }
        return h;
    }
}
