package search.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Controller responsável pela página inicial da aplicação
// Anotação @Controller indica que esta classe gere pedidos HTTP e retorna views
@Controller
public class HomeController {
    // Home apenas com formulário de pesquisa e submissão de URL
    @GetMapping("/")
    public String home(@RequestParam(value = "msg", required = false) String msg,
                       Model model) {
        // Se existir uma mensagem, adiciona ao modelo para ser exibida na view
        if (msg != null) model.addAttribute("msg", msg);
        // Retorna o nome do template Thymeleaf a renderizar (index.html)
        return "index";
    }

    // HomeController only serves the home page
    // Other routes are handled by specific controllers.
}
