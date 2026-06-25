package search.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import search.Gateway;

import java.rmi.registry.LocateRegistry;

// Serviço que gere a conexão RMI com o Gateway
// Implementa cache da referência RMI para evitar lookups repetidos
@Service
public class GatewayClientService {
    // Configurações lidas do application.properties (ou valores por defeito)
    @Value("${gateway.host:127.0.0.1}")
    private String host;        // Host onde o Gateway RMI está a correr
    @Value("${gateway.port:8181}")
    private int port;           // Porta do registry RMI do Gateway
    @Value("${gateway.name:gateway}")
    private String name;        // Nome do objeto remoto no registry

    // Cache da referência RMI - volatile para thread-safety
    private volatile Gateway cached;

    // Obtém referência ao Gateway, usando cache se disponível
    // Padrão double-checked locking para thread-safety eficiente
    public Gateway gateway() throws Exception {
        Gateway g = cached;
        if (g != null) return g;    // Fast path: usar cache
        synchronized (this) {
            if (cached == null) {
                // Lookup no RMI Registry para obter stub do Gateway
                cached = (Gateway) LocateRegistry.getRegistry(host, port).lookup(name);
            }
            return cached;
        }
    }

    // Invalida o cache, forçando novo lookup na próxima chamada
    // Útil se o Gateway reiniciar ou mudar de endereço
    public void refresh() {
        cached = null;
    }
}
