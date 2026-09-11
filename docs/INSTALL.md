# Api-Web 安装文档

> 本文档面向 **运维 / 部署人员**，介绍 Api-Web 在不同环境下的安装与启动方式。
> 阅读对象需要具备基本的 Linux / Docker 命令行经验。

---

## 目录

1. [系统要求](#1-系统要求)
2. [快速开始（Docker Compose，推荐）](#2-快速开始docker-compose推荐)
3. [手动安装](#3-手动安装)
4. [生产环境部署](#4-生产环境部署)
5. [常见问题排查](#5-常见问题排查)

---

## 1. 系统要求

### 1.1 最低配置

| 组件 | 最低 | 推荐 |
|---|---|---|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 硬盘 | 20 GB | 50 GB+ |
| 操作系统 | Linux x86_64（Ubuntu 20.04+ / CentOS 7+ / Debian 11+）/ macOS 12+ / Windows 10+（含 WSL2） |

### 1.2 依赖软件

| 软件 | 版本 | 用途 |
|---|---|---|
| Java | 17+ | 运行后端 |
| Maven | 3.8+ | 构建后端 |
| Node.js | 20+ | 构建/运行前端 |
| Docker | 20.10+ | 启动中间件（开发）/ 部署整套（生产） |
| Docker Compose | v2 | 容器编排 |
| Git | 2.20+ | 拉取代码 |
| OpenSSL | - | 生成 JWT 密钥 |

> 嵌入式 Apache JMeter 5.6.3 已作为 Maven 依赖打入 jar，**无需单独安装 JMeter**。

### 1.3 端口规划

| 端口 | 服务 | 是否必须 |
|---|---|---|
| 3306 | MySQL 8.0 | ✅ |
| 6379 | Redis 7 | ✅ |
| 5672 | RabbitMQ 3.12（AMQP） | ✅ |
| 15672 | RabbitMQ 管理界面 | 可选 |
| 9000 | MinIO API | ✅ |
| 9001 | MinIO 控制台 | 可选 |
| 8080 | Api-Web 后端（Spring Boot） | ✅ |
| 5173 | 前端开发服务器（Vite） | 仅开发 |
| 80 | Nginx（生产前端入口） | 生产 |

---

## 2. 快速开始（Docker Compose，推荐）

适合开发、测试、PoC 环境，**单台机器 5 分钟拉起**。

### 2.1 拉取代码

```bash
git clone <repo-url> api-web
cd api-web
```

### 2.2 一键启动

```bash
./scripts/start.sh
```

脚本会自动完成：
1. 启动 MySQL / Redis / RabbitMQ / MinIO 容器
2. 等待端口就绪（最长 90s）
3. 初始化数据库（导入 `server/src/main/resources/db/schema.sql`）
4. 启动 Spring Boot 后端（`mvn spring-boot:run`，日志 `logs/backend.log`）
5. 启动 Vite 前端开发服务器（`npm run dev`，日志 `logs/frontend.log`）

启动完成后访问：

| 入口 | URL | 账号 |
|---|---|---|
| 前端 | http://localhost:5173 | admin / admin@123 |
| 后端 API | http://localhost:8080/api | 同上 |
| RabbitMQ 管理 | http://localhost:15672 | apiweb / apiweb123 |
| MinIO 控制台 | http://localhost:9001 | apiweb / apiweb123 |

### 2.3 分阶段启动

```bash
./scripts/start.sh --middleware   # 仅启动 4 个中间件容器
./scripts/start.sh --backend      # 仅启动 Spring Boot（需中间件已起）
./scripts/start.sh --frontend     # 仅启动 Vite 开发服务器
./scripts/start.sh --prod         # 生产模式（启动中间件 + 跑 jar）
```

### 2.4 停止 / 查看日志 / 重置数据

```bash
./scripts/stop.sh              # 停止前后端 + 关闭中间件容器
./scripts/stop.sh --keep-db    # 停止前后端，但保留 MySQL 数据
./scripts/stop.sh --backend    # 只停后端

./scripts/logs.sh              # 实时跟踪所有日志
./scripts/logs.sh --backend    # 只看后端
./scripts/logs.sh --docker     # 看容器日志

./scripts/reset-db.sh          # 危险：删除并重建数据库（仅开发用）

### 2.5 本地无 Docker 快速启动（localdev 单机调试）

若本机已装 **MySQL 8.0 + Redis**，但没有 Docker / RabbitMQ / MinIO，可用 `localdev` profile 快速拉起后端（仅依赖 MySQL + Redis）：

```bash
# 1. 确保 MySQL(3306)、Redis(6379) 已启动，并已初始化 api_web 库
# 2. 打包并启动后端（localdev 禁用 RabbitMQ 消费端 / Quartz 集群 / MinIO）
cd server
mvn -DskipTests package
java -jar target/api-web-server-*.jar --spring.profiles.active=localdev

# 3. 启动前端
cd ../client
npm install && npm run dev
```

说明：

- 旧库升级时执行幂等迁移：`mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/migrate.sql`
- 对象存储（MinIO）在 localdev 下自动降级为本地文件系统（`C:\dev\apiweb-oss` 或 `/tmp/apiweb-oss`）
- 登录账号：`admin / admin@123`
- localdev 下 `apiweb.mq.enabled=false`，RabbitMQ 消费者不注册；UI / 性能任务改由进程内 `@Async` 直接执行，仍可正常生成报告

---

## 3. 手动安装

如果不想用 Docker（例如公司内网无法访问 Docker Hub），可以分别安装每个组件。

### 3.1 安装 MySQL 8.0

```bash
# Ubuntu
sudo apt install mysql-server-8.0
sudo mysql_secure_installation

# 创建数据库与账号
mysql -uroot -p
> CREATE DATABASE api_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
> CREATE USER 'apiweb'@'%' IDENTIFIED BY 'apiweb123';
> GRANT ALL ON api_web.* TO 'apiweb'@'%';
> FLUSH PRIVILEGES;

# 设置时区为 UTC
mysql -uroot -p -e "SET GLOBAL time_zone = '+00:00';"

# 导入 schema
mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql
```

### 3.2 安装 Redis 7

```bash
# Ubuntu
sudo apt install redis-server
sudo systemctl enable redis-server
sudo systemctl start redis-server

# 验证
redis-cli ping    # PONG
```

### 3.3 安装 RabbitMQ 3.12

```bash
# Ubuntu（添加官方源）
sudo apt install rabbitmq-server
sudo rabbitmq-plugins enable rabbitmq_management
sudo systemctl restart rabbitmq-server

# 创建账号
sudo rabbitmqctl add_user apiweb apiweb123
sudo rabbitmqctl set_user_tags apiweb administrator
sudo rabbitmqctl set_permissions -p / apiweb ".*" ".*" ".*"
```

访问 http://localhost:15672 验证。

### 3.4 安装 MinIO

```bash
# 下载二进制
wget https://dl.min.io/server/minio/release/linux-amd64/minio -O /usr/local/bin/minio
chmod +x /usr/local/bin/minio

# 创建数据目录
sudo mkdir -p /var/lib/minio
sudo useradd -r minio-user
sudo chown -R minio-user /var/lib/minio

# 创建 systemd 服务
sudo tee /etc/systemd/system/minio.service <<'EOF'
[Unit]
Description=MinIO
After=network.target

[Service]
User=minio-user
Group=minio-user
Environment="MINIO_ROOT_USER=apiweb"
Environment="MINIO_ROOT_PASSWORD=apiweb123"
ExecStart=/usr/local/bin/minio server /var/lib/minio --console-address :9001
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now minio

# 创建 bucket
mc alias set local http://localhost:9000 apiweb apiweb123
mc mb local/api-web
```

### 3.5 启动后端

```bash
cd server
mvn clean package -DskipTests
java -jar target/api-web-server-*.jar
```

### 3.6 启动前端

```bash
cd client
npm install
npm run dev
```

---

## 4. 生产环境部署

### 4.1 推荐架构

```
                      ┌────────────────┐
                      │   Nginx (80)   │  前端静态资源 + /api 反向代理
                      └───────┬────────┘
                              │
              ┌───────────────┴────────────────┐
              │                                │
       前端静态文件                       /api/* 反向代理
              │                                │
              ▼                                ▼
       /usr/share/nginx/html         ┌──────────────────┐
                                     │ Spring Boot jar  │  (8080)
                                     └────────┬─────────┘
                                              │
                ┌─────────────┬───────────────┼───────────────┐
                ▼             ▼               ▼               ▼
            MySQL 8.0      Redis 7       RabbitMQ 3.12     MinIO
            (3306)         (6379)        (5672/15672)      (9000/9001)
```

### 4.2 一键部署

```bash
./scripts/deploy.sh
```

脚本会：
1. 构建后端 jar 和前端 dist
2. 在 `deploy/api-web-release/` 生成完整发布包（含 docker-compose.prod.yml、Nginx 配置、MySQL 初始化 SQL）
3. 询问是否立即启动容器

### 4.3 自定义环境变量

部署前可以覆盖以下变量（示例）：

```bash
export DOMAIN="api.example.com"
export DB_PASSWORD="strong_passw0rd!"
export REDIS_PASSWORD="redis_passw0rd!"
export RABBITMQ_PASSWORD="rabbit_passw0rd!"
export MINIO_PASSWORD="minio_passw0rd!"
export JWT_SECRET="$(openssl rand -base64 48)"
export APP_PORT=8080

./scripts/deploy.sh
```

### 4.4 启动 / 升级 / 备份

```bash
cd deploy/api-web-release

# 启动
docker compose up -d

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend

# 升级（替换 jar 与前端资源后滚动重启）
cp ../../server/target/api-web-server-*.jar backend/app.jar
cp -r ../../client/dist/* frontend/
docker compose restart backend nginx

# 备份数据库
docker exec apiweb-mysql mysqldump -uroot -p$DB_ROOT_PASSWORD api_web > backup_$(date +%Y%m%d).sql

# 备份 MinIO 数据
docker exec apiweb-minio mc mirror /data /backup
```

### 4.5 HTTPS 配置

部署完成后建议立即启用 HTTPS（Let's Encrypt）：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.example.com
```

Certbot 会自动修改 Nginx 配置并配置证书自动续期。

---

## 5. 常见问题排查

### Q1: MySQL 容器启动失败，提示 "table doesn't exist"

A: 首次启动时 `mysql_data` 卷是空的，schema 才会自动导入。如果之前初始化过但中途出错，卷里有不完整的数据。删除卷重建：

```bash
docker compose down
docker volume rm api-web_mysql_data
docker compose up -d
```

### Q2: 后端启动失败，提示 "Communications link failure"

A: MySQL 还在初始化。等待 30s 后重试，或在 `application.yml` 增加连接超时：

```yaml
spring.datasource.hikari.connection-timeout: 60000
```

### Q3: RabbitMQ 队列消息堆积

A: 检查消费者是否正常启动：

```bash
docker compose logs backend | grep "started"
# 应看到：Started ApiWebApplication in X.XXX seconds
```

### Q4: MinIO 上传 1MB 以上文件失败

A: 默认桶策略可能不允许上传。执行：

```bash
mc anonymous set download local/api-web   # 公共读
# 或通过控制台 http://localhost:9001 设置 Access Policy
```

### Q5: 前端访问接口 404

A: 检查 Nginx 配置中 `/api/` 反向代理是否指向 `http://backend:8080`（容器内名，不是 `localhost`）。

### Q6: 时间字段差 8 小时

A: 数据库连接串必须带 `serverTimezone=UTC`：

```yaml
spring.datasource.url: jdbc:mysql://localhost:3306/api_web?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8mb4
```

### Q7: Windows 下 `mvn spring-boot:run` 报端口占用

A: 检查 8080 端口是否被其他进程占用，或在 `application.yml` 中修改 `server.port`。

### Q8: UI 自动化执行失败，提示 Chrome / ChromeDriver 版本不匹配

A: ChromeDriver 版本必须与执行机安装的 Chrome 浏览器版本一致。若不一致，下载匹配版本的 chromedriver 后通过环境变量指定：

```bash
# Linux / macOS
export WEBDRIVER_CHROME_DRIVER=/path/to/chromedriver
# Windows (cmd)
set WEBDRIVER_CHROME_DRIVER=C:\path\to\chromedriver.exe
```

后端启动后，UI 用例执行将优先使用该路径的驱动。

---

## 附：获取帮助

- GitHub Issues：<repo-url>/issues
- 内网问题联系：<联系邮箱>
- 紧急情况：执行 `./scripts/stop.sh && ./scripts/start.sh` 重启整套服务
