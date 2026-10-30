@echo off
setlocal enabledelayedexpansion

set IMAGE=apinictra
set CONTAINER=apinictra

rem Capturar el ID de la imagen anterior si existe
for /f "delims=" %%i in ('docker image inspect %IMAGE% --format "{{.Id}}" 2^>nul') do set "OLD_IMAGE=%%i"

echo Building %IMAGE%...
docker build -t %IMAGE% . || goto :fail

echo Limpiando contenedor anterior...
docker rm -f %CONTAINER% >nul 2>&1

rem Pausa breve para liberar recursos en Windows
timeout /t 2 /nobreak >nul

rem Eliminar la imagen vieja si cambió
if defined OLD_IMAGE (
    for /f "delims=" %%i in ('docker image inspect %IMAGE% --format "{{.Id}}" 2^>nul') do set "NEW_IMAGE=%%i"
    if not "!OLD_IMAGE!"=="!NEW_IMAGE!" (
        echo Removing old image...
        docker rmi !OLD_IMAGE! >nul 2>&1
    )
)

set "RUNENV="
if exist .env set "RUNENV=--env-file .env"

echo Starting new container...
docker run -d --name %CONTAINER% --restart unless-stopped -p 80:80 %RUNENV% %IMAGE% || goto :fail

echo.
echo Done.
echo Feed:  http://localhost/api/feed
echo Admin: http://localhost/admin
exit /b 0

:fail
echo.
echo FAILED with error %errorlevel%
exit /b %errorlevel%