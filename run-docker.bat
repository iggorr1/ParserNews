@echo off
cd /d "%~dp0"

echo Starting ParserNews Docker dashboard...
echo Dashboard: http://localhost:8080/
echo Press Ctrl+C to stop the server.
echo.

docker compose up --build

echo.
echo ParserNews Docker dashboard stopped.
pause
