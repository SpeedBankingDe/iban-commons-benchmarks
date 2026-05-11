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
set JFR_DIR=%SCRIPT_DIR%jfr-reports

:: ---------------------------------------------------------------------------
:: Pre-flight check
:: ---------------------------------------------------------------------------
if not exist "%JAR_PATH%" (
    echo ERROR: Benchmark JAR not found: %JAR_PATH% 1>&2
    echo Please run 'mvn package' first. 1>&2
    exit /b 1
)

if not exist "%JFR_DIR%" mkdir "%JFR_DIR%"

:: ---------------------------------------------------------------------------
:: System Information
:: ---------------------------------------------------------------------------
:: Note: JVM args are defined in @Fork(jvmArgs = {...}) in IbanBenchmarks.java.
::       The launcher below uses only a minimal heap to host the JMH runner process;
::       each benchmark fork starts its own JVM with the annotation-specified args.
:: ---------------------------------------------------------------------------
(
    echo === System Information ===
    echo Date:      %TIMESTAMP%
    for /f "tokens=2 delims==" %%C in ('wmic cpu get name /value 2^>nul') do echo CPU:       %%C
    java -version 2>&1
    echo Fork JVM:  -Xms2G -Xmx2G -XX:+AlwaysPreTouch -XX:+UseSerialGC -XX:-StackTraceInThrowable
    echo --------------------------
) > "%LOG_FILE%"
type "%LOG_FILE%"

:: ---------------------------------------------------------------------------
:: Run benchmarks
:: ---------------------------------------------------------------------------
:: The launcher JVM only drives the JMH harness. All performance-relevant JVM
:: flags are declared in IbanBenchmarks.java via @Fork(jvmArgs = {...}) and
:: apply to the child processes that actually run the benchmarks.
::
:: Development-only overrides (uncomment to iterate faster; do not commit enabled):
::   -f 1 -wi 3 -w 1s -i 3 -r 1s
:: ---------------------------------------------------------------------------
echo Running benchmarks...

java -jar "%JAR_PATH%" ^
  -prof gc ^
  -rf json -rff "%JSON_FILE%" ^
  | powershell -NoProfile -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

:: ---------------------------------------------------------------------------
:: Archive results
:: ---------------------------------------------------------------------------
if exist "%SCRIPT_DIR%..\\.git" (
    if not exist "%HISTORY_DIR%" mkdir "%HISTORY_DIR%"
    copy "%LOG_FILE%" "%HISTORY_DIR%\" >nul
    copy "%JSON_FILE%" "%HISTORY_DIR%\" >nul
    echo Results archived in: %HISTORY_DIR%
)

echo --------------------------
echo Log:  %LOG_FILE%
echo JSON: %JSON_FILE%
echo JFR:  %JFR_DIR%
echo Done.

