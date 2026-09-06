#!/usr/bin/env bash
#
# Api-Web 构建脚本
# 用途：分别构建后端 jar 与前端 dist
# 用法：
#   ./scripts/build.sh             构建前后端
#   ./scripts/build.sh --backend   只构建后端 jar
#   ./scripts/build.sh --frontend  只构建前端 dist
#   ./scripts/build.sh --clean     mvn clean + 删除前端 dist
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_err()   { echo -e "${RED}[ERROR]${NC} $*"; }

build_backend() {
    log_info "构建后端（Spring Boot 3 + Maven）..."
    if ! command -v mvn >/dev/null 2>&1; then
        log_err "未安装 Maven，请先安装 Maven 3.8+"
        exit 1
    fi
    if [[ ! -d server ]]; then
        log_err "找不到 server 目录"
        exit 1
    fi
    cd server
    mvn -B clean package -DskipTests
    local jar
    jar="$(ls target/api-web-server-*.jar 2>/dev/null | head -1 || true)"
    if [[ -z "${jar}" ]]; then
        log_err "未生成 server/target/api-web-server-*.jar"
        exit 1
    fi
    log_ok "后端构建完成：${jar}"
    cd "${PROJECT_ROOT}"
}

build_frontend() {
    log_info "构建前端（Vue 3 + Vite）..."
    if ! command -v node >/dev/null 2>&1; then
        log_err "未安装 Node.js，请先安装 Node.js 20+"
        exit 1
    fi
    if [[ ! -d client ]]; then
        log_err "找不到 client 目录"
        exit 1
    fi
    cd client
    if [[ ! -d node_modules ]]; then
        log_info "首次运行，安装前端依赖..."
        npm install
    fi
    npm run build
    if [[ ! -d dist ]]; then
        log_err "未生成 client/dist 目录"
        exit 1
    fi
    log_ok "前端构建完成：client/dist/"
    cd "${PROJECT_ROOT}"
}

clean_all() {
    log_info "清理构建产物..."
    if [[ -d server/target ]]; then
        rm -rf server/target
        log_ok "已删除 server/target"
    fi
    if [[ -d client/dist ]]; then
        rm -rf client/dist
        log_ok "已删除 client/dist"
    fi
    if [[ -d client/node_modules ]]; then
        log_ok "保留 client/node_modules（如需重装请手动 rm -rf）"
    fi
}

case "${1:-}" in
    --backend)
        build_backend
        ;;
    --frontend)
        build_frontend
        ;;
    --clean)
        clean_all
        ;;
    "")
        build_backend
        build_frontend
        log_ok "========== 全量构建完成 =========="
        log_ok "后端 jar：server/target/api-web-server-*.jar"
        log_ok "前端 dist：client/dist/"
        log_info "下一步：./scripts/start.sh --prod"
        ;;
    *)
        echo "用法：$0 [--backend|--frontend|--clean]"
        exit 1
        ;;
esac
