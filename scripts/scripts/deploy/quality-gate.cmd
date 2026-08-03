@echo off
REM Atalho: so quality gate (sem commit/push/deploy)
cd /d "%~dp0..\.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0quality-gate.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" (
  echo Quality gate FALHOU.
  pause
)
exit /b %EXITCODE%
