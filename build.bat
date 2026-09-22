@echo off
cd /d "%~dp0"
title Boss Timer - Fabric 1.20.4 Builder
call "%~dp0build-core.bat"
echo.
echo ========================================
echo Press any key to close this window.
echo ========================================
pause >nul
