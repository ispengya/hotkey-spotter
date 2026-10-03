#!/bin/bash
# Dual Worker monitoring - robust version
# Usage: bash scripts/monitor-v2.sh <output_dir> <duration_seconds>
OUT_DIR="${1:-/tmp/monitor-logs}"
DUR="${2:-120}"

mkdir -p "$OUT_DIR"
PIDS=($(jps 2>/dev/null | grep -i "hot-spotter" | awk '{print $1}'))
[ ${#PIDS[@]} -eq 0 ] && echo "No worker found" && exit 1

echo "Monitoring ${#PIDS[@]} workers for ${DUR}s: ${PIDS[*]}"

# Init files
for PID in "${PIDS[@]}"; do
    PORT=$(netstat -ano 2>/dev/null | grep "$PID" | grep LISTEN | awk '{print $2}' | sed 's/.*://' | head -1)
    [ -z "$PORT" ] && PORT="pid-$PID"
    echo "ts,cpu_pct,mem_mb,heap_used_mb,ygc,fgc,gct_sec" > "$OUT_DIR/w-$PORT.csv"
    echo "# PID=$PID, Port=$PORT" >> "$OUT_DIR/w-$PORT.csv"
done

END=$(($(date +%s) + DUR))
SAMPLE=0
while [ $(date +%s) -lt $END ]; do
    SAMPLE=$((SAMPLE + 1))
    TS=$(date +%H:%M:%S)

    for PID in "${PIDS[@]}"; do
        PORT=$(netstat -ano 2>/dev/null | grep "$PID" | grep LISTEN | awk '{print $2}' | sed 's/.*://' | head -1)
        [ -z "$PORT" ] && PORT="pid-$PID"

        # CPU & Memory via PowerShell
        CPU=0; MEM=0
        if [ "$(uname -s)" = "MINGW64_NT" ] || [ "$(uname -s)" = "MSYS_NT" ]; then
            PM=$(powershell.exe -NoProfile -Command "\$p=Get-Process -Id $PID -ErrorAction SilentlyContinue;if(\$p){'{0:0.0} {1:0}' -f \$p.CPU,(\$p.WorkingSet64/1MB)}" 2>/dev/null)
            CPU=$(echo "$PM" | awk '{print $1}')
            MEM=$(echo "$PM" | awk '{print $2}' | cut -d. -f1)
        fi

        # jstat GC (strip decimal portion for shell math)
        YGC=0; FGC=0; GCT=0; HEAP=0
        JOUT=$(jstat -gc $PID 2>/dev/null | tail -1)
        if [ -n "$JOUT" ]; then
            OU=$(echo "$JOUT" | awk '{print $8}' | cut -d. -f1)
            MU=$(echo "$JOUT" | awk '{print $10}' | cut -d. -f1)
            YGC=$(echo "$JOUT" | awk '{print $13}' | cut -d. -f1)
            FGC=$(echo "$JOUT" | awk '{print $15}' | cut -d. -f1)
            GCT=$(echo "$JOUT" | awk '{print $17}' | cut -d. -f1)
            HEAP=$(( (${OU:-0} + ${MU:-0}) / 1024 ))
        fi

        echo "$TS,$CPU,$MEM,$HEAP,$YGC,$FGC,$GCT" >> "$OUT_DIR/w-$PORT.csv"
    done
    sleep 2
done
echo "Done. Samples=$SAMPLE. Files in $OUT_DIR/"
ls -la "$OUT_DIR/"
