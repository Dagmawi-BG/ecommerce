@echo off
REM Double-click this file to launch the whole store (Postgres + Keycloak + app).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-store.ps1"
echo.
echo (This window can be closed. Keycloak and the app keep running in their own windows.)
pause
