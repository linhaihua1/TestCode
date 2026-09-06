#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Api-Web 一键安装 + 启动脚本（Linux）
#
#   首次运行：自动完成 安装依赖 → 生成 Prisma → 初始化数据库 → 构建 → 启动
#   再次运行：检测已安装则直接启动（秒级）
#
# 用法：
#   bash deploy/start.sh              一键安装并启动
#   bash deploy/start.sh --install    强制重装（依赖+构建）后启动
#   bash deploy/start.sh --install-only   只安装、不启动
#   AUTO_SEED=1 bash deploy/start.sh  安装时自动写入演示数据（免交互）
#
# 环境变量：PORT=4000 HOST=0.0.0.0 FRONTEND_PORT=8080 API_ORIGIN=http://127.0.0.1:4000
# ============================================================

cd "$(dirname "$0")/.."
APP_DIR="$(pwd)"

FORCE_INSTALL=0
INSTALL_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --install) FORCE_INSTALL=1 ;;
    --install-only) FORCE_INSTALL=1; INSTALL_ONLY=1 ;;
    -h|--help)
      sed -n '2,16p' "$0"; exit 0 ;;
  esac
done

# ---------- 是否缺少运行时/构建产物 ----------
needs_install() {
  [ ! -d node_modules ] || [ ! -f node_modules/.prisma/client/index.js ] || \
  [ ! -f server/dist/index.js ] || [ ! -f client/dist/index.html ]
}

# ---------- 安装依赖 + 构建 ----------
do_install() {
  if ! command -v node >/dev/null 2>&1; then
    echo "错误：未找到 Node.js，请先安装 Node 20+（https://nodejs.org）"; exit 1
  fi
  if [ "$(node -p 'process.versions.node.split(".")[0]')" -lt 20 ]; then
    echo "错误：需要 Node 20+，当前 $(node -v)"; exit 1
  fi
  echo "==> 项目目录：$APP_DIR"
  echo "==> Node $(node -v) 就绪"
  echo "==> 安装依赖（npm ci）…"
  npm ci
  echo "==> 生成 Prisma Client…"
  ( cd server && npx prisma generate )
  echo "==> 构建前后端…"
  npm run build
}

# ---------- 初始化数据库（仅首次，幂等） ----------
do_migrate() {
  if [ ! -f server/dev.db ]; then
    echo "==> 初始化数据库…"
    ( cd server && npx prisma migrate deploy )
    if [ "${AUTO_SEED:-0}" = "1" ]; then
      echo "==> 写入演示数据…"
      npm run seed -w @api-web/server
    elif [ -t 0 ]; then
      read -r -p "是否写入演示数据（admin/admin@123 + 演示项目）？[y/N] " yn || yn=""
      if [[ "$yn" =~ ^[Yy]$ ]]; then npm run seed -w @api-web/server; fi
    else
      echo "提示：可执行 npm run seed -w @api-web/server 写入演示数据（admin/admin@123）"
    fi
  fi
}

# ---------- 安装判断 ----------
if [ "$FORCE_INSTALL" = "1" ] || needs_install; then
  do_install
fi
do_migrate

if [ "$INSTALL_ONLY" = "1" ]; then
  echo ""
  echo "✅ 安装完成（未启动）。启动：bash deploy/start.sh"
  exit 0
fi

if [ ! -f server/jmeter/bin/ApacheJMeter.jar ]; then
  echo "提示：性能测试模块需要 JMeter 运行时（可选），详见 deploy/DEPLOY-README.md"
fi

# ---------- 启动后端 + 前端 ----------
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
