package search;

import java.net.HttpURLConnection;
import java.net.URL;
import java.rmi.registry.LocateRegistry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Downloader {
    private final Gateway gateway; // RMI gateway that provides URLs and accepts newly discovered ones
    // Local set of already-processed URLs to avoid duplicate indexing within this downloader
    private final java.util.Set<String> processed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // Network and parsing limits
    private static final int HTTP_TIMEOUT_MS = 6000; // max wait per request
    private static final int MAX_BODY_BYTES = 1_500_000; // ~1.5MB cap per page
    private static final int MAX_SNIPPET_LEN = 240; // preview length

    public Downloader(Gateway gateway) {
        this.gateway = gateway; // dependency injection of gateway
    }

    public void run() {
        while (true) {
            try {
                String url = gateway.takeNextUrl(); // get next URL to process (may block or return null)
                if (url == null) { Thread.sleep(500); continue; } // no work; back off a bit
                // Skip if we've already processed this URL in this downloader instance
                if (!processed.add(url)) { continue; }
                process(url); // fetch, parse, index, and enqueue new links
            } catch (Exception e) {
                e.printStackTrace(); // keep going even if something fails
                try { Thread.sleep(500); } catch (InterruptedException ignored) { }
            }
        }
    }

    private void process(String url) {
        try {
                // Quick HEAD check to avoid non-HTML or oversized responses
                if (!isProbablyHtmlAndSmall(url)) {
                    System.out.println("Skip non-HTML/oversized: " + url);
                    return;
                }
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                    .referrer("https://www.google.com/")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9")
                    .timeout(HTTP_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .get(); // HTTP GET with browser-like headers and timeout
            String title = Optional.ofNullable(doc.title()).orElse(""); // safe title
            String text = doc.text(); // full visible text
            String snippet = text.length() > MAX_SNIPPET_LEN ? text.substring(0, MAX_SNIPPET_LEN) + "…" : text; // small preview

            Set<String> tokens = tokenize(text); // words to index
            Set<String> links = extractLinks(doc); // outgoing links

            // enqueue discovered links
            for (String l : links) gateway.submitUrl(l); // push back into frontier

            PageIndexData data = new PageIndexData(url, title, snippet, tokens, links); // payload for barrels
            // envia via gateway: exige confirmação de TODOS os barrels
            boolean allOk;
            try { allOk = gateway.indexPage(data); } catch (Exception e) { allOk = false; }
            if (!allOk) {
                // Nenhum barrel confirmou — re-enfileira para tentar mais tarde
                try { gateway.submitUrl(url); } catch (Exception ignore) { }
            }
            System.out.printf("Indexed: %s (%d words, %d links)\n", url, tokens.size(), links.size()); // progress log
        } catch (Exception e) {
            System.err.println("Failed to fetch " + url + ": " + e.getMessage()); // fetch/parse errors
        }
    }

    // HEAD preflight to filter obviously unsuitable downloads
    private static boolean isProbablyHtmlAndSmall(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9");
            int code = conn.getResponseCode();
            if (code >= 400) return false;
            String ct = conn.getContentType();
            if (ct == null) ct = "";
            ct = ct.toLowerCase(Locale.ROOT);
            boolean html = ct.startsWith("text/html") || ct.contains("html");
            int len = conn.getContentLength();
            if (len > 0 && len > MAX_BODY_BYTES) return false;
            return html;
        } catch (Exception e) {
            // If HEAD fails, allow GET to attempt with size cap and timeout
            return true;
        }
    }

    private static Set<String> tokenize(String text) {
        Set<String> set = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9áàãâéêíóôõúç]+")) { // split on non-word chars (allow some accents)
            if (w.isBlank()) continue;
            set.add(w);
            if (set.size() >= 1000) break; // cap per page to avoid huge payloads
        }
        return set;
    }

    private static Set<String> extractLinks(Document doc) {
        Set<String> out = new HashSet<>();
        Elements links = doc.select("a[href]"); // all anchor tags with href
        for (Element link : links) {
            String abs = link.attr("abs:href"); // absolute URL resolved by Jsoup
            if (abs == null || abs.isBlank()) continue;
            out.add(abs);
            if (out.size() >= 2000) break; // cap per page
        }
        return out;
    }

    public static void main(String[] args) {
        try {
            int gPort = Integer.parseInt(System.getProperty("gateway.port", "8181")); // RMI port of gateway
            String gName = System.getProperty("gateway.name", "gateway"); // RMI name of gateway
            String gHost = System.getProperty("gateway.host", "127.0.0.1"); // host of registry
            Gateway gw = (Gateway) LocateRegistry.getRegistry(gHost, gPort).lookup(gName); // lookup gateway stub
            new Downloader(gw).run(); // start worker loop
        } catch (Exception e) {
            e.printStackTrace(); // fail fast on startup issues
        }
    }
}
