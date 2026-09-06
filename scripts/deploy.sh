#!/usr/bin/env bash
#
# Api-Web 生产环境一键部署脚本
# 适用场景：在干净的 Linux 服务器上从零部署整套服务
# 前置条件：
#   1. 服务器已安装 Docker 20+ / Docker Compose v2
#   2. 服务器已安装 Nginx（用于反向代理前端）
#   3. 已上传或 git clone 整个工程到目标目录
# 执行流程：
#   1. 构建后端 jar、前端静态资源
#   2. 打包成发布目录 deploy/api-web-release/
#   3. 生成 docker-compose.prod.yml（后端 + 中间件）
#   4. 生成 nginx 配置
#   5. 启动容器
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

# ============== 配置区（按需修改） ==============
APP_NAME="api-web"
APP_PORT="${APP_PORT:-8080}"           # 后端容器对外端口
DB_PORT="${DB_PORT:-3306}"
REDIS_PORT="${REDIS_PORT:-6379}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
MINIO_API_PORT="${MINIO_API_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
DOMAIN="${DOMAIN:-localhost}"           # 部署域名（Nginx 会用）
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-root123}"
DB_PASSWORD="${DB_PASSWORD:-apiweb123}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"   # 留空表示无密码
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-apiweb123}"
MINIO_PASSWORD="${MINIO_PASSWORD:-apiweb123}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"

RELEASE_DIR="${PROJECT_ROOT}/deploy/${APP_NAME}-release"

# ============== 步骤 1: 构建 ==============
log_info "========== 步骤 1/5: 构建前后端 =========="
if [[ ! -f server/target/api-web-server-*.jar ]]; then
    "${SCRIPT_DIR}/build.sh"
else
    log_info "检测到已有 server/target/*.jar，跳过后端构建"
fi

# ============== 步骤 2: 准备发布目录 ==============
log_info "========== 步骤 2/5: 准备发布目录 =========="
rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"/{backend,frontend,mysql/conf,mysql/init,redis,rabbitmq,minio,nginx,logs}

# 后端 jar
cp server/target/api-web-server-*.jar "${RELEASE_DIR}/backend/app.jar"

# 后端启动脚本（容器内）
cat >"${RELEASE_DIR}/backend/start.sh" <<'BACKEND_START'
#!/bin/sh
set -e
# JVM 参数：容器内存由 Docker --memory 限制，按比例分配堆
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m}"
exec java ${JAVA_OPTS} -jar /app/app.jar --spring.profiles.active=prod "$@"
BACKEND_START
chmod +x "${RELEASE_DIR}/backend/start.sh"

# 前端 dist
cp -r client/dist/* "${RELEASE_DIR}/frontend/"

# MySQL 配置：使用 utf8mb4 + UTC 时区
cat >"${RELEASE_DIR}/mysql/conf/my.cnf" <<'MYSQL_CONF'
[mysqld]
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
default-time-zone = '+00:00'
max_connections = 500
innodb_buffer_pool_size = 256M
log_bin_trust_function_creators = 1
[client]
default-character-set = utf8mb4
[mysql]
default-character-set = utf8mb4
MYSQL_CONF

# 数据库初始化脚本（容器首次启动时自动执行）
cp server/src/main/resources/db/schema.sql "${RELEASE_DIR}/mysql/init/01-schema.sql"

# Redis 配置（按需开启密码）
cat >"${RELEASE_DIR}/redis/redis.conf" <<REDIS_CONF
bind 0.0.0.0
port 6379
protected-mode yes
maxmemory 256mb
maxmemory-policy allkeys-lru
appendonly yes
REDIS_CONF
if [[ -n "${REDIS_PASSWORD}" ]]; then
    echo "requirepass ${REDIS_PASSWORD}" >>"${RELEASE_DIR}/redis/redis.conf"
fi

# Nginx 配置（前端 + 后端反向代理）
cat >"${RELEASE_DIR}/nginx/nginx.conf" <<NGINX_CONF
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;
events { worker_connections 1024; }
http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;
    sendfile      on;
    keepalive_timeout 65;
    client_max_body_size 50m;
    gzip on;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;
    # 前端静态资源
    server {
        listen 80;
        server_name ${DOMAIN};
        root /usr/share/nginx/html;
        index index.html;
        # SPA 路由：所有非 /api 路径回退到 index.html
        location / {
            try_files \$uri \$uri/ /index.html;
        }
        # 后端 API 反向代理
        location /api/ {
            proxy_pass         http://backend:${APP_PORT};
            proxy_set_header   Host              \$host;
            proxy_set_header   X-Real-IP         \$remote_addr;
            proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
            proxy_set_header   X-Forwarded-Proto \$scheme;
            proxy_read_timeout 300s;
            proxy_send_timeout 300s;
        }
        # WebSocket（如后续接入实时进度）
        location /ws/ {
            proxy_pass         http://backend:${APP_PORT};
            proxy_http_version 1.1;
            proxy_set_header   Upgrade           \$http_upgrade;
            proxy_set_header   Connection        "upgrade";
        }
    }
}
NGINX_CONF

# ============== 步骤 3: 生成 docker-compose ==============
log_info "========== 步骤 3/5: 生成 docker-compose.prod.yml =========="
cat >"${RELEASE_DIR}/docker-compose.yml" <<COMPOSE_EOF
version: "3.8"

services:
  mysql:
    image: mysql:8.0
    container_name: ${APP_NAME}-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: api_web
      MYSQL_USER: apiweb
      MYSQL_PASSWORD: ${DB_PASSWORD}
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-time-zone=+00:00
    ports:
      - "${DB_PORT}:3306"
    volumes:
      - ./mysql/conf/my.cnf:/etc/mysql/conf.d/my.cnf
      - ./mysql/init:/docker-entrypoint-initdb.d
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${DB_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: ${APP_NAME}-redis
    restart: unless-stopped
    command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
    ports:
      - "${REDIS_PORT}:6379"
    volumes:
      - ./redis/redis.conf:/usr/local/etc/redis/redis.conf
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.12-management
    container_name: ${APP_NAME}-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: apiweb
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    ports:
      - "${RABBITMQ_PORT}:5672"
      - "15672:15672"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 15s
      timeout: 10s
      retries: 5

  minio:
    image: minio/minio:RELEASE.2024-05-10T01-41-38Z
    container_name: ${APP_NAME}-minio
    restart: unless-stopped
    command: ["server", "/data", "--console-address", ":9001"]
    environment:
      MINIO_ROOT_USER: apiweb
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    ports:
      - "${MINIO_API_PORT}:9000"
      - "${MINIO_CONSOLE_PORT}:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 15s
      timeout: 5s
      retries: 5

  backend:
    image: eclipse-temurin:17-jre
    container_name: ${APP_NAME}-backend
    restart: unless-stopped
    working_dir: /app
    depends_on:
      mysql:     { condition: service_healthy }
      redis:     { condition: service_healthy }
      rabbitmq:  { condition: service_healthy }
      minio:     { condition: service_healthy }
    environment:
      TZ: UTC
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/api_web?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
      SPRING_DATASOURCE_USERNAME: apiweb
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: apiweb
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      APIWEB_MINIO_ENDPOINT: http://minio:9000
      APIWEB_MINIO_ACCESS_KEY: apiweb
      APIWEB_MINIO_SECRET_KEY: ${MINIO_PASSWORD}
      APIWEB_MINIO_BUCKET: api-web
      APIWEB_JWT_SECRET: ${JWT_SECRET}
      JAVA_OPTS: "-Xms256m -Xmx512m"
    ports:
      - "${APP_PORT}:${APP_PORT}"
    volumes:
      - ./backend:/app
    command: ["sh", "-c", "chmod +x /app/start.sh && /app/start.sh"]

  nginx:
    image: nginx:1.25-alpine
    container_name: ${APP_NAME}-nginx
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./frontend:/usr/share/nginx/html:ro

volumes:
  mysql_data:
  redis_data:
  rabbitmq_data:
  minio_data:
COMPOSE_EOF

# 部署说明
cat >"${RELEASE_DIR}/README.md" <<DEPLOY_README
# Api-Web 生产部署包

## 目录结构
\`\`\`
${APP_NAME}-release/
├── backend/          # 后端 jar + start.sh
├── frontend/         # 前端静态资源（被 Nginx 挂载）
├── mysql/            # MySQL 配置 + 初始化 SQL
├── redis/            # Redis 配置
├── nginx/            # Nginx 配置（前端 + API 反向代理）
├── logs/             # 日志（需创建）
└── docker-compose.yml
\`\`\`

## 启动
\`\`\`bash
cd ${APP_NAME}-release
docker compose up -d
\`\`\`

## 访问
- 前端：http://${DOMAIN}
- 后端 API：http://${DOMAIN}/api
- RabbitMQ 管理：http://${DOMAIN}:15672（apiweb / ${RABBITMQ_PASSWORD}）
- MinIO 控制台：http://${DOMAIN}:9001（apiweb / ${MINIO_PASSWORD}）

## 升级
\`\`\`bash
# 1. 替换 backend/app.jar
# 2. 替换 frontend/*
# 3. 滚动重启
docker compose restart backend nginx
\`\`\`

## 备份
\`\`\`bash
# 数据库
docker exec ${APP_NAME}-mysql mysqldump -uroot -p${DB_ROOT_PASSWORD} api_web > backup_\$(date +%Y%m%d).sql
# MinIO 数据
docker exec ${APP_NAME}-minio mc mirror /data /backup
\`\`\`
DEPLOY_README

log_ok "发布包已生成：${RELEASE_DIR}"

# ============== 步骤 4: 询问是否启动 ==============
log_info "========== 步骤 4/5: 部署 =========="
echo ""
echo "发布包已就绪，下一步："
echo "  cd ${RELEASE_DIR}"
echo "  docker compose up -d"
echo ""
read -p "是否立即在本机启动？(y/N) " ans
if [[ "${ans}" == "y" || "${ans}" == "Y" ]]; then
    cd "${RELEASE_DIR}"
    docker compose up -d
    log_ok "容器已启动"
    log_info "等待后端就绪（约 60s）..."
    sleep 60
    log_ok "========== 部署完成 =========="
    log_ok "前端：http://${DOMAIN}"
    log_ok "默认账号：admin / admin@123"
    log_ok "查看日志：cd ${RELEASE_DIR} && docker compose logs -f"
fi
