#!/bin/bash

# --- CONFIGURAÇÃO DE IPs ---
GATEWAY_IP="172.20.10.3"    # IP do Windows
MY_IP="172.20.10.14"        # IP do Mac

echo ">>> A configurar MAC para ligar ao Gateway em $GATEWAY_IP"

# Compilar no processo principal (aqui as variáveis não importam tanto)
mvn -q compile

CURRENT_DIR=$(pwd)

# Definimos a string de opções que TEM de ir para dentro das novas janelas
# Usamos MAVEN_OPTS porque é a forma mais segura de passar args ao Java sem problemas de aspas
ENV_SETUP="export MAVEN_OPTS='-Dgateway.host=$GATEWAY_IP -Dgateway.port=8181 -Dgateway.name=gateway -Dbarrel.host=$MY_IP -Djava.rmi.server.hostname=$MY_IP -Dui.push.host=$GATEWAY_IP'"

# Função corrigida: Injeta o ENV_SETUP antes de correr o comando
run_in_terminal() {
    # O comando final será: "cd /pasta; export MAVEN_OPTS='...'; mvn ..."
    FULL_CMD="cd '$CURRENT_DIR'; $ENV_SETUP; $1"
    
    # AppleScript para abrir janela e correr o comando completo
    osascript -e "tell application \"Terminal\" to do script \"$FULL_CMD\""
}

echo ">>> A iniciar Barrel 2..."
# Apenas indicamos a classe e args específicos, os IPs vão via MAVEN_OPTS
CMD_BARREL="mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args='8184 barrel'"
run_in_terminal "$CMD_BARREL"

echo ">>> A aguardar 2 segundos..."
sleep 2

echo ">>> A iniciar Downloader..."
CMD_DOWN="mvn -q exec:java -Dexec.mainClass=search.Downloader"
run_in_terminal "$CMD_DOWN"

echo ">>> A iniciar Client..."
CMD_CLIENT="mvn -q exec:java -Dexec.mainClass=search.Client"
run_in_terminal "$CMD_CLIENT"

echo ">>> Tudo iniciado!"
echo "NOTA: Se as janelas novas fecharem ou derem erro, verifica a Firewall do Windows."