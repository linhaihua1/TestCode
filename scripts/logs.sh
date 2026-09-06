#!/usr/bin/env bash
#
# Api-Web 日志查看脚本
# 用法：
#   ./scripts/logs.sh                实时跟踪所有日志
#   ./scripts/logs.sh --backend      只看后端
#   ./scripts/logs.sh --frontend     只看前端
#   ./scripts/logs.sh --docker       只看容器（需在有 docker-compose 的目录）
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info() { echo -e "${BLUE}[INFO]${NC}  $*"; }

mkdir -p logs

case "${1:-}" in
    --backend)
        if [[ -f logs/backend.log ]]; then
            tail -f logs/backend.log
        else
            log_info "后端日志不存在：logs/backend.log"
            exit 1
        fi
        ;;
    --frontend)
        if [[ -f logs/frontend.log ]]; then
            tail -f logs/frontend.log
        else
            log_info "前端日志不存在：logs/frontend.log"
            exit 1
        fi
        ;;
    --docker)
        if command -v docker >/dev/null 2>&1; then
            docker compose logs -f --tail=200
        else
            log_info "docker 不可用"
            exit 1
        fi
        ;;
    "")
        if [[ -f logs/backend.log && -f logs/frontend.log ]]; then
            # 同时跟踪两个日志
            tail -f logs/backend.log logs/frontend.log
        elif [[ -f logs/backend.log ]]; then
            tail -f logs/backend.log
        elif [[ -f logs/frontend.log ]]; then
            tail -f logs/frontend.log
        else
            log_info "日志文件不存在，请先执行 ./scripts/start.sh"
            exit 1
        fi
        ;;
    *)
        echo "用法：$0 [--backend|--frontend|--docker]"
        exit 1
        ;;
esac
