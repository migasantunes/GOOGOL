package search;

import java.rmi.registry.LocateRegistry; // Access to the RMI registry

/**
 * Shared endpoint wrapper for Barrel RMI stubs that can refresh themselves
 * from the local RMI registry when the remote object restarts.
 */
public class BarrelEndpoint {
    public final String label; // Human-readable identifier (e.g., "host:port:name")
    public final String host;  // RMI registry host
    public final int port;     // RMI registry port
    public final String name;  // Remote object name in the registry
    private volatile Barrel stub; // Cached remote stub; volatile for thread visibility

    public BarrelEndpoint(String label, String host, int port, String name, Barrel stub) {
        this.label = label;
        this.host = host;
        this.port = port;
        this.name = name;
        this.stub = stub;
    }

    public BarrelEndpoint(String host, int port, String name, Barrel stub) {
        this(host + ":" + port + ":" + name, host, port, name, stub); // Auto-generate label
    }

    public BarrelEndpoint(int port, String name, Barrel stub) {
        this("127.0.0.1", port, name, stub); // Default to localhost
    }

    public Barrel getStub() throws Exception {
        Barrel s = stub; // Fast path: read cached stub
        if (s == null) {
            synchronized (this) { // Lazy init with double-checked locking
                if (stub == null) refresh();
                s = stub;
            }
        }
        if (s == null) throw new java.rmi.RemoteException("Barrel stub unavailable for " + label); // Still unavailable
        return s; // Return valid stub
    }

    public synchronized void refresh() throws Exception {
        if (port <= 0) return; // cannot refresh without a port
        this.stub = (Barrel) LocateRegistry.getRegistry(host, port).lookup(name); // Resolve from RMI registry
    }

    @Override public String toString() { return label; } // For logging/printing
}
