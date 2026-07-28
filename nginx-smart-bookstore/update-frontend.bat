@echo off
chcp 65001 >nul
cd /d "%~dp0.."
echo Building...
call npm run build
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
echo Syncing to nginx-smart-bookstore\html ...
robocopy dist nginx-smart-bookstore\html /MIR /NFL /NDL /NJH /NJS /nc /ns /np
cd /d "%~dp0"
nginx.exe -s reload >nul 2>&1
echo Done. http://localhost:8088
pause
