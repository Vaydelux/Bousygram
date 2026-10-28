@echo off
setlocal EnableExtensions
if "%~1"=="" (
  echo Usage: %~nx0 ^<40-character TDLib commit^>
  exit /b 2
)
set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%generate_tdlib_sources.ps1" -TdlibRef "%~1"
exit /b %ERRORLEVEL%
