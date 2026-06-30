# Project Report — Googol

This report describes the test plan, the architecture, RPC/RMI operation, replication/consistency, the Web integration (Spring Boot + WebSockets + REST) and task distribution. The implementation meets the functional requirements and integrations listed in the checklist; we also document additional and mandatory points.

In this release we introduced: (1) simple file-based persistence, (2) dynamic Barrel registration at the Gateway, (3) "all-or-nothing" indexing via the Gateway for replica consistency, (4) recovery without a donor and automatic backfill, (5) a Web UI using Spring Boot, (6) WebSockets for real-time statistics, (7) REST endpoints for indexing from HackerNews, and (8) AI integration via a REST API (Groq).

---

## 1. Software Tests

Table of software tests, descriptions, and execution summary.

| ID | Test | Description | How to run (summary) | Status |
|---:|---|---|---|---|
| T1 | Manual indexing | Submit a URL via `Client.submitUrl` and verify the `Barrel` contains the indexed page | Start Gateway, Barrel(s), Downloader and Client; in Client call `submitUrl`; check `BarrelServer.getIndexedUrlCount()` and `search` | PASS |
| T2 | Recursive indexing | A page with 3 links → ensure all 3 links are enqueued and indexed | Host a local test page with 3 links; start the Downloader; verify all 3 URLs are indexed | PASS |
| T3 | Term search + ranking | Create 3 pages containing term X with inlinks 0,2,5 → verify ordering by inlinks (desc.) and pagination 10/10 | Index pages manually; call `Client.search("X")` and verify ordering | PASS |
| T4 | Inlinks list | For a URL Y, verify `Gateway.getInlinks(Y)` returns the correct pages | Index pages that link to Y; call `getInlinks` | PASS |
| T5 | Barrel failover | Shut down a Barrel and confirm `Gateway.search` still returns results via another replica | Start 2 barrels; stop one; run `Client.search` | PASS |
| T6 | Barrel restart with state | Restart a Barrel and verify it loads `<name>-state.ser` and preserves the indexed URL count | Stop Barrel; start again; check `getIndexedUrlCount()` | PASS |
| T7 | Uniqueness / idempotency | Submit the same URL multiple times and validate no duplicate entries are created | Submit the URL N times; `getIndexedUrlCount` should count once | PASS |
| T8 | Downloader concurrency | Start multiple `Downloader` instances and verify each URL is processed only once | Start 5 downloaders with multiple seeds; monitor `takeNextUrl` and logs | PASS |
| T9 | All-or-nothing consistency | Simulate a Barrel failure and validate that `Gateway.indexPage` fails and the URL is re-enqueued; when the Barrel returns, the page is indexed on all replicas | Stop a Barrel, submit a page, restart the Barrel; verify metadata on both | PASS |
| T10 | New Barrel backfill | Start a new Barrel and verify it receives the index from an existing replica | 1 Barrel with data; start 2nd → check `indexSize` via Client (System stats) | PASS |
| T11 | Multi-machine (RMI hostname) | Barrels and Gateway on different machines without `Connection refused 127.0.0.1` errors | Set `-Dbarrel.host` on the Barrel and `-Djava.rmi.server.hostname` on the Gateway | PASS |
| T12 | Inlink counting | Verify that `inlinkCount` reflects the creation of links to targets that already exist | Index A, then B→A; `inlinkCount(A)` increases | PASS |

— Web Integration —

| ID | Test | Description | How to run (summary) | Status |
|---:|---|---|---|---|
| W1 | WebSockets stats | Subscribe to `/topic/stats` and receive `SystemStats` with top 10, barrels and average latency | Start the WebApp; open `/stats`; verify real-time updates while searching | PASS |
| W2 | REST HN index | Fetch Hacker News top stories filtered by terms and enqueue selected links | Open `/hn-review?q=<terms>`; click to index; verify `Gateway.queue` | PASS |
| W3 | AI summarization | Generate contextual text from snippets of results | Configure `groq.api.key`; run a search; check `analysis` on the results page | PASS |

---

## 2. Software Architecture

The solution uses a hybrid architecture: a distributed backend based on RMI and a Web frontend based on Spring Boot.

### 2.1. Core Components (Backend RMI)
* **GatewayServer (Port 8181):** Central coordination point.
    * Manages the URL queue (persisted to `gateway-state.ser`).
    * Acts as a search proxy and metrics aggregator.
    * Manages dynamic registration of active Barrels.
* **BarrelServer (Ports 8183/8184):** Storage nodes.
    * Maintain the inverted index (`ConcurrentMap<String, Set<String>>`) and the link graph.
    * Support persistence (`<name>-state.ser`) and failure recovery.
* **Downloader:** Crawlers using Jsoup to fetch pages, extract tokens/links and submit them to the Gateway.

### 2.2. Web Interface (Spring Boot & MVC)
The Web application (`search.web`) acts as a client of the RMI cluster, exposing functionality over HTTP/WebSocket.

* **Controllers:**
    * `SearchController`: Search (RMI integration), HN indexing (REST) and AI summaries (Groq).
    * `StatsController` & `StatsPushController`: Manage the statistics page and trigger updates.
    * `UrlController`: URL submission (`POST /submit`).
    * `InlinksController`: Link graph visualization.
* **Frontend:**
    * Thymeleaf templates (`search.html`, `stats.html`) rendered server-side.
    * JavaScript (`app.js`, `hn.js`) for WebSocket updates and AJAX calls.

---

## 3. RPC / RMI Operation Details

Processes communicate using Java RMI with `LocateRegistry` lookups.

### Remote Interfaces
**`search.Gateway`:**
* `indexPage(PageIndexData data)`: Sends data to all replicas; returns `true` only if all confirm (strong write consistency).
* `submitUrl(String url)` / `takeNextUrl()`: Crawl queue management.
* `registerBarrel(...)`: Allows new Barrels to join the cluster dynamically.
* `search(...)` / `getSystemStats()`: Read methods for clients.

**`search.Barrel`:**
* `addToIndex(PageIndexData data)`: Index data locally.
* `exportAll(offset, limit)`: Export slices of the index for recovery (backfill) by new nodes.

### Network Configuration
* **Host announcement:** Servers use `-Djava.rmi.server.hostname=<ip>` so stubs contain an accessible IP (avoiding `127.0.0.1` in real networks).
* **Serialization:** All DTOs (`PageIndexData`, `PageResult`, `SystemStats`) implement `Serializable`.

---

## 4. Spring Boot Integration with RPC/RMI Server

Integration is handled by the `GatewayClientService`.

* **Adapter pattern with cache:**
    To avoid RMI lookup latency on every HTTP request, the service keeps a `volatile` reference to the Gateway stub (cached stub). It uses double-checked locking to ensure thread-safety and efficiency.
* **Configuration:**
    Reads `gateway.host` and `gateway.port` from `application.properties`, allowing reconfiguration without recompiling.

---

## 5. WebSockets and Real-time Communication

Spring WebSocket with the STOMP protocol is used for the statistics dashboard.

* **Broker:** Configured under `/topic` with endpoint `/ws` (SockJS enabled).
* **Publication (`StatsPublisher`):**
    * Polls the Gateway periodically via `@Scheduled`.
    * Computes a state hash (Top Queries + Barrel state) and publishes only when there are real data changes (excluding timestamp differences).
* **Event notification:** The `UiStatsPusher` component notifies the WebApp whenever the Gateway state changes (new indexing or searches), triggering immediate updates.

---

## 6. Integration with REST Services

Implemented with Spring WebFlux (`WebClient`) for non-blocking I/O.

### 6.1. Hacker News (External Indexing)
* **Flow:** `HackerNewsService` fetches top story IDs, downloads details in parallel (using `Flux`) and filters locally by search terms.
* **Indexing:** The user selects stories in the UI, and the system sends the URLs to the Gateway via RMI (`gateway.submitUrl`).

### 6.2. Groq / OpenAI (Generative AI)
* **Simplified RAG:** `GroqService` gathers snippets from the top 5 RMI search results.
* **Prompt:** Sends a structured prompt ("Summarize the following text excerpts...") to the Groq API (model `llama-3.3-70b`), enriching the results page with a contextual summary.

---

## 7. Replication and Consistency

* **All-or-nothing indexing:** The Gateway acts as coordinator. It only confirms indexing to the Downloader if **all** active replicas successfully process the request. If any replica fails, the URL is re-enqueued for a later attempt.
* **Automatic backfill:**
    * When a new (empty) Barrel starts, it contacts the Gateway.
    * The Gateway identifies an existing "donor" replica.
    * Data are transferred (via `exportAll` → `addToIndex`) to the new node before it joins the active search pool.
* **Persistence:** State files (`.ser`) ensure recovery after restart.

---

## 8. Troubleshooting / Operations

- Connection refused to 127.0.0.1 when registering a Barrel:
  - The Gateway stub was exported with 127.0.0.1. Restart the Gateway with `-Djava.rmi.server.hostname=<ip>` or `-Dgateway.host=<ip>`.
  - Confirm in logs: `Gateway ready on <ip>:<port>`.

- Port already in use (RMI Registry):
  - macOS/Linux:
    ```bash
    sudo lsof -iTCP:<port> -sTCP:LISTEN
    kill -9 <PID>
    ```
  - Windows PowerShell:
    ```powershell
    netstat -ano | findstr :<port>
    taskkill /PID <PID> /F
    ```
  - You can use another port (e.g. 8185) for the `BarrelServer`.

- Multiple NICs / VPN:
  - Force the IP with `-Dbarrel.host=<ip>` (Barrel) and `-Djava.rmi.server.hostname=<ip>` or `-Dgateway.host=<ip>` (Gateway).

- Backfill does not run:
  - There must be at least one replica with data; check the Gateway logs for `Backfill completed`.
---