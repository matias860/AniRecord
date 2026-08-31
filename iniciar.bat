@echo off
setlocal
cd /d "%~dp0"

if not exist "frontend\package.json" (
  echo No se encontro frontend\package.json
  pause
  exit /b 1
)

start "AniRecord - Frontend" cmd /k "cd /d ""%~dp0frontend"" && (if not exist node_modules call npm install) && npm run dev"

echo AniRecord se esta iniciando.
echo Frontend local: http://localhost:5173
echo Backend remoto: https://anirecord.com.ar/api
echo La primera carga puede tardar unos segundos mientras el servicio responde.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$limit = (Get-Date).AddSeconds(120); while ((Get-Date) -lt $limit) { try { $frontend = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:5173' -TimeoutSec 3; if ($frontend.StatusCode -eq 200) { exit 0 } } catch {}; Start-Sleep -Seconds 1 }; exit 1"
if errorlevel 1 (
  echo No se pudo iniciar el frontend en 120 segundos.
  echo Revisa la ventana de Frontend para ver el error.
  pause
  exit /b 1
)

echo Sistema listo. Abriendo AniRecord...
if exist "%LOCALAPPDATA%\Programs\Opera GX\opera.exe" (
  start "" "%LOCALAPPDATA%\Programs\Opera GX\opera.exe" "http://localhost:5173"
) else if exist "%LOCALAPPDATA%\Programs\Opera\opera.exe" (
  start "" "%LOCALAPPDATA%\Programs\Opera\opera.exe" "http://localhost:5173"
) else (
  start "" "http://localhost:5173"
)

endlocal
