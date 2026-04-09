@echo off
setlocal enabledelayedexpansion

:: ---------------------------------------------------------------------------
:: Paths
:: ---------------------------------------------------------------------------
set SCRIPT_DIR=%~dp0
set JAR_NAME=${project.artifactId}.jar
set JAR_PATH=%SCRIPT_DIR%%JAR_NAME%

set HISTORY_DIR=%SCRIPT_DIR%..\benchmarks\history

:: Build a sortable YYYYMMDD-HHMMSS timestamp that works regardless of locale
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set DATETIME=%%I
set TIMESTAMP=%DATETIME:~0,8%-%DATETIME:~8,6%
set BASE_NAME=%~n0-%TIMESTAMP%

set LOG_FILE=%SCRIPT_DIR%%BASE_NAME%.log
set JSON_FILE=%SCRIPT_DIR%%BASE_NAME%.json

:: ---------------------------------------------------------------------------
:: Pre-flight check
:: ---------------------------------------------------------------------------
if not exist "%JAR_PATH%" (
    echo ERROR: Benchmark JAR not found: %JAR_PATH% 1>&2
    echo Please run 'mvn package' first. 1>&2
    exit /b 1
)

:: ---------------------------------------------------------------------------
:: System Information
:: ---------------------------------------------------------------------------
(
    echo === System Information ===
    echo Date:      %TIMESTAMP%
    for /f "tokens=2 delims==" %%C in ('wmic cpu get name /value 2^>nul') do echo CPU:       %%C
    java -version 2>&1
    echo JVM Args:  -Xms2G -Xmx2G -XX:+UseZGC -XX:+ZGenerational
    echo --------------------------
) > "%LOG_FILE%"
type "%LOG_FILE%"

:: ---------------------------------------------------------------------------
:: Run benchmarks
:: ---------------------------------------------------------------------------
echo Running benchmarks...

:: Note: The following line has the JMH-specific override flags removed/commented out.
:: To re-enable them, add them back to the java command below.
:: Disabled flags: -bm thrpt -tu s -f 3 -wi 5 -i 10

java -Xms2G -Xmx2G -XX:+UseZGC -XX:+ZGenerational -jar "%JAR_PATH%" ^
  -prof gc ^
  -rf json -rff "%JSON_FILE%" ^
  | powershell -NoProfile -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

:: ---------------------------------------------------------------------------
:: Archive results
:: ---------------------------------------------------------------------------
if exist "%SCRIPT_DIR%..\.git" (
    if not exist "%HISTORY_DIR%" mkdir "%HISTORY_DIR%"
    copy "%LOG_FILE%" "%HISTORY_DIR%\" >nul
    copy "%JSON_FILE%" "%HISTORY_DIR%\" >nul
    echo Results archived in: %HISTORY_DIR%
)

