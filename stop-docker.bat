@echo off
setlocal

cd /d "%~dp0"

echo ==========================================
echo  Stopping ParserNews Docker containers
echo ==========================================
echo.

docker compose down

echo.
echo [INFO] Docker containers stopped.
echo [INFO] PostgreSQL data volume was NOT deleted.
echo.
echo If you ever want to fully reset database, use manually:
echo   docker compose down -v
echo.
pause
