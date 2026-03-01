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

# ---------------------------------------------------------------------------
# System Information
# ---------------------------------------------------------------------------
{
    echo "=== System Information ==="
    echo "Date:     $(date +"%Y%m%d-%H%M%S")"
    echo "OS:       $(uname -sr)"
    echo "CPU:      $(lscpu 2>/dev/null | grep 'Model name' | cut -d ':' -f 2 | xargs || echo 'unknown')"
    echo "Java:     $(java -version 2>&1 | head -n 1)"
    echo "JVM Args: -Xms2G -Xmx2G -XX:+UseZGC -XX:+ZGenerational"
    echo "--------------------------"
} | tee "$LOG_FILE"

# ---------------------------------------------------------------------------
# Run benchmarks (pinned to core 0 when taskset is available)
# ---------------------------------------------------------------------------
JAVA_CMD=(java -Xms2G -Xmx2G -XX:+UseZGC -XX:+ZGenerational -jar "$JAR_PATH"
    -bm thrpt -tu s -f 3 -wi 5 -i 10
    -prof gc
    -rf json -rff "$JSON_FILE")

if [ "$USE_TASKSET" = true ]; then
    taskset -c 0 "${JAVA_CMD[@]}" | tee -a "$LOG_FILE"
else
    "${JAVA_CMD[@]}" | tee -a "$LOG_FILE"
fi

# ---------------------------------------------------------------------------
# Archive results
# ---------------------------------------------------------------------------
if [ -d "$SCRIPT_DIR/../.git" ]; then
    mkdir -p "$HISTORY_DIR"
    cp "$LOG_FILE" "$JSON_FILE" "$HISTORY_DIR/"
    echo "Results archived in: $HISTORY_DIR"
fi

