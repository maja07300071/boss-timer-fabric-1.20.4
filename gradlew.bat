@echo off
setlocal EnableExtensions
set "APP_HOME=%~dp0"

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found. Install Java 17 and reopen this window.
  exit /b 1
)

"%JAVA_EXE%" -cp "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
