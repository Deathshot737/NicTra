@echo off
setlocal enabledelayedexpansion

set IMAGE=apinictra
set CONTAINER=apinictra

rem Capture current image id (empty if none yet)
set OLD_IMAGE=
docker image inspect %IMAGE% --format {{.Id}} >nul 2>&1
if %errorlevel%==0 (
  for /f "delims=" %%i in ('docker image inspect %IMAGE% --format {{.Id}}') do set OLD_IMAGE=%%i
)

echo Building %IMAGE%...
docker build -t %IMAGE% . || goto :fail

rem Forzar limpieza total del contenedor por nombre Y por ID si estuviera colgado
docker ps -a --filter "name=^/%CONTAINER%$" --format "{{.ID}}" > container_id.tmp
set /p TARGET_ID=<container_id.tmp
del container_id.tmp

if not "!TARGET_ID!"=="" (
  echo Removing locked container !TARGET_ID! (%CONTAINER%)...
  docker rm -f !TARGET_ID! >nul 2>&1
)

rem Fallback por si acaso el nombre sigue retenido
docker rm -f %CONTAINER% >nul 2>&1

rem Breve pausa de seguridad para Windows
timeout /t 2 /nobreak >nul

rem Remove the previous image now that the rebuild is done
if defined OLD_IMAGE (
  set NEW_IMAGE=
  for /f "delims=" %%i in ('docker image inspect %IMAGE% --format {{.Id}}') do set NEW_IMAGE=%%i
  if not "!OLD_IMAGE!"=="!NEW_IMAGE!" (
    echo Removing old image...
    docker rmi !OLD_IMAGE! >nul 2>&1
  )
)

set RUNENV=
if exist .env set RUNENV=--env-file .env

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