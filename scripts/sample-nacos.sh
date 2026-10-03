#!/bin/bash
# 后台采样多个 worker 的 Nacos 指标（JSONL，每行一条，含 _group 字段）
# usage: sample-nacos.sh <groups:g1,g2> <tag> <seconds>
set -u
GROUP_LIST="$1"; TAG="$2"; SECS="${3:-45}"
IFS=',' read -ra GS <<< "$GROUP_LIST"
OUT="/tmp/sample-$TAG.jsonl"
: > "$OUT"
END=$((SECONDS + SECS))
while [ $SECONDS -lt $END ]; do
  for G in "${GS[@]}"; do
    curl -s -m 5 "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=hotkey-config-metrics&group=$G" 2>/dev/null \
      | GROUP="$G" python -c "import sys,os,json;d=json.load(sys.stdin);d['_group']=os.environ['GROUP'];print(json.dumps(d,ensure_ascii=False))" >> "$OUT" 2>/dev/null
  done
  sleep 3
done
echo "sampling done -> $OUT"
