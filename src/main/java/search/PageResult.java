package search;

import java.io.Serializable;

// Simplified search result
public class PageResult implements Serializable, Comparable<PageResult> {
    private static final long serialVersionUID = 1L;

    public final String url;// normalized URL
    public final String title;// title of the page
    public final String snippet;// snippet of text from the page
    public final int inlinkCount;// number of inlinks to the page

    public PageResult(String url, String title, String snippet, int inlinkCount) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.inlinkCount = inlinkCount;
    }

    // Compare by inlink count desc, then by URL asc
    @Override
    public int compareTo(PageResult o) {
        int cmp = Integer.compare(o.inlinkCount, this.inlinkCount); // desc
        if (cmp != 0) return cmp;
        return this.url.compareToIgnoreCase(o.url);
    }
}
