@echo off
setlocal
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%
