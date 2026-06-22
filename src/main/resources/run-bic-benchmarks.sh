#!/bin/bash
# run-bic-benchmarks.sh - automated JMH benchmark run for BICs
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
# Note: JVM args are defined in @Fork(jvmArgs = {...}) in BicBenchmarks.java.
#       The launcher below uses only a minimal heap to host the JMH runner process;
#       each benchmark fork starts its own JVM with the annotation-specified args.
# ---------------------------------------------------------------------------
{
    echo "=== System Information ==="
    echo "Date:     $(date +"%Y%m%d-%H%M%S")"
    echo "OS:       $(uname -sr)"
    echo "CPU:      $(lscpu 2>/dev/null | grep 'Model name' | cut -d ':' -f 2 | xargs || echo 'unknown')"
    echo "Java:     $(java -version 2>&1 | head -n 1)"
    echo "Fork JVM: -Xms2G -Xmx2G -XX:+AlwaysPreTouch -XX:+UseSerialGC -XX:-StackTraceInThrowable"
    echo "--------------------------"
} | tee "$LOG_FILE"

# ---------------------------------------------------------------------------
# Run benchmarks
# ---------------------------------------------------------------------------
# The launcher JVM only drives the JMH harness. All performance-relevant JVM
# flags (-XX:+UseSerialGC, -XX:+AlwaysPreTouch, heap size, etc.) are declared
# in IbanBenchmarks.java via @Fork(jvmArgs = {...}) and apply to the child
# processes that actually run the benchmarks.
#
# Taskset pins both the launcher and (via fork inheritance) all child JVMs to
# core 0. SerialGC has no background threads, so a single core is sufficient.
# ---------------------------------------------------------------------------
JAVA_CMD=(java
    -jar "$JAR_PATH"
    BicBenchmarks
    -prof gc
    -rf json
    -rff "$JSON_FILE")

# Development-only overrides (uncomment to iterate faster; do not commit enabled):
# JAVA_CMD+=(-f 1 -wi 3 -w 1s -i 3 -r 1s)
# JFR profiling (uncomment to record; produces large files):
# JAVA_CMD+=(-prof "jfr:dir=$JFR_DIR;configName=profile")

echo "Starting IBAN benchmarks..." | tee -a "$LOG_FILE"

if [ "$USE_TASKSET" = true ]; then
    echo "Running with taskset -c 0 (SerialGC has no background threads)" | tee -a "$LOG_FILE"
    taskset -c 0 "${JAVA_CMD[@]}" | tee -a "$LOG_FILE"
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
