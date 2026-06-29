# Ensure realtime UI stats reach the web UI by setting ui.push.host
$uiHost = ${env:UI_PUSH_HOST}
if (-not $uiHost -or $uiHost.Trim().Length -eq 0) { $uiHost = "127.0.0.1" }

Write-Host ">>> A compilar o projeto..." -ForegroundColor Cyan
mvn -q compile

if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro na compilação!" -ForegroundColor Red
    Read-Host "Enter para sair"
    exit
}

Write-Host ">>> A iniciar componentes" -ForegroundColor Green

# 1. Gateway
Start-Process cmd -ArgumentList "/k title Gateway && mvn -q exec:java -Dexec.mainClass=search.GatewayServer -Dexec.jvmArgs=`"-Dui.push.host=$uiHost -Dgateway.port=8181 -Dgateway.name=gateway -Djava.rmi.server.hostname=127.0.0.1`""

Write-Host "A aguardar 3 segundos..."
Start-Sleep -Seconds 3

# 2. Barrel 1
Start-Process cmd -ArgumentList "/k title Barrel_1 && mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args=`"8183 barrel`" -Dexec.jvmArgs=`"-Dui.push.host=$uiHost -Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway -Dbarrel.host=127.0.0.1`""

# 3. Barrel 2
Start-Process cmd -ArgumentList "/k title Barrel_2 && mvn -q exec:java -Dexec.mainClass=search.BarrelServer -Dexec.args=`"8184 barrel`" -Dexec.jvmArgs=`"-Dui.push.host=$uiHost -Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway -Dbarrel.host=127.0.0.1`""

Write-Host "A aguardar 2 segundos..."
Start-Sleep -Seconds 2

# 4. Downloader
Start-Process cmd -ArgumentList "/k title Downloader && mvn -q exec:java -Dexec.mainClass=search.Downloader -Dexec.jvmArgs=`"-Dui.push.host=$uiHost -Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway`""
Start-Process cmd -ArgumentList "/k title Downloader && mvn -q exec:java -Dexec.mainClass=search.Downloader -Dexec.jvmArgs=`"-Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway`""

# 5. Client
Start-Process cmd -ArgumentList "/k title Client && mvn -q exec:java -Dexec.mainClass=search.Client -Dexec.jvmArgs=`"-Dui.push.host=$uiHost -Dgateway.host=127.0.0.1 -Dgateway.port=8181 -Dgateway.name=gateway`""

Write-Host ">>> Tudo iniciado!" -ForegroundColor Cyan