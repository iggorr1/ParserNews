@echo off
echo [1/3] Starting Docker containers...
docker compose up -d
if %errorlevel% neq 0 (
    echo ERROR: Docker failed to start. Is Docker Desktop running?
    pause
    exit /b 1
)

echo [2/3] Waiting for app to be ready...
timeout /t 15 /nobreak >nul

echo [3/3] Starting Cloudflare Tunnel...
tasklist /fi "imagename eq cloudflared.exe" 2>nul | find /i "cloudflared.exe" >nul
if %errorlevel% equ 0 (
    echo Cloudflare Tunnel already running, skipping.
) else (
    start "Cloudflare Tunnel" /min cloudflared tunnel run parsernews
)

echo.
echo Done! ParserNews is running.
echo   Local:  http://localhost:8080
echo   Public: https://parsernews.wwwho.lol
echo.
echo To stop everything run stop.bat
