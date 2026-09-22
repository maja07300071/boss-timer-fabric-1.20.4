@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo ========================================
echo   Boss Timer - Fabric 1.20.4 Builder
echo   Build JDK: Java 21
echo ========================================
echo.

set "JDK21="

rem 1) Optional project-specific override.
if defined BOSS_TIMER_JAVA_HOME call :try_jdk "%BOSS_TIMER_JAVA_HOME%"

rem 2) Existing JAVA_HOME.
if not defined JDK21 if defined JAVA_HOME call :try_jdk "%JAVA_HOME%"

rem 3) Common JDK 21 install locations.
if not defined JDK21 call :scan_pattern "%ProgramFiles%\Eclipse Adoptium\jdk-21*"
if not defined JDK21 call :scan_pattern "%ProgramFiles%\Microsoft\jdk-21*"
if not defined JDK21 call :scan_pattern "%ProgramFiles%\Java\jdk-21*"
if not defined JDK21 call :scan_pattern "%ProgramFiles%\Amazon Corretto\jdk21*"
if not defined JDK21 call :scan_pattern "%ProgramFiles%\Zulu\zulu-21*"
if not defined JDK21 call :scan_pattern "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21*"
if not defined JDK21 call :scan_pattern "%USERPROFILE%\.jdks\*21*"

rem 4) Try the JDK that provides javac on PATH.
if not defined JDK21 (
    for /f "delims=" %%J in ('where javac 2^>nul') do (
        if not defined JDK21 (
            for %%P in ("%%~dpJ..") do call :try_jdk "%%~fP"
        )
    )
)

if not defined JDK21 (
    echo [ERROR] JDK 21 was not found.
    echo.
    echo This build is configured to run with Java 21.
    echo Please install a full JDK 21, not only a Java runtime.
    echo.
    echo After installing JDK 21, run build.bat again.
    echo.
    echo If JDK 21 is already installed in a custom folder, run:
    echo   set "BOSS_TIMER_JAVA_HOME=C:\path\to\jdk-21"
    echo   build.bat
    echo.
    exit /b 1
)

set "JAVA_HOME=%JDK21%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [OK] Java 21 found:
echo      %JAVA_HOME%
echo.
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 (
    echo.
    echo [ERROR] Java 21 could not be started.
    exit /b 1
)

echo.
echo [INFO] Building with Gradle Wrapper...
echo [INFO] Gradle does not need to be installed manually.
echo [INFO] The first build may download Gradle/Fabric/Minecraft dependencies.
echo.

call "%~dp0gradlew.bat" --no-daemon clean build
set "BUILD_RESULT=%ERRORLEVEL%"

if not "%BUILD_RESULT%"=="0" (
    echo.
    echo [ERROR] Build failed with exit code %BUILD_RESULT%.
    echo Copy the error text above and send it back to me.
    exit /b %BUILD_RESULT%
)

echo.
echo [OK] BUILD SUCCESSFUL
echo [OK] Output folder:
echo      %~dp0build\libs
echo.

if exist "%~dp0build\libs" explorer "%~dp0build\libs"
exit /b 0

:scan_pattern
for /d %%D in (%1) do (
    if not defined JDK21 call :try_jdk "%%~fD"
)
exit /b 0

:try_jdk
set "CANDIDATE=%~1"
if not exist "!CANDIDATE!\bin\java.exe" exit /b 0
if not exist "!CANDIDATE!\bin\javac.exe" exit /b 0

set "VERFILE=%TEMP%\bosstimer-java-version-!RANDOM!-!RANDOM!.txt"
"!CANDIDATE!\bin\java.exe" -version >"!VERFILE!" 2>&1
set "FIRSTLINE="
set /p "FIRSTLINE="<"!VERFILE!"
del /q "!VERFILE!" >nul 2>&1

set "JAVA_VER="
for /f "tokens=3" %%V in ("!FIRSTLINE!") do set "JAVA_VER=%%~V"
if "!JAVA_VER:~0,3!"=="21." set "JDK21=!CANDIDATE!"
exit /b 0
