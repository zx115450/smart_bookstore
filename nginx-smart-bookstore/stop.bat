@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Stopping...
nginx.exe -s quit
if errorlevel 1 taskkill /F /IM nginx.exe >nul 2>&1
echo Stopped.
pause
