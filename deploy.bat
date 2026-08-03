@echo off
setlocal EnableExtensions
title MIRA Auto-Deploy

cd /d "%~dp0"

echo.
echo ================================================================
echo  MIRA Prospect Portal - esteira automatica
echo  working tree -^> quality -^> commit -^> push -^> deploy
echo ================================================================
echo.

REM Flags opcionais (passe apos o bat):
REM   deploy.bat
REM   deploy.bat -Watch
REM   deploy.bat -ForceDeploy          ^<-- reinicia API + frontend de verdade
REM   deploy.bat -QualityOnly
REM   deploy.bat -Message "fix(ui): contraste"
REM   deploy.bat -SkipFrontendBuild
REM   deploy.bat -SkipDeploy
REM   deploy.bat -SkipPush
REM
REM Nota:
REM   - Commit so de scripts/deploy = VERIFY (app nao muda). Esperado.
REM   - Mudanca em frontend/ = reinicia ng serve.
REM   - Mudanca em backend/ ou -ForceDeploy = restart completo (1-3 min).

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\deploy\auto-deploy.ps1" %*
set "EXITCODE=%ERRORLEVEL%"

echo.
if "%EXITCODE%"=="0" (
  echo Esteira finalizada com sucesso.
) else (
  echo Esteira FALHOU. Codigo %EXITCODE%.
  echo Veja os logs acima e scripts\deploy\quality-gate.ps1 / auto-deploy.ps1
)

echo.
pause
exit /b %EXITCODE%
