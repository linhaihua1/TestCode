#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Api-Web 快速启动（后端 API + 前端静态/反代一体，无需 Nginx）
# 环境变量：PORT=4000 HOST=0.0.0.0 FRONTEND_PORT=8080 API_ORIGIN=http://127.0.0.1:4000
# 访问：http://<服务器IP>:8080
# ============================================================

cd "$(dirname "$0")/.."

export PORT="${PORT:-4000}"
export HOST="${HOST:-0.0.0.0}"
export FRONTEND_PORT="${FRONTEND_PORT:-8080}"

echo "==> 启动后端：$HOST:$PORT"
node server/dist/index.js &
BACKEND_PID=$!

echo "==> 启动前端（静态 + /api、/mock 反向代理）：0.0.0.0:$FRONTEND_PORT"
node deploy/serve.mjs &
FRONTEND_PID=$!

echo ""
echo "  后端 API：http://<服务器IP>:$PORT/api"
echo "  前端界面：http://<服务器IP>:$FRONTEND_PORT"
echo "  按 Ctrl+C 停止。"
echo ""

trap 'echo "==> 正在停止…"; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null || true' EXIT INT TERM
wait
