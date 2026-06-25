package search;

import java.net.HttpURLConnection;
import java.net.URL;

// Classe utilitária para notificar a UI de que as estatísticas mudaram
// Usa HTTP POST fire-and-forget - não espera resposta

public final class UiStatsPusher {
    
    // URL por defeito construído dinamicamente com base nas configurações
    private static final String DEFAULT_URL = buildDefaultUrl();
    
    // Timestamp do último push para implementar coalescing (agrupamento de pedidos)
    // volatile para garantir visibilidade entre threads
    private static volatile long lastFireTs = 0L;

    // Construtor privado - classe utilitária com métodos estáticos apenas
    private UiStatsPusher() {}

    // Método principal: notifica a UI de que há novas estatísticas
    // Implementa coalescing: se chamado várias vezes em menos de 100ms,
    // só o primeiro pedido é enviado (evita sobrecarga)
    public static void push() {
        // Coalescing leve: evita disparar mais de uma vez a cada 100ms
        long now = System.nanoTime();
        if ((now - lastFireTs) < 100_000_000L) return;  // 100ms
        lastFireTs = now;
        
        // Obtém URL do endpoint, com fallback para o valor por defeito
        final String url = System.getProperty("ui.push.url", getenvOrDefault("UI_PUSH_URL", DEFAULT_URL));
        
        // Cria thread daemon para fazer o POST de forma assíncrona
        // Daemon thread: não impede a JVM de terminar
        Thread t = new Thread(() -> doPost(url), "ui-stats-push");
        t.setDaemon(true);
        t.start();
    }

    // Método auxiliar: obtém variável de ambiente com valor por defeito
    private static String getenvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    // Executa o HTTP POST para o endpoint da UI
    // Best-effort: ignora erros silenciosamente (UI pode não estar disponível)
    private static void doPost(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            // Timeouts curtos para não bloquear - é apenas notificação
            con.setConnectTimeout(1000);    // 1 segundo para conectar
            con.setReadTimeout(1000);       // 1 segundo para ler resposta
            con.setDoOutput(true);
            
            // Escreve body vazio para evitar erro 411 (Length Required) em servidores estritos
            byte[] body = new byte[0];
            con.getOutputStream().write(body);
            con.getOutputStream().flush();
            con.getOutputStream().close();
            
            // Lê código de resposta (força envio do pedido)
            int code = con.getResponseCode();
            con.disconnect();
        } catch (Exception ignore) {
            // Best-effort: ignora todos os erros
            // A UI pode não estar a correr, rede pode falhar, ...
        }
    }

    // Constrói URL por defeito com base nas configurações disponíveis

    private static String buildDefaultUrl() {
        // Permite especificar apenas o host via system property ou env var
        String host = System.getProperty("ui.push.host", getenvOrDefault("UI_PUSH_HOST", "localhost"));
        if (host == null || host.isBlank()) host = "localhost";
        
        // Normaliza o input
        host = host.trim();
        
        // Se um URL completo foi passado acidentalmente via host, respeita-o
        if (host.startsWith("http://") || host.startsWith("https://")) {
            // Garante que termina com o endpoint correto
            if (host.endsWith("/internal/stats/push")) return host;
            // Remove barra final se existir e adiciona endpoint
            String base = host.endsWith("/") ? host.substring(0, host.length()-1) : host;
            return base + "/internal/stats/push";
        }
        
        // Caso normal: constrói URL completo com host, porta 8080 e endpoint
        return "http://" + host + ":8080/internal/stats/push";
    }
}
