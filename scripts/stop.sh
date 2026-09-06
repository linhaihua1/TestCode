#!/usr/bin/env bash
#
# Api-Web 一键停止脚本
# 用法：
#   ./scripts/stop.sh                停止前后端 + 中间件（docker compose down）
#   ./scripts/stop.sh --keep-db      停止前后端，但保留中间件容器
#   ./scripts/stop.sh --backend      只停后端
#   ./scripts/stop.sh --frontend     只停前端
#   ./scripts/stop.sh --middleware   只停中间件
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "\033[0;34m[INFO]\033[0m  $*"; }
log_ok()    { echo -e "${GREEN}[OK]\033[0m    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]\033[0m  $*"; }

stop_pid() {
    local pid_file="$1" name="$2"
    if [[ -f "${pid_file}" ]]; then
        local pid
        pid="$(cat "${pid_file}")"
        if kill -0 "${pid}" >/dev/null 2>&1; then
            log_info "停止 ${name}（PID ${pid}）..."
            # 优雅终止：先 SIGTERM，5s 后还活着就 SIGKILL
            kill -TERM "${pid}" >/dev/null 2>&1 || true
            for _ in $(seq 1 5); do
                if ! kill -0 "${pid}" >/dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            if kill -0 "${pid}" >/dev/null 2>&1; then
                log_warn "${name} 未响应 SIGTERM，强制 SIGKILL"
                kill -KILL "${pid}" >/dev/null 2>&1 || true
            fi
            log_ok "${name} 已停止"
        else
            log_warn "${name} 进程不存在（PID ${pid}）"
        fi
        rm -f "${pid_file}"
    else
        log_warn "${name} 未运行"
    fi
}

stop_backend() {
    # 先按 PID 文件停，再按端口兜底（兼容非脚本启动的实例）
    stop_pid logs/backend.pid "后端"
    # 兜底：通过端口杀掉占用的进程
    local pids
    pids="$(lsof -ti:"${BACKEND_PORT:-8080}" 2>/dev/null || true)"
    if [[ -n "${pids}" ]]; then
        log_warn "检测到端口 ${BACKEND_PORT:-8080} 仍被占用：${pids}，强制结束"
        kill -KILL ${pids} >/dev/null 2>&1 || true
    fi
}

stop_frontend() {
    stop_pid logs/frontend.pid "前端"
    local pids
    pids="$(lsof -ti:"${FRONTEND_PORT:-5173}" 2>/dev/null || true)"
    if [[ -n "${pids}" ]]; then
        log_warn "检测到端口 ${FRONTEND_PORT:-5173} 仍被占用：${pids}，强制结束"
        kill -KILL ${pids} >/dev/null 2>&1 || true
    fi
}

stop_middleware() {
    log_info "停止中间件容器..."
    if command -v docker >/dev/null 2>&1; then
        docker compose down
        log_ok "中间件容器已停止"
    else
        log_warn "docker 命令不可用，跳过中间件停止"
    fi
}

case "${1:-}" in
    --backend)
        stop_backend
        ;;
    --frontend)
        stop_frontend
        ;;
    --middleware)
        stop_middleware
        ;;
    --keep-db)
        stop_backend
        stop_frontend
        ;;
    "")
        stop_backend
        stop_frontend
        stop_middleware
        log_ok "Api-Web 全栈已停止"
        ;;
    *)
        echo "用法：$0 [--backend|--frontend|--middleware|--keep-db]"
        exit 1
        ;;
esac
