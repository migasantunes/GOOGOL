package search; // Client CLI for an RMI-based search system

import java.rmi.registry.LocateRegistry;
import java.util.List;
import java.util.Scanner;

public class Client { // Simple interactive console client
    private static final Scanner SC = new Scanner(System.in); // Console input reader

    public static void main(String[] args) {
        try {
            // Load gateway connection settings from system properties (with defaults)
            int port = Integer.parseInt(System.getProperty("gateway.port", "8181"));
            String name = System.getProperty("gateway.name", "gateway");
            String host = System.getProperty("gateway.host", "127.0.0.1");
            Gateway gw = (Gateway) LocateRegistry.getRegistry(host, port).lookup(name); // RMI lookup for Gateway stub

            while (true) { // Main menu loop
                System.out.println("\nGoogol Client\n1) Submit URL\n2) Search\n3) Show inlinks for URL\n4) System stats\n0) Exit");
                System.out.print("> ");
                String opt = SC.nextLine().trim(); // Read user option
                switch (opt) {
                    case "1": // Submit a URL to be processed/crawled
                        System.out.print("URL: ");
                        String url = SC.nextLine().trim();
                        gw.submitUrl(url); // Remote call
                        System.out.println("Submitted.");
                        break;
                    case "2": // Search for terms with simple pagination
                        System.out.print("Terms: ");
                        String terms = SC.nextLine();
                        int page = 1;
                        while (true) { // Pagination loop
                            List<PageResult> res = gw.search(terms, page); // Remote search
                            if (res.isEmpty()) {
                                System.out.println(page == 1 ? "No results." : "No more results.");
                                break;
                            }
                            System.out.printf("-- Page %d --\n", page);
                            int i = 1;
                            for (PageResult r : res) { // Print each result
                                System.out.printf("%d. %s (%d)\n   %s\n   %s\n", i++, r.title, r.inlinkCount, r.url, r.snippet);
                            }
                            System.out.print("n-next, b-back, q-quit: ");
                            String a = SC.nextLine().trim().toLowerCase(); // Navigation command
                            if (a.equals("n")) page++;
                            else if (a.equals("b") && page > 1) page--;
                            else break; // Quit pagination
                        }
                        break;
                    case "3": // Show inlinks (pages linking to the given URL)
                        System.out.print("URL: ");
                        String u = SC.nextLine().trim();
                        List<String> ins = gw.getInlinks(u); // Remote call
                        System.out.println("Inlinks: " + ins.size());
                        for (String s : ins) System.out.println(" - " + s);
                        break;
                    case "4": // Show system statistics
                        SystemStats stats = gw.getSystemStats(); // Remote call
                        System.out.println("== System Stats ==");
                        System.out.println("Top queries:");
                        int qi = 1;
                        for (QueryStat qs : stats.topQueries) { // Print top queries by count
                            System.out.printf("%d. '%s' -> %d\n", qi++, qs.query, qs.count);
                            if (qi > 10) break;
                        }
                        System.out.println("Barrels:");
                        for (BarrelMetrics bm : stats.barrels) { // Show metrics for active barrels only
                            if (bm.active) {
                                System.out.printf("- %s \n  active: %s\n  indexSize: %d\n  avgResp: %d ds\n",
                                        bm.label, bm.active, bm.indexSize, bm.avgResponseDeci);
                            }
                        }
                        break;
                    case "0": // Exit program
                        return;
                    default: // Unknown menu option
                        System.out.println("Unknown option");
                }
            }
        } catch (Exception e) { // Log any errors (connection, remote calls, parsing)
            e.printStackTrace();
        }
    }
}
