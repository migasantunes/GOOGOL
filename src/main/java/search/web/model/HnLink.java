package search.web.model;

// Representa um link do Hacker News com URL e título
public class HnLink {
    public final String url;   // URL do link
    public final String title; // Título do link

    // Cria um novo HnLink; se o título for nulo ou vazio, usa a URL como título
    public HnLink(String url, String title) {
        this.url = url;
        this.title = title == null || title.isBlank() ? url : title;
    }
}
