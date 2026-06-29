package search.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Configuração do WebSocket com STOMP (Simple Text Oriented Messaging Protocol)
// Permite comunicação bidirecional em tempo real entre o servidor e os clientes web
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    // Configura o broker de mensagens que gere a distribuição de mensagens
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Ativa um broker simples em memória para destinos que começam com "/topic"
        // Os clientes subscrevem a "/topic/stats" para receber atualizações
        registry.enableSimpleBroker("/topic");
        // Define o prefixo para mensagens enviadas do cliente para o servidor
        registry.setApplicationDestinationPrefixes("/app");
    }

    // Regista os endpoints onde os clientes podem conectar-se via WebSocket
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Cria endpoint "/ws" com fallback SockJS para browsers sem suporte WebSocket nativo
        // setAllowedOriginPatterns("*") permite conexões de qualquer origem (CORS)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
