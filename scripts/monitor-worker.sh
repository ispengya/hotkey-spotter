#!/bin/bash
# Worker 进程监控脚本
# 用法: bash scripts/monitor-worker.sh <output_file> [duration_seconds]
# 在压测期间后台运行，每 2 秒采样一次 Worker 进程的 CPU / 内存 / GC

OUTPUT_FILE="${1:-worker-monitor.log}"
DURATION="${2:-60}"
INTERVAL=2

echo "# Timestamp, CPU%, MemMB, HeapUsedMB, HeapMaxMB, GCYoungCount, GCFullCount, GCTimeMs" > "$OUTPUT_FILE"
echo "# Monitoring Worker process for ${DURATION}s, interval=${INTERVAL}s" >> "$OUTPUT_FILE"

# 找到 Worker JVM 进程
get_worker_pid() {
    jps 2>/dev/null | grep -i "HotKeyServerApplication\|hot-spotter-server" | awk '{print $1}' | head -1
}

PID=$(get_worker_pid)
if [ -z "$PID" ]; then
    echo "ERROR: Worker process not found. Is it running?" | tee -a "$OUTPUT_FILE"
    exit 1
fi
echo "# Worker PID=$PID" >> "$OUTPUT_FILE"

START_TS=$(date +%s)
END_TS=$((START_TS + DURATION))

SAMPLE=0
while [ $(date +%s) -lt $END_TS ]; do
    SAMPLE=$((SAMPLE + 1))
    TS=$(date +%H:%M:%S)

    # CPU 和内存：通过 PowerShell (Windows) 或 ps (Linux)
    if [ "$(uname -s)" = "MINGW64_NT" ] || [ "$(uname -s)" = "MSYS_NT" ]; then
        # Git Bash on Windows
        CPU_MEM=$(powershell.exe -NoProfile -Command "
            \$p = Get-Process -Id $PID -ErrorAction SilentlyContinue;
            if (\$p) {
                '{0:0.0} {1:0.0}' -f \$p.CPU, (\$p.WorkingSet64/1MB)
            } else { '0 0' }
        " 2>/dev/null)
        CPU=$(echo "$CPU_MEM" | awk '{print $1}')
        MEM_MB=$(echo "$CPU_MEM" | awk '{print $2}')
    else
        # Linux
        CPU=$(ps -p $PID -o %cpu --no-headers 2>/dev/null | tr -d ' ' || echo "0")
        MEM_KB=$(ps -p $PID -o rss --no-headers 2>/dev/null | tr -d ' ' || echo "0")
        MEM_MB=$(echo "scale=1; $MEM_KB / 1024" | bc 2>/dev/null || echo "0")
    fi

    # JVM 堆和 GC
    HEAP_USED=0; HEAP_MAX=0; YGC=0; FGC=0; GCT=0
    if command -v jstat &>/dev/null; then
        JSTAT_OUT=$(jstat -gc $PID 2>/dev/null | tail -1)
        if [ -n "$JSTAT_OUT" ]; then
            # S0C S1C S0U S1U EC EU OC OU MC MU CCSC CCSU YGC YGCT FGC FGCT GCT
            OC=$(echo "$JSTAT_OUT" | awk '{print $7}')
            OU=$(echo "$JSTAT_OUT" | awk '{print $8}')
            MC=$(echo "$JSTAT_OUT" | awk '{print $9}')
            MU=$(echo "$JSTAT_OUT" | awk '{print $10}')
            YGC=$(echo "$JSTAT_OUT" | awk '{print $13}')
            YGCT=$(echo "$JSTAT_OUT" | awk '{print $14}')
            FGC=$(echo "$JSTAT_OUT" | awk '{print $15}')
            FGCT=$(echo "$JSTAT_OUT" | awk '{print $16}')
            GCT=$(echo "$JSTAT_OUT" | awk '{print $17}')
            HEAP_USED=$(( (OU + MU) / 1024 ))
            HEAP_MAX=$(( (OC + MC) / 1024 ))
        fi
    fi

    echo "$TS, $CPU, $MEM_MB, $HEAP_USED, $HEAP_MAX, $YGC, $FGC, $GCT" >> "$OUTPUT_FILE"

    sleep $INTERVAL
done

echo "# Monitoring complete. Samples=$SAMPLE" >> "$OUTPUT_FILE"
echo "[monitor] Done. Result: $OUTPUT_FILE"
