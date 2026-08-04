@echo off
setlocal
cd /d "%~dp0"

if not exist "backend\mvnw.cmd" (
  echo No se encontro backend\mvnw.cmd
  pause
  exit /b 1
)

if not exist "frontend\package.json" (
  echo No se encontro frontend\package.json
  pause
  exit /b 1
)

powershell -NoProfile -Command "$service = Get-Service | Where-Object { $_.Name -like 'MySQL*' } | Select-Object -First 1; if (-not $service) { exit 2 }; if ($service.Status -ne 'Running') { try { Start-Service $service.Name -ErrorAction Stop } catch { exit 1 } }"
if errorlevel 2 echo AVISO: No se encontro un servicio de MySQL instalado.
if errorlevel 1 if not errorlevel 2 echo AVISO: No se pudo iniciar MySQL. Proba ejecutar este archivo como administrador.

start "Anime List - Backend" cmd /k "cd /d ""%~dp0backend"" && call mvnw.cmd spring-boot:run"
start "Anime List - Frontend" cmd /k "cd /d ""%~dp0frontend"" && (if not exist node_modules call npm install) && npm run dev"

echo Anime List se esta iniciando.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:5173

powershell -NoProfile -Command "$limit = (Get-Date).AddSeconds(90); while ((Get-Date) -lt $limit) { try { $client = [Net.Sockets.TcpClient]::new(); $client.Connect('localhost', 5173); $client.Dispose(); exit 0 } catch { Start-Sleep -Seconds 1 } }; exit 1"
if errorlevel 1 (
  echo No se pudo abrir la pagina porque el frontend no inicio en 90 segundos.
  pause
  exit /b 1
)

if exist "%LOCALAPPDATA%\Programs\Opera GX\opera.exe" (
  start "" "%LOCALAPPDATA%\Programs\Opera GX\opera.exe" "http://localhost:5173"
) else if exist "%LOCALAPPDATA%\Programs\Opera\opera.exe" (
  start "" "%LOCALAPPDATA%\Programs\Opera\opera.exe" "http://localhost:5173"
) else (
  start "" "http://localhost:5173"
)

endlocal
