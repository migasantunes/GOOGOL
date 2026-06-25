package search;

// Metrics for a single Barrel as observed by the Gateway.

import java.io.Serializable; // allows instances to be serialized (sent/saved)


public class BarrelMetrics implements Serializable { // Simple serializable data holder
    private static final long serialVersionUID = 1L; // Serialization version control

    public final String label;         // ex: "8183:barrel"
    public final boolean active;       // reachable at the time of report
    public final int indexSize;        // number of indexed URLs (meta size)
    public final int avgResponseDeci;  // average search time in tenths of a second

    public BarrelMetrics(String label, boolean active, int indexSize, int avgResponseDeci) { // Initialize immutable fields
        this.label = label;
        this.active = active;
        this.indexSize = indexSize;
        this.avgResponseDeci = avgResponseDeci;
    }
}
