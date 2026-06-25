Write-Host ">>> A compilar e iniciar Web UI..." -ForegroundColor Cyan
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Write-Host "Build falhou" -ForegroundColor Red; exit 1 }

$env:GEMINI_API_KEY = $env:GEMINI_API_KEY

# Ensure realtime UI stats reach this web host
$uiHost = ${env:UI_PUSH_HOST}
if (-not $uiHost -or $uiHost.Trim().Length -eq 0) { $uiHost = "127.0.0.1" }

java -jar target/projeto-1.0.0.jar --gateway.host=127.0.0.1 --gateway.port=8181 --gateway.name=gateway --ui.push.host=$uiHost
