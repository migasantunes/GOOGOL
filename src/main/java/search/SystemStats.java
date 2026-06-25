package search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


//Snapshot of system statistics returned by the Gateway.
public class SystemStats implements Serializable {
    private static final long serialVersionUID = 1L;// For serialization compatibility

    public final long timestampMillis;  
    public final List<QueryStat> topQueries;// top 10 queries
    public final List<BarrelMetrics> barrels;// per-barrel metrics

    public SystemStats(long timestampMillis, List<QueryStat> topQueries, List<BarrelMetrics> barrels) {
        this.timestampMillis = timestampMillis;
        this.topQueries = Collections.unmodifiableList(new ArrayList<>(topQueries));
        this.barrels = Collections.unmodifiableList(new ArrayList<>(barrels));
    }
}
