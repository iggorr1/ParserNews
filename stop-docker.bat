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
echo WARNING:
echo   docker compose down -v deletes database data and should be used only for full reset.
echo.
echo If you ever want to fully reset the database, use manually:
echo   docker compose down -v
echo.
pause
