# Googol

Distributed Java search engine with RMI, a Jsoup-based crawler, and a Spring Boot web interface. The application keeps a central gateway, several replicated Barrels, and a Web UI for submission, search, inlinks, statistics, and integration with external sources.

## Overview

The system is composed of these services:

- `GatewayServer`: receives URLs, manages the work queue, proxies search requests, and coordinates Barrel registration.
- `BarrelServer`: stores the inverted index and inlinks, with local file-based persistence.
- `Downloader`: consumes URLs from the Gateway, fetches pages, and indexes their content.
- `Client`: command-line interface for submitting URLs, searching, and listing inlinks.
- `WebApplication`: Spring Boot application with a web UI, REST, and WebSockets.

The final project behavior includes simple persistence, dynamic Barrel registration, automatic backfill for new Barrels, paginated results, a real-time statistics dashboard, and optional AI summaries with Groq.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer
- Docker and VS Code if you want to use the Dev Container

## Dev Container

The repository includes [.devcontainer/devcontainer.json](/workspaces/projeto-sd-meta-1-ma-al/.devcontainer/devcontainer.json), with an image based on Maven and Java 21, plus forwarded ports for the RMI and web services.

To open the project in that environment, use the **Reopen in Container** option in VS Code. This avoids installing local dependencies and leaves the services ready to run inside the container.

## Build

```powershell
mvn --% -q compile
```

## Local execution

To run everything on the same machine, open five terminals and start:

1. Gateway
```powershell
mvn --% -q exec:java -Dexec.mainClass=search.GatewayServer -Dexec.jvmArgs="-Dgateway.port=8181 -Dgateway.name=gateway -Djava.rmi.server.hostname=127.0.0.1"
```

2. Barrel 1
```powershell
mvn --% -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args="8183 barrel" -Dexec.jvmArgs="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway -Dbarrel.host=127.0.0.1"
```

3. Barrel 2
```powershell
mvn --% -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args="8184 barrel" -Dexec.jvmArgs="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway -Dbarrel.host=127.0.0.1"
```

4. Downloader
```powershell
mvn --% -q exec:java -Dexec.mainClass=search.Downloader -Dexec.jvmArgs="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway"
```

5. Client
```powershell
mvn --% -q exec:java -Dexec.mainClass=search.Client -Dexec.jvmArgs="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway"
```

## Web UI

Start the web application with:

```powershell
mvn --% -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway"
```

The interface is available at `http://localhost:8080` and includes:

- home page for submitting URLs
- paginated search with inlink-based ranking
- inlinks page for any indexed URL
- real-time statistics dashboard
- review and indexing of Hacker News links

## Configuration

Public application settings live in [src/main/resources/application.properties](src/main/resources/application.properties).
Local secrets must stay out of Git in [config/local-secrets.properties](config/local-secrets.properties), which is loaded automatically by the application.

To create the local file, copy [config/local-secrets.properties.example](config/local-secrets.properties.example) to `config/local-secrets.properties` and fill in the required values.

Example:

```properties
spring.config.import=optional:file:./config/local-secrets.properties
groq.api.key=YOUR_GROQ_API_KEY_HERE
groq.model=llama-3.3-70b-versatile
```

If `config/local-secrets.properties` does not exist or does not contain the key, the AI summary feature is unavailable, but the rest of the application keeps working.

## Scripts

The repository includes ready-to-use scripts for Windows and macOS:

- `scripts/run-local.ps1` / `scripts/run-local.sh`: full local startup
- `scripts/run-machine1.ps1` / `scripts/run-machine1.sh`: Gateway + Barrel 1 + Downloader
- `scripts/run-machine2.ps1` / `scripts/run-machine2.sh`: Barrel 2 + Downloader + Client
- `scripts/run-web.ps1` / `scripts/run-web.sh`: Web UI startup

## Persistence

- The Gateway stores the queue, already visited URLs, and statistics in `gateway-state.ser`.
- Each Barrel stores its index in its own `<name>-state.ser` file.
- On restart, state is loaded automatically.

## Project Structure

```text
src/main/java/search/
  Barrel.java
  BarrelServer.java
  Gateway.java
  GatewayServer.java
  Downloader.java
  Client.java
  PageIndexData.java
  PageResult.java
  BarrelMetrics.java
  QueryStat.java
  SystemStats.java
  BarrelEndpoint.java
  web/
    WebApplication.java
    controllers/
    services/
    model/
```

## Relevant Behavior

- Indexing is only confirmed when all registered Barrels accept the page.
- If there is a partial failure, the Downloader retries the URL.
- New Barrels receive automatic backfill when an active Barrel with data exists.
- The web UI uses WebSockets on `/ws` to publish statistics to `/topic/stats`.

## Notes

- The `target/` directory should not be versioned; it is only build output.
- To access the Web UI from outside `localhost`, adjust port `8080` or point the application to the correct Gateway.
