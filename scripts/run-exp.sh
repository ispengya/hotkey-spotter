#!/bin/bash
# 单组全链路实验：客户端 record 吞吐 + 端到端延迟 + worker 采样指标
# usage: run-exp.sh <mode:lock|double> <workers:host:port,...> <groups:g1,g2> <tag>
set -u
MODE="$1"; WORKERS="$2"; GROUP_LIST="$3"; TAG="$4"
ROOT="D:/workspace-v2/hk/hot-spotter"
CP="$ROOT/hot-spotter-example/target/classes;$(cat /tmp/example-cp.txt)"
SECS=50   # 采样时长（略长于 benchmark，覆盖完整测量窗）

echo "===== 实验 [$TAG] mode=$MODE workers=$WORKERS ====="

# 1. 后台启动采样
bash "$ROOT/scripts/sample-nacos.sh" "$GROUP_LIST" "$TAG" "$SECS" &
SAMPLER_PID=$!

# 2. 跑 benchmark（输出完整到文件）
BENCH_OUT="/tmp/bench-$TAG.log"
java -Dfile.encoding=UTF-8 -cp "$CP" com.ispengya.hotkey.example.perf.SdkReportBenchmark \
  --mode "$MODE" --workers "$WORKERS" --threads 8 --keys 50000 --hot-keys 500 --hot-ratio 0.9 \
  --report-period-ms 500 --duration 30 --warmup 3 > "$BENCH_OUT" 2>&1

# 3. 等采样结束
wait $SAMPLER_PID 2>/dev/null

# 4. 打印 benchmark 结果
echo "---- 客户端 ----"
grep -E "record=|e2e延迟|单 map|双 map" "$BENCH_OUT"

# 5. 打印 worker 采样结果
echo "---- worker 采样 ----"
python "$ROOT/scripts/parse-sample.py" "/tmp/sample-$TAG.jsonl"

echo "===== 实验 [$TAG] 结束 ====="
echo
