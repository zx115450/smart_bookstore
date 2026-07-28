@echo off
chcp 65001 >nul
cd /d "%~dp0"

if exist logs\nginx.pid (
  nginx.exe -s quit >nul 2>&1
  timeout /t 1 /nobreak >nul
)

echo Starting smart-bookstore on http://localhost:8088 ...
start "" nginx.exe
timeout /t 1 /nobreak >nul
start "" http://localhost:8088
echo OK. Use stop.bat to stop.
pause
