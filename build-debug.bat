@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-debug.ps1"
exit /b %errorlevel%
