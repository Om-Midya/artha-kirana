#!/usr/bin/env bash
# Artha — start the on-device LLM (llama-server serving Qwen 2.5 3B) on the phone.
# This is DEMO RUNBOOK STEP 0: the app reaches the model over HTTP on 127.0.0.1:8080.
#
# Usage:  ./scripts/start-llama-server.sh
# Stop:   adb shell "pkill -f llama-server"
# Logs:   adb shell tail -f /sdcard/Download/llama-server.log
set -euo pipefail

ADB="${ADB:-adb}"
LLAMA_DIR="/data/local/tmp/llama/llama-b9620"
MODEL="/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf"
PORT="${PORT:-8080}"

echo "Starting llama-server on the phone (port ${PORT})..."
"$ADB" shell "cd ${LLAMA_DIR} && LD_LIBRARY_PATH=. nohup ./llama-server \
  -m ${MODEL} --host 127.0.0.1 --port ${PORT} -c 2048 -t 6 \
  > /sdcard/Download/llama-server.log 2>&1 &"

echo "Waiting for /health to come up (cold mmap of ~2.4GB can take ~10-20s)..."
for i in $(seq 1 40); do
  code="$("$ADB" shell "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${PORT}/health" 2>/dev/null || true)"
  if [ "$code" = "200" ]; then
    echo "llama-server is UP (HTTP 200 on /health) after ~$((i*2))s."
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for /health. Check: adb shell tail -n 40 /sdcard/Download/llama-server.log" >&2
exit 1
