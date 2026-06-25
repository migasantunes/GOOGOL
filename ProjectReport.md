# Relatório do Projeto — Googol

Este relatório descreve os testes, a arquitetura, o funcionamento do RPC/RMI, a replicação/consistência, a integração Web (Spring Boot + WebSockets + REST) e a distribuição de tarefas. A implementação cumpre os requisitos funcionais e integrações pedidos na checklist; também documentamos pontos extra e obrigatórios.

Nesta versão introduzimos: (1) persistência simples em ficheiros, (2) registo dinâmico de Barrels no Gateway, (3) indexação "all‑or‑nothing" via Gateway para consistência entre réplicas, (4) recuperação sem donor e backfill automático, (5) UI Web com Spring Boot, (6) WebSockets para estatísticas em tempo real, (7) REST para indexação a partir do HackerNews e (8) integração de IA via API REST (Groq).

---

## 1. Testes de software

Tabela com testes de software, descrição e coluna de status.

| ID | Teste | Descrição | Como executar (resumo) | Status |
|---:|---|---|---|---|
| T1 | Indexação manual | Enviar URL via `Client.submitUrl` e verificar que o `Barrel` contém a página indexada | Iniciar Gateway, Barrel(s), Downloader e Client; no Client `submitUrl`; verificar `BarrelServer.getIndexedUrlCount()` e `search` | PASS |
| T2 | Indexação recursiva | Página com 3 links → garantir que os 3 links são enfileirados e indexados | Configurar página de teste local com 3 links; arrancar Downloader; verificar que as 3 URLs aparecem indexadas | PASS |
| T3 | Pesquisa por termos + ordenação | Criar 3 páginas que contenham o termo X com inlinks 0,2,5 → verificar ordenação por inlinks (desc.) e paginação 10/10 | Indexar manualmente páginas; usar `Client.search("X")` e verificar a ordem | PASS |
| T4 | Lista de inlinks | Para uma URL Y, verificar que `Gateway.getInlinks(Y)` devolve as páginas corretas | Indexar páginas que apontem para Y; invocar `getInlinks` | PASS |
| T5 | Failover de Barrels | Desligar um Barrel e confirmar que `Gateway.search` continua a devolver resultados via outra réplica | Iniciar 2 barrels; fechar um; executar `Client.search` | PASS |
| T6 | Reinício de Barrel com estado | Reiniciar um Barrel e verificar que carrega `<nome>-state.ser` e mantém o número de URLs indexados | Parar Barrel; arrancar novamente; verificar `getIndexedUrlCount()` | PASS |
| T7 | Unicidade / idempotência | Enviar a mesma URL várias vezes e validar que não são criadas entradas duplicadas | Enviar submitUrl N vezes; `getIndexedUrlCount` deve contar uma vez | PASS |
| T8 | Concorrência de Downloaders | Iniciar múltiplos `Downloader` e verificar que cada URL é processada apenas uma vez | Iniciar 5 downloaders e várias seeds; monitorizar `takeNextUrl` e logs | PASS |
| T9 | Consistência all‑or‑nothing | Simular falha num Barrel e validar que `Gateway.indexPage` falha e o URL é re‑enfileirado; quando o Barrel regressa, a página é indexada em todas as réplicas | Parar um Barrel, submeter página, voltar a iniciar Barrel; verificar meta nos dois | PASS |
| T10 | Backfill de novo Barrel | Arrancar um Barrel novo e verificar que recebe índice do existente | 1 Barrel com dados; arrancar 2º → verificar `indexSize` no Client (System stats) | PASS |
| T11 | Multi‑máquina (RMI hostname) | Barrels e Gateway em máquinas diferentes sem erro de `Connection refused 127.0.0.1` | Definir `-Dbarrel.host` no Barrel e `-Djava.rmi.server.hostname` no Gateway | PASS |
| T12 | Contagem de inlinks | Verificar que o `inlinkCount` reflete a criação de links a alvos já presentes | Indexar A, depois B→A; `inlinkCount(A)` incrementa | PASS |

— Integração Web —

| ID | Teste | Descrição | Como executar (resumo) | Status |
|---:|---|---|---|---|
| W1 | WebSockets stats | Subscrição a `/topic/stats` e receção de `SystemStats` com top 10, barrels e latência média | Arrancar WebApp; abrir `/stats`; verificar atualização em tempo real ao pesquisar | PASS |
| W2 | REST HN index | Buscar top stories do HN com termos e enfileirar links selecionados | Abrir `/hn-review?q=<termos>`; clicar para indexar; verificar `Gateway.queue` | PASS |
| W3 | IA resumo | Gerar texto contextual com snippets dos resultados | Configurar `groq.api.key`; pesquisar; verificar `analysis` na página de resultados | PASS |

---

## 2. Arquitetura de Software

A solução segue uma arquitetura híbrida: um backend distribuído baseado em RMI e um frontend Web baseado em Spring Boot.

### 2.1. Componentes Core (Backend RMI)
* **GatewayServer (Porta 8181):** Ponto central de coordenação.
    * Gere a fila de URLs (persistida em `gateway-state.ser`).
    * Atua como proxy de pesquisa e agregador de métricas.
    * Gere o registo dinâmico de Barrels ativos.
* **BarrelServer (Portas 8183/8184):** Nós de armazenamento.
    * Mantêm o índice invertido (`ConcurrentMap<String, Set<String>>`) e grafo de ligações.
    * Suportam persistência (`<nome>-state.ser`) e recuperação de falhas.
* **Downloader:** Crawlers que usam Jsoup para processar páginas, extrair tokens/links e enviá-los ao Gateway.

### 2.2. Interface Web (Spring Boot & MVC)
A aplicação Web (`search.web`) atua como cliente do cluster RMI, expondo funcionalidades via HTTP/WebSocket.

* **Controllers:**
    * `SearchController`: Pesquisa (integração RMI), Indexação HN (REST) e Resumos IA (Groq).
    * `StatsController` & `StatsPushController`: Gestão da página de estatísticas e trigger de updates.
    * `UrlController`: Submissão de URLs (`POST /submit`).
    * `InlinksController`: Visualização de grafos de links.
* **Frontend:**
    * Templates Thymeleaf (`search.html`, `stats.html`) renderizados no servidor.
    * JavaScript (`app.js`, `hn.js`) para atualizações via WebSocket e chamadas AJAX.

---

## 3. Detalhes do Funcionamento RPC / RMI

A comunicação entre processos utiliza Java RMI com registos `LocateRegistry`.

### Interfaces Remotas
**`search.Gateway`:**
* `indexPage(PageIndexData data)`: Envia dados a todas as réplicas; retorna `true` apenas se todas confirmarem (consistência forte na escrita).
* `submitUrl(String url)` / `takeNextUrl()`: Gestão da fila de crawling.
* `registerBarrel(...)`: Permite que novos Barrels se juntem ao cluster dinamicamente.
* `search(...)` / `getSystemStats()`: Métodos de leitura para clientes.

**`search.Barrel`:**
* `addToIndex(PageIndexData data)`: Indexa dados localmente.
* `exportAll(offset, limit)`: Exporta fatias do índice para *recovery* (backfill) de novos nós.

### Configuração de Rede
* **Anúncio de Host:** Os servidores usam `-Djava.rmi.server.hostname=<ip>` para garantir que os stubs contêm um IP acessível (evitando `127.0.0.1` em redes reais).
* **Serialização:** Todos os DTOs (`PageIndexData`, `PageResult`, `SystemStats`) implementam `Serializable`.

---

## 4. Integração Spring Boot com Servidor RPC/RMI

A integração é gerida pelo serviço `GatewayClientService`.

* **Padrão Adapter com Cache:**
    Para evitar a latência do lookup RMI em cada pedido HTTP, o serviço mantém uma referência `volatile` para o stub do Gateway (Cached Stub). Utiliza *double-checked locking* para garantir thread-safety e eficiência.
* **Configuração:**
    Lê `gateway.host` e `gateway.port` do `application.properties`, permitindo reconfiguração sem recompilar o código.

---

## 5. WebSockets e Comunicação em Tempo Real

Utilizou-se **Spring WebSocket** com protocolo **STOMP** para o dashboard de estatísticas.

* **Broker:** Configurado em `/topic` com endpoint `/ws` (suporte SockJS).
* **Publicação (`StatsPublisher`):**
    * Consulta o Gateway periodicamente via `@Scheduled`.
    * Calcula um **hash do estado** (Top Queries + Estado Barrels) e envia mensagem apenas se houver alterações reais nos dados (excluindo timestamp).
* **Notificação de Eventos:** O componente `UiStatsPusher` notifica a WebApp sempre que o Gateway muda de estado (nova pesquisa ou indexação), disparando atualizações imediatas.

---

## 6. Integração com Serviços REST

Implementada com **Spring WebFlux (`WebClient`)** para I/O não-bloqueante.

### 6.1. Hacker News (Indexação Externa)
* **Fluxo:** O `HackerNewsService` obtém IDs das *top stories*, descarrega detalhes em paralelo (usando `Flux`) e filtra localmente por termos de pesquisa.
* **Indexação:** O utilizador seleciona histórias na UI, e o sistema envia os URLs para o Gateway via RMI (`gateway.submitUrl`).

### 6.2. Groq / OpenAI (IA Generativa)
* **RAG Simplificado:** O `GroqService` recolhe os *snippets* dos top 5 resultados da pesquisa RMI.
* **Prompt:** Envia um prompt estruturado (*"Summarize the following text excerpts..."*) para a API da Groq (modelo `llama-3.3-70b`), enriquecendo a página de resultados com um resumo contextual.

---

## 7. Replicação e Consistência

* **Indexação "All-or-Nothing":** O Gateway atua como coordenador. Só confirma a indexação ao Downloader se **todas** as réplicas ativas processarem o pedido com sucesso. Em caso de falha de qualquer réplica, o URL é re-enfileirado para tentativa posterior.
* **Backfill Automático:**
    * Quando um novo Barrel (vazio) arranca, contacta o Gateway.
    * O Gateway identifica uma réplica "dadora" existente.
    * Os dados são transferidos (via `exportAll` → `addToIndex`) para o novo nó antes de este entrar no pool de pesquisa ativo.
* **Persistência:** Ficheiros de estado (`.ser`) garantem recuperação após reinício.

---

## 7. Troubleshooting / Operação

- Connection refused para 127.0.0.1 ao registar Barrel:
  - O stub do Gateway foi exportado com 127.0.0.1. Reiniciar Gateway com `-Djava.rmi.server.hostname=<ip>` ou `-Dgateway.host=<ip>`.
  - Confirmar no log: `Gateway ready on <ip>:<port>`.

- Port already in use (RMI Registry):
  - macOS/Linux:
    ```bash
    sudo lsof -iTCP:<porto> -sTCP:LISTEN
    kill -9 <PID>
    ```
  - Windows PowerShell:
    ```powershell
    netstat -ano | findstr :<porto>
    taskkill /PID <PID> /F
    ```
  - Pode usar outro porto (ex.: 8185) no `BarrelServer`.

- Várias NICs/VPN:
  - Forçar IP com `-Dbarrel.host=<ip>` (Barrel) e `-Djava.rmi.server.hostname=<ip>` ou `-Dgateway.host=<ip>` (Gateway).

- Backfill não corre:
  - É necessário existir pelo menos uma réplica com dados; verificar log do Gateway por `Backfill concluído`.
---