#!/bin/bash

# =============================================================================
# run-web.sh - Inicia a WebApp Spring Boot (macOS/Linux)
# =============================================================================

set -e

# Configurações padrão
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
GATEWAY_PORT="${GATEWAY_PORT:-8181}"
WEB_PORT="${WEB_PORT:-8080}"
GROQ_API_KEY="${GROQ_API_KEY:-}"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Googol WebApp - Spring Boot         ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Configuração:${NC}"
echo "  Gateway: ${GATEWAY_HOST}:${GATEWAY_PORT}"
echo "  WebApp:  http://localhost:${WEB_PORT}"
if [ -n "$GROQ_API_KEY" ]; then
    echo "  IA:      Groq API configurada"
else
    echo "  IA:      Não configurada (definir GROQ_API_KEY)"
fi
echo ""

# Verificar se Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Erro: Maven não está instalado.${NC}"
    echo "Instale com: brew install maven"
    exit 1
fi

echo -e "${GREEN}Iniciando WebApp...${NC}"
echo ""

# Iniciar Spring Boot
mvn spring-boot:run \
    -Dspring-boot.run.arguments="--server.port=${WEB_PORT} --gateway.host=${GATEWAY_HOST} --gateway.port=${GATEWAY_PORT}" \
    -Dspring-boot.run.jvmArguments="-DGROQ_API_KEY=${GROQ_API_KEY}"
