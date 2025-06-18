@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

REM Set service details
SET SERVICE_NAME=RobotCarSecurityMonitor
SET SERVICE_DISPLAY_NAME=RobotCar Security Monitor
SET SERVICE_EXECUTABLE=%~dp0RobotCarSecurityMonitor.exe

REM Check if ffmpeg exists
IF NOT EXIST "%~dp0ffmpeg\ffmpeg.exe" (
    echo ERROR: ffmpeg.exe not found in "%~dp0ffmpeg"
    EXIT /B 1
)

REM Add ffmpeg folder to system PATH if not already present
set "FFMPEG_PATH=%~dp0ffmpeg"
for /f "tokens=2*" %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v Path 2^>nul') do (
    set "CurrentPath=%%B"
)

echo !CurrentPath! | find /I "!FFMPEG_PATH!" >nul
if errorlevel 1 (
    echo Adding ffmpeg to system PATH...
    setx /M PATH "!CurrentPath!;!FFMPEG_PATH!"
)

REM Delete existing service if exists
sc query "%SERVICE_NAME%" >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo Service "%SERVICE_NAME%" already exists. Deleting it first...
    sc stop "%SERVICE_NAME%" >nul 2>&1
    timeout /t 2 >nul
    sc delete "%SERVICE_NAME%"
    timeout /t 2 >nul
)

REM Create service using PowerShell for better reliability
echo Creating Windows Service "%SERVICE_NAME%"...
powershell -Command "$servicePath = '%SERVICE_EXECUTABLE%'; New-Service -Name '%SERVICE_NAME%' -BinaryPathName \"`\"$servicePath`\"\" -DisplayName '%SERVICE_DISPLAY_NAME%' -StartupType Automatic; icacls (Split-Path $servicePath) /grant \"NT AUTHORITY\SYSTEM:(OI)(CI)F\""

REM Verify service creation
timeout /t 3 >nul
sc query "%SERVICE_NAME%" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Failed to create service "%SERVICE_NAME%".
    EXIT /B 1
)

echo Service created successfully.
echo Starting the service...
sc start "%SERVICE_NAME%"
timeout /t 2 >nul

EXIT /B 0