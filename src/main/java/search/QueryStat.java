package search;

import java.io.Serializable;


//Query term stats entry for top-k reporting.
public class QueryStat implements Serializable, Comparable<QueryStat> {
    private static final long serialVersionUID = 1L;// For serialization compatibility

    public final String query;// the query
    public final long count;// number of times this query was made

    public QueryStat(String query, long count) {
        this.query = query;
        this.count = count;
    }

    // Compare by count desc, then by query asc
    @Override
    public int compareTo(QueryStat o) {
        int c = Long.compare(o.count, this.count); // desc by count
        return c != 0 ? c : this.query.compareToIgnoreCase(o.query);
    }
}
