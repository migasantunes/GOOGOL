package search.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Classe principal da aplicação Spring Boot
// scanBasePackages define os pacotes onde o Spring procura componentes (@Component, @Service, etc.)
@SpringBootApplication(scanBasePackages = {"search", "search.web"})
// Ativa o agendamento de tarefas (@Scheduled) para execução periódica de métodos
@EnableScheduling
public class WebApplication {
    // Ponto de entrada da aplicação - inicia o servidor Spring Boot
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
