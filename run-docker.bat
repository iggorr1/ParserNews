@echo off
setlocal

cd /d "%~dp0"

echo ==========================================
echo  ParserNews Docker/PostgreSQL launcher
echo ==========================================
echo.

where docker >nul 2>&1
if errorlevel 1 (
echo [ERROR] Docker is not installed or not available in PATH.
echo Open Docker Desktop and try again.
pause
exit /b 1
)

echo [INFO] Starting Docker Compose...
docker compose up -d --build

if errorlevel 1 (
echo.
echo [ERROR] Failed to start Docker Compose.
echo Possible reason: port 8080 is already busy.
echo.
echo Try:
echo   netstat -ano ^| findstr :8080
echo.
pause
exit /b 1
)

echo.
echo [INFO] Current containers:
docker compose ps

echo.
echo [INFO] Waiting a few seconds for the app to start...
timeout /t 8 /nobreak >nul

echo.
echo [INFO] Opening dashboard...
start http://localhost:8080

echo.
echo ==========================================
echo  Dashboard: http://localhost:8080
echo  Login:     admin
echo  Password:  change-me
echo ==========================================
echo.
echo To stop Docker mode, run:
echo   stop-docker.bat
echo.
pause
