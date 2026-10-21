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

rem Remove old container if present
docker ps -a --format "{{.Names}}" | findstr /x /c:"%CONTAINER%" >nul
if %errorlevel%==0 (
  echo Removing old container %CONTAINER%...
  docker rm -f %CONTAINER% >nul
)

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
