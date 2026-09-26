@echo off
rem Dhole command line. Requires a JDK 21 or newer (JAVA_HOME or java on PATH).
setlocal
set "DHOLE_HOME=%~dp0.."
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if defined JAVA_HOME if not exist "%JAVA_EXE%" goto noJava
if not defined JAVA_HOME where java.exe >nul 2>nul || goto noJava

"%JAVA_EXE%" %DHOLE_OPTS% "-Ddhole.home=%DHOLE_HOME%" -cp "%DHOLE_HOME%\lib\*" org.dhole.internal.cli.JavaCheck %*
exit /b %ERRORLEVEL%

:noJava
echo Dhole requires a JDK 21 or newer, but Java was not found. 1>&2
echo Install a JDK and set JAVA_HOME, or add java to PATH. 1>&2
exit /b 1
