#!/bin/bash
# 四组全链路实验：单/双 worker × 锁/双map
# 每组：重启 worker 清空 dedup 缓存与累计基线 → 采样 → 跑 benchmark → 解析
set -u
ROOT="D:/workspace-v2/hk/hot-spotter"
NACOS="http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=hotkey-config-metrics&group="

W1_GROUP="worker-172.17.0.2:8888"
W2_GROUP="worker-172.17.0.3:8889"

# 重启并等待 worker 重新发布清零后的指标（received 归 0）
restart_workers() {
  echo ">>> 重启 worker..."
  docker restart hotspotter-worker hotspotter-worker2 >/dev/null 2>&1
  for G in "$W1_GROUP" "$W2_GROUP"; do
    local ok=0
    for i in $(seq 1 40); do
      R=$(curl -s -m 5 "${NACOS}${G}" 2>/dev/null | python -c "import sys,json;print(json.load(sys.stdin).get('received',-1))" 2>/dev/null || echo -1)
      if [ "$R" = "0" ]; then ok=1; break; fi
      sleep 2
    done
    echo "    $G ready=$ok"
  done
  # 多等 2s，让所有 group 的 metrics 都刷新完毕
  sleep 2
}

MASTER="/tmp/four-exps-master.log"
: > "$MASTER"

run_one() {
  local MODE="$1" WORKERS="$2" GROUP_LIST="$3" TAG="$4"
  restart_workers | tee -a "$MASTER"
  bash "$ROOT/scripts/run-exp.sh" "$MODE" "$WORKERS" "$GROUP_LIST" "$TAG" | tee -a "$MASTER"
}

run_one lock   "127.0.0.1:8888"                       "$W1_GROUP"                 "exp1-single-lock"
run_one double "127.0.0.1:8888"                       "$W1_GROUP"                 "exp2-single-double"
run_one lock   "127.0.0.1:8888,127.0.0.1:8889"        "$W1_GROUP,$W2_GROUP"       "exp3-dual-lock"
run_one double "127.0.0.1:8888,127.0.0.1:8889"        "$W1_GROUP,$W2_GROUP"       "exp4-dual-double"

echo "全部实验完成。结果见 $MASTER"
