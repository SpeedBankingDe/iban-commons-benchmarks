#!/bin/bash
# run-benchmarks.sh - automated JMH benchmark run
# version: ${project.version}
set -euo pipefail

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NAME="${project.artifactId}.jar"
JAR_PATH="$SCRIPT_DIR/$JAR_NAME"

# History lives one level up from target/, inside the project tree
HISTORY_DIR="$SCRIPT_DIR/../benchmarks/history"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
BASE_NAME="$(basename "$0" .sh)-$TIMESTAMP"

LOG_FILE="$SCRIPT_DIR/$BASE_NAME.log"
JSON_FILE="$SCRIPT_DIR/$BASE_NAME.json"
JFR_DIR="$SCRIPT_DIR/jfr-reports"

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: Benchmark JAR not found: $JAR_PATH" >&2
    echo "Please run 'mvn package' first." >&2
    exit 1
fi

if ! command -v taskset &>/dev/null; then
    echo "WARNING: 'taskset' not found – running without CPU affinity. Install util-linux for reproducible results." >&2
    USE_TASKSET=false
else
    USE_TASKSET=true
fi

# Ensure JFR directory exists
mkdir -p "$JFR_DIR"

# ---------------------------------------------------------------------------
# System Information
# ---------------------------------------------------------------------------
{
    echo "=== System Information ==="
    echo "Date:     $(date +"%Y%m%d-%H%M%S")"
    echo "OS:       $(uname -sr)"
    echo "CPU:      $(lscpu 2>/dev/null | grep 'Model name' | cut -d ':' -f 2 | xargs || echo 'unknown')"
    echo "Java:     $(java -version 2>&1 | head -n 1)"
    echo "JVM Args: -Xms2G -Xmx2G -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch"
    echo "--------------------------"
} | tee "$LOG_FILE"

# ---------------------------------------------------------------------------
# Run benchmarks
# ---------------------------------------------------------------------------
# Note: Using -prof "jfr:..." to analyze CPU hotspots and allocation stalls.
# Taskset is expanded to two cores (0,1) to allow ZGC background work.
# ---------------------------------------------------------------------------
JAVA_CMD=(java 
    -Xms2G 
    -Xmx2G 
    -XX:+UseZGC 
    -XX:+ZGenerational 
    -XX:+AlwaysPreTouch 
    -jar "$JAR_PATH"    # -bm thrpt
    # -tu s
    # -f 1          # Reduziert auf 1 Fork für schnellere Ergebnisse während der Entwicklung
    # -wi 3         # 3 Warmup Iterationen
    # -w 1s         # EXPLICIT: 1 Sekunde Warmup Zeit
    # -i 5          # 5 Measurement Iterationen
    # -r 1s         # EXPLICIT: 1 Sekunde Measurement Zeit
    # -prof "jfr:dir=$JFR_DIR;configName=profile"
    # -prof stack
    -prof gc
    -rf json 
    -rff "$JSON_FILE")

echo "Starting benchmarks..." | tee -a "$LOG_FILE"

if [ "$USE_TASKSET" = true ]; then
    echo "Running with taskset -c 0,1 (Core 0: Benchmark, Core 1: ZGC)" | tee -a "$LOG_FILE"
    taskset -c 0,1 "${JAVA_CMD[@]}" | tee -a "$LOG_FILE"
else
    "${JAVA_CMD[@]}" | tee -a "$LOG_FILE"
fi

# ---------------------------------------------------------------------------
# Post-processing
# ---------------------------------------------------------------------------
if [ -d "$HISTORY_DIR" ] && [ -f "$JSON_FILE" ]; then
    cp "$JSON_FILE" "$HISTORY_DIR/"
    echo "Results copied to history: $HISTORY_DIR"
fi

echo "--------------------------"
echo "Log:  $LOG_FILE"
echo "JSON: $JSON_FILE"
echo "JFR:  $JFR_DIR"
echo "Done."
