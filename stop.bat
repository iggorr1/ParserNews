@echo off
echo [1/2] Stopping Cloudflare Tunnel...
taskkill /f /im cloudflared.exe >nul 2>&1
echo Done.

echo [2/2] Stopping Docker containers...
docker compose stop
echo Done.

echo.
echo ParserNews stopped.
