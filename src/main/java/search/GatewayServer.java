package search;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GatewayServer extends UnicastRemoteObject implements Gateway {
    private static final long serialVersionUID = 1L;

    // In-memory queue of URLs to be crawled
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    // Set of URLs already seen/enqueued (cluster-wide dedupe via Gateway)
    private final java.util.Set<String> seenUrls = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Thread-safe list of registered Barrel endpoints (can be refreshed)
    private final List<BarrelEndpoint> barrels = new CopyOnWriteArrayList<>();
    // Random used to shuffle barrel selection for load spreading
    private final Random rnd = new Random();
    // Global flag to pause handing URLs to crawlers
    private final java.util.concurrent.atomic.AtomicBoolean crawlPaused = new java.util.concurrent.atomic.AtomicBoolean(false);

    // Per-query counters (normalized)
    private final ConcurrentMap<String, java.util.concurrent.atomic.LongAdder> queryCounts = new ConcurrentHashMap<>();
    // Accumulated latency per barrel (milliseconds)
    private final ConcurrentMap<String, java.util.concurrent.atomic.LongAdder> barrelTimeMs = new ConcurrentHashMap<>();
    // Remote call counts per barrel
    private final ConcurrentMap<String, java.util.concurrent.atomic.LongAdder> barrelCalls = new ConcurrentHashMap<>();
    // Persist throttling to avoid excessive disk writes
    private int pendingSaves = 0;

    // Backwards-compat constructor: wraps provided Barrel stubs into endpoints
    public GatewayServer(List<Barrel> initialBarrels) throws RemoteException {
        super();
        // Kept for backward compatibility but we prefer Endpoint instances created in main
        if (initialBarrels != null) {
            int i = 0;
            for (Barrel b : initialBarrels) {
                String label = "barrel-" + (++i);
                barrels.add(new BarrelEndpoint(label, -1, "barrel", b));
            }
        }
    }

    // Persistência simples do estado (queue + seenUrls). Serialização.
    private final java.nio.file.Path stateFile = java.nio.file.Paths.get("gateway-state.ser");
    private static class GatewayState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        List<String> queue;
        java.util.Set<String> seen;
        java.util.Map<String, Long> queries; // normalized query -> count
    }
    private synchronized void saveState() {
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(java.nio.file.Files.newOutputStream(stateFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
            GatewayState st = new GatewayState();
            st.queue = new ArrayList<>(queue);
            st.seen = new java.util.HashSet<>(seenUrls);
            // snapshot query counts (LongAdder -> long)
            java.util.Map<String, Long> qc = new java.util.HashMap<>();
            for (var e : queryCounts.entrySet()) {
                qc.put(e.getKey(), e.getValue().sum());
            }
            st.queries = qc;
            oos.writeObject(st);
        } catch (Exception e) { System.err.println("Gateway saveState falhou: " + e.getMessage()); }
    }
    private synchronized void saveStateThrottled() {
        if (++pendingSaves < 20) return; // flush every 20 events
        pendingSaves = 0;
        saveState();
    }
    private synchronized void loadState() {
        if (!java.nio.file.Files.exists(stateFile)) return;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(java.nio.file.Files.newInputStream(stateFile))) {
            Object o = ois.readObject();
            if (o instanceof GatewayState st) {
                queue.clear(); queue.addAll(st.queue);
                seenUrls.clear(); seenUrls.addAll(st.seen);
                // restore query counts
                if (st.queries != null) {
                    queryCounts.clear();
                    for (var e : st.queries.entrySet()) {
                        java.util.concurrent.atomic.LongAdder la = new java.util.concurrent.atomic.LongAdder();
                        if (e.getValue() != null && e.getValue() > 0) la.add(e.getValue());
                        queryCounts.put(e.getKey(), la);
                    }
                }
                System.out.printf("Gateway carregou estado: %d na fila, %d vistos.%n", queue.size(), seenUrls.size());
            }
        } catch (Exception e) { System.err.println("Gateway loadState falhou: " + e.getMessage()); }
    }

    @Override
    public List<PageResult> search(String query, int page) throws RemoteException {
        // Count normalized queries for stats
        String normalized = normalize(query);
        if (!normalized.isBlank()) {
            queryCounts.computeIfAbsent(normalized, k -> new java.util.concurrent.atomic.LongAdder()).increment();
            saveStateThrottled();
        }
        // Call a barrel (random order), track latency and calls; fallback to empty list
        List<PageResult> result = withBarrelFallback(ref -> {
            long start = System.nanoTime();
            List<PageResult> out = ref.getStub().search(query, page, 10);
            long durMs = (System.nanoTime() - start) / 1_000_000L;
            barrelTimeMs.computeIfAbsent(ref.label, k -> new java.util.concurrent.atomic.LongAdder()).add(durMs);
            barrelCalls.computeIfAbsent(ref.label, k -> new java.util.concurrent.atomic.LongAdder()).increment();
            return out;
        }, Collections.emptyList());
        try { UiStatsPusher.push(); } catch (Exception ignore) {}
        return result;
    }

    @Override
    public List<String> getInlinks(String url) throws RemoteException {
        // Ask any barrel for inlinks; empty list if all fail
        return withBarrelFallback(ref -> ref.getStub().getInlinks(url), Collections.emptyList());
    }

    @Override
    public void submitUrl(String url) throws RemoteException {
        // Enqueue URL for crawlers (ignore null/blank) with de-duplication
        if (url == null) return;
        String u = url.trim();
        if (u.isBlank()) return;
        // Only enqueue if not seen before
        if (seenUrls.add(u)) {
            queue.offer(u);
            // Throttle persistence to avoid blocking on every enqueue
            saveStateThrottled();
        }
    }

    @Override
    public String takeNextUrl() throws RemoteException {
        // Dequeue next URL unless paused
        if (crawlPaused.get()) return null;
        String out = queue.poll();
        if (out != null) {
            // Throttle persistence to avoid blocking on every dequeue
            saveStateThrottled();
        }
        return out;
    }

    // Helper to try barrels in random order; refresh once on failure; return fallback if all fail
    private <T> T withBarrelFallback(BarrelCall<T> call, T fallback) {
    List<BarrelEndpoint> candidates = new ArrayList<>(barrels);
        if (candidates.isEmpty()) return fallback;
        Collections.shuffle(candidates, rnd);
        for (BarrelEndpoint ref : candidates) {
            try {
                return call.apply(ref);
            } catch (Exception first) {
                // Attempt to refresh once and retry this endpoint
                try { ref.refresh(); } catch (Exception ignore) { }
                try { return call.apply(ref); } catch (Exception ignored) { }
            }
        }
        return fallback;
    }

    // Small functional interface to abstract a call using a BarrelEndpoint
    @FunctionalInterface private interface BarrelCall<T> { T apply(BarrelEndpoint ref) throws Exception; }

    // Normalize text for stats (trim + lowercase)
    private static String normalize(String q) {
        return q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static void main(String[] args) {
        try {
            // Inicialmente sem barrels predefinidos; novos barrels registam-se dinamicamente via registerBarrel

            // Start and bind the Gateway RMI service
            int port = Integer.parseInt(System.getProperty("gateway.port", "8181"));
            String name = System.getProperty("gateway.name", "gateway");
            // Determine the host to advertise in RMI stubs
            String bindHost = System.getProperty("java.rmi.server.hostname");
            if (bindHost == null || bindHost.isBlank()) {
                String gHost = System.getProperty("gateway.host", "").trim();
                if (!gHost.isBlank()) bindHost = gHost;
            }
            if (bindHost == null || bindHost.isBlank()) {
                bindHost = detectNonLoopbackHost();
            }
            if (bindHost != null && !bindHost.isBlank()) {
                System.setProperty("java.rmi.server.hostname", bindHost);
            }
            GatewayServer server = new GatewayServer(null);
            server.loadState();
            // Guardar estado no shutdown para persistir contadores de queries mesmo com poucas pesquisas
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { server.saveState(); } catch (Exception ignore) {}
            }, "gateway-shutdown-save"));
            Registry reg = LocateRegistry.createRegistry(port);
            reg.rebind(name, server);
            System.out.printf("Gateway ready on %s:%d bound as '%s'. Aguarda registos de Barrels...%n", 
                    System.getProperty("java.rmi.server.hostname", "127.0.0.1"), port, name);
            // Block forever to keep the process alive
            final Object blocker = new Object();
            synchronized (blocker) { blocker.wait(); }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Attempts to find a non-loopback IPv4 address for this machine
    private static String detectNonLoopbackHost() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress ia = addrs.nextElement();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                        return ia.getHostAddress();
                    }
                }
            }
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    @Override
    public SystemStats getSystemStats() throws RemoteException {
        // Build top 10 queries (by normalized text)
        List<QueryStat> top = new ArrayList<>();
        for (var e : queryCounts.entrySet()) {
            top.add(new QueryStat(e.getKey(), e.getValue().sum()));
        }
        top.sort(null);
        if (top.size() > 10) top = new ArrayList<>(top.subList(0, 10));

        // Gather per-barrel metrics (availability, indexed size, avg latency)
        List<BarrelMetrics> metrics = new ArrayList<>();
        for (BarrelEndpoint ref : barrels) {
            boolean active = false;
            int size = 0;
            try {
                size = ref.getStub().getIndexedUrlCount();
                active = true;
            } catch (Exception ignored) { }
            long totalMs = barrelTimeMs.getOrDefault(ref.label, new java.util.concurrent.atomic.LongAdder()).sum();
            long calls = barrelCalls.getOrDefault(ref.label, new java.util.concurrent.atomic.LongAdder()).sum();
            int avgDeci = (calls == 0) ? 0 : (int)Math.round((totalMs / (double)calls) / 100.0); // average latency in 0.1 s
            metrics.add(new BarrelMetrics(ref.label, active, size, avgDeci));
        }
        return new SystemStats(System.currentTimeMillis(), top, metrics);
    }

    @Override
    public void setCrawlPaused(boolean paused) throws RemoteException {
        // Toggle crawl pause state
        crawlPaused.set(paused);
    }

    @Override
    public boolean isCrawlPaused() throws RemoteException {
        // Read crawl pause state
        return crawlPaused.get();
    }

    @Override
    public void registerBarrel(String host, int port, String name) throws RemoteException {
        String label = host + ":" + port + ":" + name;
        // evitar duplicados
        for (BarrelEndpoint be : barrels) if (be.label.equals(label)) return;
        try {
            Barrel b = (Barrel) LocateRegistry.getRegistry(host, port).lookup(name);
            barrels.add(new BarrelEndpoint(label, host, port, name, b));
            System.out.println("Gateway: Barrel registado " + label);
            try { UiStatsPusher.push(); } catch (Exception ignore) {}
            // Attempt backfill of index to the newly registered barrel
            boolean pauseDuringBackfill = Boolean.parseBoolean(System.getProperty("gateway.pauseDuringBackfill", "true"));
            if (pauseDuringBackfill) {
                try { setCrawlPaused(true); } catch (Exception ignore) { }
            }
            Thread t = new Thread(() -> {
                try { asyncBackfillTo(label); } finally {
                    if (pauseDuringBackfill) {
                        try { setCrawlPaused(false); } catch (Exception ignore) { }
                    }
                    try { UiStatsPusher.push(); } catch (Exception ignore) {}
                }
            }, "gateway-backfill-wrapper-" + label);
            t.start();
        } catch (Exception e) {
            // Regista endpoint vazio para futura recuperação
            barrels.add(new BarrelEndpoint(label, host, port, name, null));
            System.err.println("Gateway: Barrel " + label + " registado mas stub inacessível agora: " + e.getMessage());
            try { UiStatsPusher.push(); } catch (Exception ignore) {}
        }
    }

    // Launches a background task to copy index data from an existing active barrel
    private void asyncBackfillTo(String targetLabel) {
        new Thread(() -> {
            BarrelEndpoint target = null;
            for (BarrelEndpoint be : barrels) {
                if (be.label.equals(targetLabel)) { target = be; break; }
            }
            if (target == null) return;
            try {
                // Ensure target is reachable
                Barrel targetStub = target.getStub();
                // Pick a source barrel with data (not the same as target)
                BarrelEndpoint source = null;
                for (BarrelEndpoint be : barrels) {
                    if (be == target) continue;
                    try {
                        Barrel s = be.getStub();
                        if (s.getIndexedUrlCount() > 0) { source = be; break; }
                    } catch (Exception ignored) { }
                }
                if (source == null) return; // nothing to copy
                Barrel srcStub = source.getStub();
                final int limit = 500;
                int offset = 0;
                while (true) {
                    java.util.List<PageIndexData> chunk = java.util.Collections.emptyList();
                    try {
                        chunk = srcStub.exportAll(offset, limit);
                    } catch (Exception ex) {
                        // try refresh once on either side
                        try { source.refresh(); } catch (Exception ignore) {}
                        try { target.refresh(); } catch (Exception ignore) {}
                        try { chunk = source.getStub().exportAll(offset, limit); } catch (Exception ignore) { break; }
                    }
                    if (chunk == null || chunk.isEmpty()) break;
                    for (PageIndexData data : chunk) {
                        try { targetStub.addToIndex(data); } catch (Exception ex) {
                            try { targetStub = target.getStub(); targetStub.addToIndex(data); } catch (Exception ignore) { /* skip */ }
                        }
                    }
                    offset += chunk.size();
                }
                System.out.println("Gateway: Backfill concluído para " + target.label);
            } catch (Exception ignored) {
                // silently ignore backfill issues
            }
        }, "gateway-backfill-" + targetLabel).start();
    }

    @Override
    public boolean indexPage(PageIndexData data) throws RemoteException {
        List<BarrelEndpoint> list = new ArrayList<>(barrels);
        if (list.isEmpty()) return false;
        boolean allOk = true;
        for (BarrelEndpoint endpoint : list) {
            boolean delivered = false;
            try {
                delivered = endpoint.getStub().addToIndex(data);
                barrelCalls.computeIfAbsent(endpoint.label, k -> new java.util.concurrent.atomic.LongAdder()).increment();
            } catch (Exception ex) {
                try { endpoint.refresh(); } catch (Exception ignore) { }
                try {
                    delivered = endpoint.getStub().addToIndex(data);
                    barrelCalls.computeIfAbsent(endpoint.label, k -> new java.util.concurrent.atomic.LongAdder()).increment();
                } catch (Exception ignored) { delivered = false; }
            }
            if (!delivered) allOk = false;
        }
        try { UiStatsPusher.push(); } catch (Exception ignore) {}
        return allOk;
    }
}

