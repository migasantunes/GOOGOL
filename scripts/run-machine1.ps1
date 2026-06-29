# Definição fixa do IP da Máquina 1
$MyIP = "172.20.10.3"

Write-Host ">>> A configurar ambiente para a Maquina 1 (IP: $MyIP)" -ForegroundColor Cyan

# TRUQUE NOVO: Injetar o IP diretamente nas opções globais do Maven
# Isto garante que o Java NÃO pode ignorar o IP, mesmo que queira.
$env:MAVEN_OPTS = "-Djava.rmi.server.hostname=$MyIP -Dgateway.host=$MyIP -Dbarrel.host=$MyIP -Dgateway.port=8181 -Dgateway.name=gateway -Dui.push.host=$MyIP"

Write-Host ">>> A compilar..."
mvn -q compile

if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação!" -ForegroundColor Red
    exit
}

# 1. Gateway
Write-Host ">>> A iniciar Gateway..."
Start-Process cmd -ArgumentList "/k title Gateway && mvn -q exec:java -Dexec.mainClass=search.GatewayServer"

Write-Host "A aguardar 5 segundos..."
Start-Sleep -Seconds 5

# 2. Barrel 1
Write-Host ">>> A iniciar Barrel 1..."
Start-Process cmd -ArgumentList "/k title Barrel_1 && mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args=`"8183 barrel`""

Write-Host "A aguardar 1 segundo..."
Start-Sleep -Seconds 1

# 3. Downloader
Write-Host ">>> A iniciar Downloader..."
Start-Process cmd -ArgumentList "/k title Downloader && mvn -q exec:java -Dexec.mainClass=search.Downloader"

Write-Host ">>> Máquina 1 iniciada em $MyIP." -ForegroundColor Green