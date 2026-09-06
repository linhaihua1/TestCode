#!/usr/bin/env bash
#
# Api-Web 一键启动脚本（Linux/macOS）
# 作用：拉起 MySQL / Redis / RabbitMQ / MinIO + 后端 Spring Boot + 前端开发服务器
# 用法：
#   ./scripts/start.sh                  默认开发模式（前后端分离 + 中间件用 docker）
#   ./scripts/start.sh --middleware     仅启动中间件（4 个容器）
#   ./scripts/start.sh --backend        仅启动后端（依赖已有中间件）
#   ./scripts/start.sh --frontend       仅启动前端开发服务器
#   ./scripts/start.sh --prod           生产模式（构建前端静态资源 + 后端 jar + docker）
#
set -euo pipefail

# ============== 路径与配置 ==============
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# 端口默认值（与 docker-compose.yml / application.yml 保持一致）
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_PORT="${REDIS_PORT:-6379}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
RABBITMQ_MGMT_PORT="${RABBITMQ_MGMT_PORT:-15672}"
MINIO_API_PORT="${MINIO_API_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

# 颜色输出
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ============== 工具函数 ==============

# 检查必需命令
require_cmd() {
    for cmd in "$@"; do
        if ! command -v "${cmd}" >/dev/null 2>&1; then
            log_err "缺少必需命令：${cmd}"
            return 1
        fi
    done
}

# 端口可用性检查
wait_port() {
    local host="$1" port="$2" timeout="${3:-60}"
    log_info "等待 ${host}:${port} 就绪（最长 ${timeout}s）..."
    for i in $(seq 1 "${timeout}"); do
        if (echo >/dev/tcp/"${host}"/"${port}") >/dev/null 2>&1; then
            log_ok "${host}:${port} 已就绪"
            return 0
        fi
        sleep 1
    done
    log_err "${host}:${port} 在 ${timeout}s 内未就绪"
    return 1
}

# 检测 Java / Maven / Node
detect_env() {
    log_info "检测本机环境..."
    command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 | sed 's/^/  Java: /' || log_warn "未安装 Java（生产模式后端需要）"
    command -v mvn  >/dev/null 2>&1 && mvn  -version 2>&1 | head -1 | sed 's/^/  Maven: /' || log_warn "未安装 Maven（开发模式后端需要）"
    command -v node >/dev/null 2>&1 && node --version | sed 's/^/  Node: /' || log_warn "未安装 Node（前端需要）"
    command -v docker >/dev/null 2>&1 && docker --version | sed 's/^/  Docker: /' || log_warn "未安装 Docker（中间件需要）"
    command -v docker compose >/dev/null 2>&1 && echo "  Docker Compose: $(docker compose version --short 2>/dev/null)" || true
}

# ============== 中间件启动 ==============

start_middleware() {
    log_info "启动中间件容器（MySQL / Redis / RabbitMQ / MinIO）..."
    require_cmd docker || return 1
    docker compose up -d mysql redis rabbitmq minio
    log_ok "中间件容器已启动"
    wait_port localhost "${MYSQL_PORT}" 90
    wait_port localhost "${REDIS_PORT}" 30
    wait_port localhost "${RABBITMQ_PORT}" 60
    wait_port localhost "${MINIO_API_PORT}" 30
}

# ============== 数据库初始化 ==============

init_database() {
    log_info "初始化 MySQL 数据库与表结构..."
    require_cmd docker || return 1
    # 等待 MySQL 完全就绪（不仅端口，还要能执行 SQL）
    local retries=30
    until docker exec apiweb-mysql mysql -uapiweb -papiweb123 -e "SELECT 1" >/dev/null 2>&1; do
        retries=$((retries - 1))
        if [[ "${retries}" -le 0 ]]; then
            log_err "MySQL 在 30s 内未就绪"
            return 1
        fi
        sleep 1
    done
    # 导入 schema.sql（首次创建，重复执行需先 DROP）
    docker exec -i apiweb-mysql mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql
    log_ok "数据库初始化完成"
}

# ============== 后端启动 ==============

start_backend_dev() {
    log_info "启动后端（开发模式：mvn spring-boot:run）..."
    require_cmd java mvn || return 1
    if [[ ! -d server ]]; then
        log_err "找不到 server 目录"
        return 1
    fi
    cd server
    nohup mvn spring-boot:run >"${PROJECT_ROOT}/logs/backend.log" 2>&1 &
    echo $! >"${PROJECT_ROOT}/logs/backend.pid"
    cd "${PROJECT_ROOT}"
    wait_port localhost "${BACKEND_PORT}" 120
    log_ok "后端已启动（PID $(cat logs/backend.pid)）"
}

start_backend_prod() {
    log_info "启动后端（生产模式：jar）..."
    require_cmd java || return 1
    local jar
    jar="$(ls server/target/api-web-server-*.jar 2>/dev/null | head -1 || true)"
    if [[ -z "${jar}" ]]; then
        log_err "找不到 server/target/api-web-server-*.jar，请先执行 ./scripts/build.sh"
        return 1
    fi
    mkdir -p logs
    nohup java -jar "${jar}" --spring.profiles.active=prod >"${PROJECT_ROOT}/logs/backend.log" 2>&1 &
    echo $! >"${PROJECT_ROOT}/logs/backend.pid"
    wait_port localhost "${BACKEND_PORT}" 60
    log_ok "后端 jar 已启动（PID $(cat logs/backend.pid)）"
}

# ============== 前端启动 ==============

start_frontend() {
    log_info "启动前端（开发服务器）..."
    require_cmd node || return 1
    if [[ ! -d client ]]; then
        log_err "找不到 client 目录"
        return 1
    fi
    cd client
    if [[ ! -d node_modules ]]; then
        log_info "首次运行，安装前端依赖..."
        npm install
    fi
    nohup npm run dev >"${PROJECT_ROOT}/logs/frontend.log" 2>&1 &
    echo $! >"${PROJECT_ROOT}/logs/frontend.pid"
    cd "${PROJECT_ROOT}"
    wait_port localhost "${FRONTEND_PORT}" 60
    log_ok "前端已启动（PID $(cat logs/frontend.pid)）"
}

# ============== 主流程 ==============

mkdir -p logs

case "${1:-}" in
    --middleware)
        start_middleware
        ;;
    --backend)
        start_backend_dev
        ;;
    --frontend)
        start_frontend
        ;;
    --prod)
        start_middleware
        init_database
        start_backend_prod
        log_info "前端请自行用 Nginx 部署 client/dist/ 目录"
        ;;
    "")
        detect_env
        start_middleware
        init_database
        start_backend_dev
        start_frontend
        log_ok "========== Api-Web 启动完成 =========="
        log_ok "前端地址：http://localhost:${FRONTEND_PORT}"
        log_ok "后端地址：http://localhost:${BACKEND_PORT}"
        log_ok "RabbitMQ 管理：http://localhost:${RABBITMQ_MGMT_PORT}（apiweb / apiweb123）"
        log_ok "MinIO 控制台：http://localhost:${MINIO_CONSOLE_PORT}（apiweb / apiweb123）"
        log_ok "默认账号：admin / admin@123"
        log_ok "停止服务：./scripts/stop.sh"
        log_ok "查看日志：tail -f logs/backend.log logs/frontend.log"
        ;;
    *)
        log_err "未知参数：${1}"
        echo "用法：$0 [--middleware|--backend|--frontend|--prod]"
        exit 1
        ;;
esac
