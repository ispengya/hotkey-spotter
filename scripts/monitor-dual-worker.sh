#!/bin/bash
# 双 Worker 进程监控脚本
# 用法: bash scripts/monitor-dual-worker.sh <output_dir> [duration_seconds]
# 同时对两个 Worker 进程进行 CPU / 内存 / GC 采样

OUTPUT_DIR="${1:-./monitor-logs}"
DURATION="${2:-60}"
INTERVAL=2

mkdir -p "$OUTPUT_DIR"

# 找到所有 Worker JVM 进程（可能多个）
get_worker_pids() {
    jps 2>/dev/null | grep -i "HotKeyServerApplication\|hot-spotter-server" | awk '{print $1}'
}

PIDS=($(get_worker_pids))
if [ ${#PIDS[@]} -eq 0 ]; then
    echo "ERROR: No Worker process found. Is it running?" | tee "$OUTPUT_DIR/error.log"
    exit 1
fi

echo "Found ${#PIDS[@]} Worker process(es): ${PIDS[*]}"
for PID in "${PIDS[@]}"; do
    PORT=$(netstat -ano 2>/dev/null | grep "$PID" | grep LISTEN | awk '{print $2}' | sed 's/.*://' | head -1)
    if [ -z "$PORT" ]; then
        # fallback: use PID
        PORT="pid-$PID"
    fi
    OUTFILE="$OUTPUT_DIR/worker-$PORT.csv"
    echo "# Timestamp, CPU%, MemMB, HeapUsedMB, HeapMaxMB, YGC, FGC, GCT_sec" > "$OUTFILE"
    echo "# Worker PID=$PID Port=$PORT" >> "$OUTFILE"
done

echo "# Monitoring ${#PIDS[@]} workers for ${DURATION}s, interval=${INTERVAL}s"
echo "# Output: $OUTPUT_DIR/"

START_TS=$(date +%s)
END_TS=$((START_TS + DURATION))
SAMPLE=0

while [ $(date +%s) -lt $END_TS ]; do
    SAMPLE=$((SAMPLE + 1))
    TS=$(date +%H:%M:%S)

    for PID in "${PIDS[@]}"; do
        PORT=$(netstat -ano 2>/dev/null | grep "$PID" | grep LISTEN | awk '{print $2}' | sed 's/.*://' | head -1)
        [ -z "$PORT" ] && PORT="pid-$PID"
        OUTFILE="$OUTPUT_DIR/worker-$PORT.csv"

        # CPU 和内存
        CPU=0; MEM_MB=0
        if [ "$(uname -s)" = "MINGW64_NT" ] || [ "$(uname -s)" = "MSYS_NT" ]; then
            CPU_MEM=$(powershell.exe -NoProfile -Command "
                \$p = Get-Process -Id $PID -ErrorAction SilentlyContinue;
                if (\$p) {
                    '{0:0.0} {1:0.0}' -f \$p.CPU, (\$p.WorkingSet64/1MB)
                } else { '0 0' }
            " 2>/dev/null)
            CPU=$(echo "$CPU_MEM" | awk '{print $1}')
            MEM_MB=$(echo "$CPU_MEM" | awk '{print $2}')
        else
            CPU=$(ps -p $PID -o %cpu --no-headers 2>/dev/null | tr -d ' ' || echo "0")
            MEM_KB=$(ps -p $PID -o rss --no-headers 2>/dev/null | tr -d ' ' || echo "0")
            MEM_MB=$(echo "scale=1; $MEM_KB / 1024" | bc 2>/dev/null || echo "0")
        fi

        # JVM 堆和 GC
        HEAP_USED=0; HEAP_MAX=0; YGC=0; FGC=0; GCT=0
        if command -v jstat &>/dev/null; then
            JSTAT_OUT=$(jstat -gc $PID 2>/dev/null | tail -1)
            if [ -n "$JSTAT_OUT" ]; then
                OC=$(echo "$JSTAT_OUT" | awk '{print $7}')
                OU=$(echo "$JSTAT_OUT" | awk '{print $8}')
                MC=$(echo "$JSTAT_OUT" | awk '{print $9}')
                MU=$(echo "$JSTAT_OUT" | awk '{print $10}')
                YGC=$(echo "$JSTAT_OUT" | awk '{print $13}')
                FGC=$(echo "$JSTAT_OUT" | awk '{print $15}')
                GCT=$(echo "$JSTAT_OUT" | awk '{print $17}')
                HEAP_USED=$(( (OU + MU) / 1024 ))
                HEAP_MAX=$(( (OC + MC) / 1024 ))
            fi
        fi

        echo "$TS, $CPU, $MEM_MB, $HEAP_USED, $HEAP_MAX, $YGC, $FGC, $GCT" >> "$OUTFILE"
    done

    sleep $INTERVAL
done

echo "# Monitoring complete. Samples=$SAMPLE"
echo "# Results saved to: $OUTPUT_DIR/"
ls -la "$OUTPUT_DIR/"
