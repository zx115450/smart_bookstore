@echo off
chcp 65001 >nul
cd /d "%~dp0"
nginx.exe -t || (pause & exit /b 1)
nginx.exe -s reload
echo Reloaded.
pause
