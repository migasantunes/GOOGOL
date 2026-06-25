package search;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


// Payload sent by Downloaders to Barrels with the processed page data.
public class PageIndexData implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String url;// normalized URL
    public final String title;// title of the page
    public final String snippet;// snippet of text from the page
    public final Set<String> tokens;// distinct lower-cased words
    public final Set<String> outLinks;// absolute URLs found on the page

    public PageIndexData(String url, String title, String snippet, Set<String> tokens, Set<String> outLinks) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.tokens = Collections.unmodifiableSet(new HashSet<>(tokens));
        this.outLinks = Collections.unmodifiableSet(new HashSet<>(outLinks));
    }
}
