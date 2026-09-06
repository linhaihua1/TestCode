#!/usr/bin/env bash
set -uo pipefail

# 停止由 deploy/start.sh 拉起的后端与前端进程（按命令行特征匹配）
pkill -f 'node server/dist/index.js' 2>/dev/null && echo "已停止后端" || echo "后端未在运行（start.sh 方式）"
pkill -f 'node deploy/serve.mjs' 2>/dev/null && echo "已停止前端" || echo "前端未在运行（start.sh 方式）"

echo "提示：如使用 systemd 部署，请用：sudo systemctl stop api-web"
