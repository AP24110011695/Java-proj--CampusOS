@echo off
REM CampusOS - create the FIRST administrator account (run once after schema.sql)
REM Usage: scripts\bootstrap-admin.bat "Full Name" admin@campus.edu "Admin@123"
cd /d "%~dp0.."
if "%~1"=="" (
  echo Interactive mode - you will be asked for the details:
  call mvn compile exec:java -Dexec.mainClass=com.campusos.tool.AdminBootstrap
) else (
  call mvn compile exec:java -Dexec.mainClass=com.campusos.tool.AdminBootstrap -Dexec.args="--name \"%~1\" --email %~2 --password \"%~3\""
)
pause
