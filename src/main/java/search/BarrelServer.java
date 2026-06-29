package search;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class BarrelServer extends UnicastRemoteObject implements Barrel {
    private static final long serialVersionUID = 1L;

    // word -> set(url)
    private final ConcurrentMap<String, Set<String>> inverted = new ConcurrentHashMap<>();
    // url -> set(url inlinks)
    private final ConcurrentMap<String, Set<String>> inlinks = new ConcurrentHashMap<>();
    // url -> meta
    private final ConcurrentMap<String, PageResult> meta = new ConcurrentHashMap<>();

    // Nome lógico usado para ficheiros de persistência
    private final String barrelName;
    private final java.nio.file.Path stateFile;
    private int pendingWrites = 0; // simples throttling

    private static class BarrelState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        Map<String, Set<String>> inverted;
        Map<String, Set<String>> inlinks;
        Map<String, PageResult> meta;
    }

    public BarrelServer(String name) throws RemoteException { 
        super(); 
        this.barrelName = name == null ? "barrel" : name;
        this.stateFile = java.nio.file.Paths.get(barrelName + "-state.ser");
        loadState();
    }

    private synchronized void saveState() {
        // escreve cada N alterações para reduzir custo
        if (++pendingWrites < 10) return; // flush a cada 10 páginas
        pendingWrites = 0;
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(java.nio.file.Files.newOutputStream(stateFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
            BarrelState st = new BarrelState();
            st.inverted = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : inverted.entrySet()) {
                st.inverted.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
            }
            st.inlinks = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : inlinks.entrySet()) {
                st.inlinks.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
            }
            st.meta = new HashMap<>(meta);
            oos.writeObject(st);
        } catch (Exception e) {
            System.err.println("Barrel saveState falhou: " + e.getMessage());
        }
    }

    // Força escrita imediata ignorando throttling
    private synchronized void forceSave() {
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(java.nio.file.Files.newOutputStream(stateFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
            BarrelState st = new BarrelState();
            st.inverted = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : inverted.entrySet()) {
                st.inverted.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
            }
            st.inlinks = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : inlinks.entrySet()) {
                st.inlinks.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
            }
            st.meta = new HashMap<>(meta);
            oos.writeObject(st);
        } catch (Exception e) {
            System.err.println("Barrel forceSave falhou: " + e.getMessage());
        }
    }

    private synchronized void loadState() {
        if (!java.nio.file.Files.exists(stateFile)) return;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(java.nio.file.Files.newInputStream(stateFile))) {
            Object o = ois.readObject();
            if (o instanceof BarrelState st) {
                inverted.clear();
                for (Map.Entry<String, Set<String>> e : st.inverted.entrySet()) {
                    Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
                    set.addAll(e.getValue());
                    inverted.put(e.getKey(), set);
                }
                inlinks.clear();
                for (Map.Entry<String, Set<String>> e : st.inlinks.entrySet()) {
                    Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
                    set.addAll(e.getValue());
                    inlinks.put(e.getKey(), set);
                }
                meta.clear(); meta.putAll(st.meta);
                System.out.printf("Barrel '%s' carregou estado: %d URLs indexados.%n", barrelName, meta.size());
            }
        } catch (Exception e) {
            System.err.println("Barrel loadState falhou: " + e.getMessage());
        }
    }

    @Override
    public boolean addToIndex(PageIndexData data) throws RemoteException {
        // store meta (snippet/title may be updated)
        meta.put(data.url, new PageResult(data.url,
                data.title == null ? "" : data.title,
                data.snippet == null ? "" : data.snippet,
                getInlinkCount(data.url)));

        // update inverted index
        for (String token : data.tokens) {
            inverted.compute(token, (k, set) -> {
                if (set == null) set = ConcurrentHashMap.newKeySet();
                set.add(data.url);
                return set;
            });
        }

        // update inlinks for each out link
        for (String target : data.outLinks) {
            inlinks.compute(target, (k, set) -> {
                if (set == null) set = ConcurrentHashMap.newKeySet();
                set.add(data.url);
                return set;
            });
            // Update target page's meta inlink count if it exists
            int targetCnt = getInlinkCount(target);
            meta.computeIfPresent(target, (k, pr) -> new PageResult(pr.url, pr.title, pr.snippet, targetCnt));
        }

        // refresh its inlink count in meta for ranking
        int inCnt = getInlinkCount(data.url);
        meta.computeIfPresent(data.url, (k, pr) -> new PageResult(pr.url, pr.title, pr.snippet, inCnt));
        saveState();
        return true;
    }

    @Override
    public List<PageResult> search(String query, int page, int pageSize) throws RemoteException {
        if (query == null || query.isBlank()) return Collections.emptyList();
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);

        String[] terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (terms.length == 0) return Collections.emptyList();

        // intersect url sets for all terms
        Set<String> resultUrls = null;
        for (String t : terms) {
            Set<String> urls = inverted.getOrDefault(t, Collections.emptySet());
            if (resultUrls == null) resultUrls = new HashSet<>(urls);
            else {
                resultUrls.retainAll(urls);
            }
            if (resultUrls.isEmpty()) break;
        }
        if (resultUrls == null || resultUrls.isEmpty()) return Collections.emptyList();

        List<PageResult> results = resultUrls.stream()
                .map(u -> meta.getOrDefault(u, new PageResult(u, u, "", getInlinkCount(u))))
                .sorted()
                .collect(Collectors.toList());

        int from = Math.min(results.size(), (page - 1) * pageSize);
        int to = Math.min(results.size(), from + pageSize);
        if (from >= to) return Collections.emptyList();
        return new ArrayList<>(results.subList(from, to));
    }

    @Override
    public List<String> getInlinks(String url) throws RemoteException {
        return new ArrayList<>(inlinks.getOrDefault(url, Collections.emptySet()));
    }

    private int getInlinkCount(String url) {
        return inlinks.getOrDefault(url, Collections.emptySet()).size();
    }

    @Override
    public int getIndexedUrlCount() throws RemoteException {
        return meta.size();
    }

    @Override
    public List<PageIndexData> exportAll(int offset, int limit) throws RemoteException {
        // Deterministic list of URLs
        List<String> urls = new ArrayList<>(meta.keySet());
        Collections.sort(urls);
        int n = urls.size();
        if (offset < 0) offset = 0;
        if (limit <= 0) limit = 100;
        if (offset >= n) return Collections.emptyList();
        int to = Math.min(n, offset + limit);
        List<String> slice = urls.subList(offset, to);

        // Precompute selected set for faster membership tests
        Set<String> selected = new HashSet<>(slice);

        // Reconstruct tokens per URL for selected URLs
        Map<String, Set<String>> tokensByUrl = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : inverted.entrySet()) {
            String token = e.getKey();
            for (String u : e.getValue()) {
                if (!selected.contains(u)) continue;
                tokensByUrl.computeIfAbsent(u, k -> new HashSet<>()).add(token);
            }
        }

        // Reconstruct outlinks per URL from inlinks map (target -> sources)
        Map<String, Set<String>> outBySource = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : inlinks.entrySet()) {
            String target = e.getKey();
            for (String src : e.getValue()) {
                if (!selected.contains(src)) continue;
                outBySource.computeIfAbsent(src, k -> new HashSet<>()).add(target);
            }
        }

        List<PageIndexData> out = new ArrayList<>(slice.size());
        for (String u : slice) {
            PageResult pr = meta.get(u);
            String title = pr == null ? u : pr.title;
            String snippet = pr == null ? "" : pr.snippet;
            Set<String> toks = tokensByUrl.getOrDefault(u, Collections.emptySet());
            Set<String> outs = outBySource.getOrDefault(u, Collections.emptySet());
            out.add(new PageIndexData(u, title, snippet, toks, outs));
        }
        return out;
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8183;
            String name = args.length > 1 ? args[1] : "barrel";
            // Determine the externally reachable host for RMI stubs
            String configuredHost = System.getProperty("barrel.host", "").trim();
            String rmiHost = configuredHost.isBlank() ? detectNonLoopbackHost() : configuredHost;
            if (rmiHost != null && !rmiHost.isBlank()) {
                System.setProperty("java.rmi.server.hostname", rmiHost);
            }
            BarrelServer server = new BarrelServer(name);
            // Guardar estado no shutdown para persistir índices pequenos
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { server.forceSave(); } catch (Exception ignore) {}
            }, "barrel-shutdown-save"));
            Registry reg = LocateRegistry.createRegistry(port);
            reg.rebind(name, server);
            System.out.printf("BarrelServer ready on %s:%d bound as '%s'%n", 
                    System.getProperty("java.rmi.server.hostname", "127.0.0.1"), port, name);
            // Registo dinâmico no Gateway
            try {
                int gPort = Integer.parseInt(System.getProperty("gateway.port", "8181"));
                String gName = System.getProperty("gateway.name", "gateway");
                String gHost = System.getProperty("gateway.host", "127.0.0.1");
                Gateway gw = (Gateway) LocateRegistry.getRegistry(gHost, gPort).lookup(gName);
                gw.registerBarrel(rmiHost == null || rmiHost.isBlank() ? "127.0.0.1" : rmiHost, port, name);
                System.out.println("Barrel registado dinamicamente no Gateway.");
            } catch (Exception e) {
                System.err.println("Falha ao registar Barrel no Gateway: " + e.getMessage());
            }
            // Keep JVM alive: RMI export threads can be daemon threads, so block main.
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
            // Fallback to local host address
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
