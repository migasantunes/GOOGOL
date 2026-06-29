#!/bin/bash

# =============================================================================
# run-local.sh - Inicia todos os componentes do Googol numa única máquina (macOS/Linux)
# =============================================================================

set -e

# Configurações padrão
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
GATEWAY_PORT="${GATEWAY_PORT:-8181}"
GATEWAY_NAME="${GATEWAY_NAME:-gateway}"
BARREL1_PORT="${BARREL1_PORT:-8183}"
BARREL2_PORT="${BARREL2_PORT:-8184}"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Googol - Arranque Local (macOS)     ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Configuração:${NC}"
echo "  Gateway: ${GATEWAY_HOST}:${GATEWAY_PORT}"
echo "  Barrel1: ${GATEWAY_HOST}:${BARREL1_PORT}"
echo "  Barrel2: ${GATEWAY_HOST}:${BARREL2_PORT}"
echo ""

# Verificar se Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Erro: Maven não está instalado.${NC}"
    echo "Instale com: brew install maven"
    exit 1
fi

# Compilar projeto
echo -e "${YELLOW}[1/6] Compilando projeto...${NC}"
mvn -q compile -DskipTests

# Função para iniciar componente em nova janela do Terminal
start_component() {
    local name=$1
    local cmd=$2
    osascript -e "tell application \"Terminal\" to do script \"cd '$(pwd)' && echo '=== $name ===' && $cmd\""
}

# Iniciar Gateway
echo -e "${GREEN}[2/6] Iniciando Gateway...${NC}"
start_component "Gateway" "mvn -q exec:java -Dexec.mainClass=search.GatewayServer \"-Dexec.jvmArgs=-Dgateway.port=${GATEWAY_PORT} -Dgateway.name=${GATEWAY_NAME} -Djava.rmi.server.hostname=${GATEWAY_HOST}\""
sleep 3

# Iniciar Barrel 1
echo -e "${GREEN}[3/6] Iniciando Barrel 1...${NC}"
start_component "Barrel1" "mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args=\"${BARREL1_PORT} barrel1\" \"-Dexec.jvmArgs=-Dgateway.host=${GATEWAY_HOST} -Dgateway.port=${GATEWAY_PORT} -Dgateway.name=${GATEWAY_NAME} -Dbarrel.host=${GATEWAY_HOST}\""
sleep 2

# Iniciar Barrel 2
echo -e "${GREEN}[4/6] Iniciando Barrel 2...${NC}"
start_component "Barrel2" "mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args=\"${BARREL2_PORT} barrel2\" \"-Dexec.jvmArgs=-Dgateway.host=${GATEWAY_HOST} -Dgateway.port=${GATEWAY_PORT} -Dgateway.name=${GATEWAY_NAME} -Dbarrel.host=${GATEWAY_HOST}\""
sleep 2

# Iniciar Downloader
echo -e "${GREEN}[5/6] Iniciando Downloader...${NC}"
start_component "Downloader" "mvn -q exec:java -Dexec.mainClass=search.Downloader \"-Dexec.jvmArgs=-Dgateway.host=${GATEWAY_HOST} -Dgateway.port=${GATEWAY_PORT} -Dgateway.name=${GATEWAY_NAME}\""
sleep 2

# Iniciar Client
echo -e "${GREEN}[6/6] Iniciando Client...${NC}"
start_component "Client" "mvn -q exec:java -Dexec.mainClass=search.Client \"-Dexec.jvmArgs=-Dgateway.host=${GATEWAY_HOST} -Dgateway.port=${GATEWAY_PORT} -Dgateway.name=${GATEWAY_NAME}\""

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Todos os componentes iniciados!     ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Componentes em execução:${NC}"
echo "  - Gateway:    ${GATEWAY_HOST}:${GATEWAY_PORT}"
echo "  - Barrel1:    ${GATEWAY_HOST}:${BARREL1_PORT}"
echo "  - Barrel2:    ${GATEWAY_HOST}:${BARREL2_PORT}"
echo "  - Downloader: conectado ao Gateway"
echo "  - Client:     conectado ao Gateway"
echo ""
echo -e "${YELLOW}Para iniciar a WebApp, execute:${NC}"
echo "  ./scripts/run-web.sh"
