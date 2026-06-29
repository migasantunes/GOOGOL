package search.web;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

// Componente que escuta eventos de subscrição WebSocket
// Quando um cliente subscreve "/topic/stats", envia imediatamente os dados atuais
// Isto evita que o cliente fique à espera da próxima atualização periódica
@Component
public class StatsConnectNotifier {
    private final GatewayClientService gatewayService;
    private final SimpMessagingTemplate template;

    public StatsConnectNotifier(GatewayClientService gatewayService, SimpMessagingTemplate template) {
        this.gatewayService = gatewayService;
        this.template = template;
    }

    // Método invocado automaticamente quando um cliente STOMP subscreve um tópico
    @EventListener
    public void onSubscribe(SessionSubscribeEvent evt) {
        try {
            // Extrai informação do cabeçalho STOMP da mensagem de subscrição
            StompHeaderAccessor sha = StompHeaderAccessor.wrap(evt.getMessage());
            String destination = sha.getDestination();
            // Verifica se o cliente está a subscrever o tópico de estatísticas
            if (destination != null && destination.equals("/topic/stats")) {
                // Obtém estatísticas atuais do Gateway
                var stats = gatewayService.gateway().getSystemStats();
                // Envia imediatamente para que o cliente tenha dados logo ao conectar
                if (stats != null) template.convertAndSend("/topic/stats", stats);
            }
        } catch (Exception ignored) { 
            // Ignora erros (ex: Gateway não disponível)
        }
    }
}