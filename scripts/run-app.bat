@echo off
REM CampusOS - build and run the desktop application (Windows)
cd /d "%~dp0.."
echo Building CampusOS...
call mvn -q clean compile
if errorlevel 1 (
  echo Build failed. Check that JDK 21 and Maven are installed and on PATH.
  pause
  exit /b 1
)
echo Starting CampusOS...
call mvn javafx:run
