@echo off
cd /d "%~dp0"

echo Starting ParserNews live dashboard...
echo Dashboard: http://localhost:8081/
echo Press Ctrl+C to stop the server.
echo.

powershell -ExecutionPolicy Bypass -File ".\scripts\start-live.ps1" -Port 8081 -OpenBrowser

echo.
echo ParserNews live dashboard stopped.
pause
