#!/usr/bin/env bash
#
# Api-Web 数据库重置脚本（仅开发环境使用）
# 警告：会删除全部数据！仅在本地开发时使用！
# 用法：
#   ./scripts/reset-db.sh           删除并重建数据库
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_err()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }

echo ""
log_warn "============================================="
log_warn "警告：此操作将删除 api_web 库的全部数据！"
log_warn "============================================="
echo ""
read -p "确认继续吗？(yes/no) " ans
if [[ "${ans}" != "yes" ]]; then
    log_info "已取消"
    exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
    log_err "需要 docker 才能执行此操作"
    exit 1
fi

# 等待 MySQL 就绪
log_info "等待 MySQL 容器就绪..."
for i in $(seq 1 30); do
    if docker exec apiweb-mysql mysql -uapiweb -papiweb123 -e "SELECT 1" >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

log_info "删除 api_web 库..."
docker exec apiweb-mysql mysql -uapiweb -papiweb123 -e "DROP DATABASE IF EXISTS api_web; CREATE DATABASE api_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

log_info "重新导入 schema.sql..."
docker exec -i apiweb-mysql mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql

log_ok "数据库重置完成"
log_info "默认账号：admin / admin@123"
