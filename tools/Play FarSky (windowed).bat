@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java 17 or newer is required but was not found on this computer.
    echo Install it from https://adoptium.net/ and run this script again.
    pause
    exit /b 1
)

set "CP=lib\*"
if exist "farsky.jar" set "CP=%CP%;farsky.jar"
if exist "res" set "CP=%CP%;res"

java -Djava.library.path=native\windows -Dsun.java2d.d3d=false -cp "%CP%" game.Main -windowMode %*
if not "%errorlevel%"=="0" (
    echo.
    echo The game exited with an error. See farsky.log in this folder for details.
    pause
)
