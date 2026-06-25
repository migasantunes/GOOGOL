# Googol

Motor de pesquisa distribuído em Java com RMI, crawler baseado em Jsoup e interface web em Spring Boot. A aplicação mantém um gateway central, vários Barrels replicados e uma Web UI para submissão, pesquisa, inlinks, estatísticas e integração com fontes externas.

## Visão geral

O sistema é composto por estes serviços:

- `GatewayServer`: recebe URLs, gere a fila de trabalho, faz proxy de pesquisa e coordena o registo de Barrels.
- `BarrelServer`: armazena o índice invertido e os inlinks, com persistência local em ficheiro.
- `Downloader`: consome URLs do Gateway, faz fetch das páginas e indexa o conteúdo.
- `Client`: interface de linha de comandos para submeter URLs, pesquisar e listar inlinks.
- `WebApplication`: aplicação Spring Boot com UI web, REST e WebSockets.

O comportamento final do projeto inclui persistência simples, registo dinâmico de Barrels, backfill automático para novos Barrels, paginação de resultados, painel de estatísticas em tempo real e resumo IA opcional com Groq.

## Requisitos

- Java 17 ou superior
- Maven 3.9 ou superior

## Build

```powershell
mvn --% -q compile
```

## Execução local

Para executar tudo na mesma máquina, abre cinco terminais e inicia:

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

Arranque a aplicação web com:

```powershell
mvn --% -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway"
```

A interface fica disponível em `http://localhost:8080` e inclui:

- página inicial para submeter URLs
- pesquisa paginada com ranking por inlinks
- página de inlinks para qualquer URL indexada
- painel de estatísticas em tempo real
- revisão e indexação de links do Hacker News

## Configuração

As definições públicas da aplicação estão em [src/main/resources/application.properties](src/main/resources/application.properties).
As chaves locais devem ficar fora do Git em [config/local-secrets.properties](config/local-secrets.properties), carregado automaticamente pela aplicação.

Para criar o ficheiro local, copie [config/local-secrets.properties.example](config/local-secrets.properties.example) para `config/local-secrets.properties` e preencha os valores necessários.

Exemplo:

```properties
spring.config.import=optional:file:./config/local-secrets.properties
groq.api.key=YOUR_GROQ_API_KEY_HERE
groq.model=llama-3.3-70b-versatile
```

Se `config/local-secrets.properties` não existir ou não tiver a chave, a funcionalidade de resumo IA fica indisponível, mas o resto da aplicação continua funcional.

## Scripts

O repositório inclui scripts prontos para Windows e macOS:

- `scripts/run-local.ps1` / `scripts/run-local.sh`: arranque completo local
- `scripts/run-machine1.ps1` / `scripts/run-machine1.sh`: Gateway + Barrel 1 + Downloader
- `scripts/run-machine2.ps1` / `scripts/run-machine2.sh`: Barrel 2 + Downloader + Client
- `scripts/run-web.ps1` / `scripts/run-web.sh`: arranque da Web UI

## Persistência

- O Gateway grava a fila, URLs já visitadas e estatísticas em `gateway-state.ser`.
- Cada Barrel guarda o índice num ficheiro próprio do tipo `<nome>-state.ser`.
- Ao reiniciar, o estado é recarregado automaticamente.

## Estrutura do projeto

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

## Comportamento relevante

- A indexação só é confirmada quando todos os Barrels registados aceitam a página.
- Se houver falha parcial, o Downloader volta a tentar a URL.
- Novos Barrels recebem backfill automático quando existe um Barrel ativo com dados.
- A UI web usa WebSockets em `/ws` para publicar estatísticas em `/topic/stats`.

## Notas

- O diretório `target/` não deve ser versionado; é apenas saída de compilação.
- Para aceder à Web UI fora de `localhost`, adapte a porta `8080` ou aponte a aplicação para o Gateway correto.
