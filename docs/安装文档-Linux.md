# API 自动化测试平台 · Linux 安装文档

> 本文档专门说明在 **Linux 服务器**上的安装与部署（Ubuntu/Debian 为例，CentOS 命令略有差异）。
> 本地开发（Windows）环境请查看《安装文档.md》。

平台结构：前端 React 静态站点 + 后端 Node.js（Fastify + Prisma + SQLite），数据库为单文件 SQLite，无需额外数据库服务。

---

## 一、环境要求

| 依赖 | 版本/说明 | 是否必需 |
|---|---|---|
| Node.js | ≥ 20（含 npm） | 必需 |
| 网络 | 安装时访问 npm 仓库下载依赖 | 必需 |
| JDK + Apache JMeter | JDK 8+、JMeter 5.x | 仅「性能测试」模块 |
| Chrome + chromedriver | 版本需匹配 | 仅「UI 自动化」模块 |

---

## 二、快速开始：安装包一键部署（推荐）

### 2.1 上传并解压

将安装包 `api-web-<版本>-linux.tar.gz` 上传到服务器后：

```bash
tar -xzf api-web-<版本>-linux.tar.gz -C /opt
cd /opt/api-web
```

### 2.2 一键安装并启动

```bash
bash deploy/start.sh
```

`start.sh` 已做到「一键」：**首次运行**自动完成 Node 检查 → 安装依赖（`npm ci`）→ 生成 Prisma Client → 初始化数据库 → 构建前后端 → 启动；**再次运行**检测到已安装则直接秒启动。

启动后：

- 后端 API：`http://<服务器IP>:4000/api`
- 前端界面：`http://<服务器IP>:8080`（内置静态服务器 + `/api`、`/mock` 反向代理，无需 Nginx）

### 2.3 常用参数

```bash
AUTO_SEED=1 bash deploy/start.sh           # 安装时免交互写入演示数据（admin/admin@123）
bash deploy/start.sh --install             # 强制重装（重新 npm ci + 构建）
bash deploy/start.sh --install-only        # 只安装、不启动（等价 bash deploy/install.sh）
bash deploy/stop.sh                        # 停止由 start.sh 拉起的进程
```

### 2.4 验证

```bash
curl http://127.0.0.1:4000/api/health
# 应返回：{"status":"ok","service":"api-web-server"}
```

浏览器打开 `http://<服务器IP>:8080`，默认账号 `admin / admin@123`（需已写入演示数据）。

> 防火墙放行：`sudo ufw allow 8080/tcp`（或按需放行 4000/80）。

---

## 三、生产部署：systemd + Nginx（推荐生产环境）

快速方式（第二节）适合验证与轻量使用；生产环境建议用 systemd 常驻后端、Nginx 托管前端并做反向代理。

### 3.1 部署架构

```
互联网 → Nginx(80/443) → 前端静态 client/dist + 反代 /api、/mock
                              │
                        Node.js 后端(4000, systemd)
                              │
                        SQLite 文件 server/dev.db
```

### 3.2 后端以 systemd 常驻

```bash
sudo cp deploy/api-web.service /etc/systemd/system/api-web.service
# 按实际路径修改该文件中的 WorkingDirectory 与 JWT_SECRET
sudo systemctl daemon-reload
sudo systemctl enable --now api-web
sudo systemctl status api-web
```

`deploy/api-web.service` 要点（可按需调整）：

```ini
[Service]
WorkingDirectory=/opt/api-web
ExecStart=/usr/bin/node server/dist/index.js
Environment=PORT=4000
Environment=HOST=127.0.0.1        # 后端不直接暴露公网，仅由 Nginx 代理
Environment=JWT_SECRET=改成随机长字符串
Restart=always
```

### 3.3 前端以 Nginx 托管

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/api-web.conf
# 按实际路径/域名修改 root 与 server_name
sudo nginx -t && sudo systemctl reload nginx
```

配置要点：SPA 路由回退 + `/api`、`/mock` 反代 + 压测接口长超时（见 `deploy/nginx.conf.example`）。

### 3.4 配置 HTTPS（可选）

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

---

## 四、手动源码部署（无安装包时）

```bash
# 1) 安装 Node 20（nvm 方式）
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 20 && nvm use 20 && nvm alias default 20

# 2) 获取代码
cd /opt && git clone <仓库地址> api-web && cd api-web

# 3) 安装依赖
npm install

# 4) 配置环境变量（可选，见第五节）
#    cat > server/.env << 'EOF'
#    PORT=4000
#    HOST=127.0.0.1
#    JWT_SECRET=改成随机长字符串
#    EOF

# 5) 初始化数据库
cd server && npx prisma generate && npx prisma migrate deploy && cd ..
npm run seed -w @api-web/server        # 可选：写演示数据

# 6) 构建
npm run build

# 7) 启动（二选一）
node server/dist/index.js              # 前台运行
# 或按第三节用 systemd / Nginx 常驻
```

---

## 五、环境变量说明

在 `server/.env` 或 systemd 的 `Environment=` 中配置：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PORT` / `HOST` | `4000` / `0.0.0.0` | 后端监听端口/地址；生产建议 `HOST=127.0.0.1` |
| `JWT_SECRET` | `api-web-secret-change-me` | JWT 签名密钥，**生产务必修改**且保持不变 |
| `DATABASE_URL` | `file:./dev.db` | SQLite 路径（相对 `server` 目录，即 `server/dev.db`） |
| `CHROMEDRIVER_PATH` | 空 | chromedriver 路径（UI 自动化用） |
| `JMETER_HOME` | 空 | JMeter 安装目录；留空优先用内置 `server/jmeter/` |
| `JAVA_HOME` | 空 | JDK 目录；留空用 PATH 上的 `java` |
| `PERF_TIMEOUT_MS` | `3600000` | 单次压测最长时长（毫秒） |
| `PERF_MAX_CONCURRENCY` | `2` | 同时允许的压测进程数 |

---

## 六、可选运行时依赖

### 6.1 性能测试（JMeter + JDK）

```bash
sudo apt-get install -y openjdk-11-jre-headless
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.2.1.tgz
tar -xzf apache-jmeter-5.2.1.tgz
mkdir -p server/jmeter
cp -r apache-jmeter-5.2.1/bin apache-jmeter-5.2.1/lib server/jmeter/
# 校验：server/jmeter/bin/ApacheJMeter.jar 存在即就绪
# 也可不内置，改为设置 JMETER_HOME=/opt/apache-jmeter-5.2.1
```

- 运行时查找优先级：`server/jmeter/`（内置）→ `JMETER_HOME` → `PATH` 上的 `jmeter`。
- 内置发行包已含常用第三方插件（阶梯/目标并发线程组、图形监听器、PerfMon 采集器等），可用 `GET /api/perf-plugins` 查看状态；缺失的 MQTT/AMQP/Dubbo 插件按需把 jar 放入 `server/jmeter/lib/ext/` 后重启后端。
- 配置后重启后端，`/api/perf-env` 的 `available` 应为 `true`。

### 6.2 UI 自动化（Chrome + chromedriver）

1. 安装 Chrome 浏览器；
2. 下载与 Chrome 版本匹配的 chromedriver，放到 `server/bin/` 或通过 `CHROMEDRIVER_PATH` 指定，或加入 `PATH`。

> 不使用对应模块则无需配置，各模块相互独立。

---

## 七、数据备份与迁移

数据库是单个 SQLite 文件 `server/dev.db`，备份即复制该文件：

```bash
# 备份（建议先停后端保证一致性）
cp /opt/api-web/server/dev.db /backup/api-web-$(date +%F).db

# 迁移到新机器：复制整个项目目录 + server/dev.db，重新执行「安装 + 启动」即可
```

压测产生的临时数据在 `server/.perf-runs/`（原始 JTL + 官方 HTML 报告），可定期清理。

---

## 八、更新与回滚

```bash
cd /opt/api-web
# 方式一：安装包升级 → 解压新包覆盖后重新 bash deploy/start.sh --install
# 方式二：Git 仓库升级
git pull
npm install
( cd server && npx prisma migrate deploy )
npm run build
sudo systemctl restart api-web     # systemd 方式
sudo systemctl reload nginx        # 静态文件已替换（可选）
```

回滚：切回上一个提交后重新构建、重启即可（SQLite 数据不受影响，除非迁移有破坏性变更）。

---

## 九、常见问题 FAQ

**Q1：后端 health 正常，但通过域名访问 /api 返回 502？**
后端进程未启动或 systemd 崩溃。检查 `systemctl status api-web` 与 `journalctl -u api-web`。

**Q2：前端页面空白 / 刷新后 404？**
Nginx 未配置 SPA 回退 `try_files $uri $uri/ /index.html`（见 `deploy/nginx.conf.example`）。

**Q3：`bash deploy/start.sh` 提示 Permission denied？**
用 `bash deploy/start.sh` 方式运行即可（无需执行权限）；或 `chmod +x deploy/*.sh`。

**Q4：安装时 npm ci 报错 / 依赖下载失败？**
确认服务器能访问 npm 仓库（必要时配置镜像：`npm config set registry https://registry.npmmirror.com`）。

**Q5：登录报错或 token 失效？**
`JWT_SECRET` 被修改会导致旧 token 全部失效，需重新登录；部署时设定一次后保持不变。

**Q6：性能测试提示「未找到 JMeter 运行时」？**
按 6.1 准备运行时并重启后端；用 `GET /api/perf-env` 查看后端探测到的 `source` 与 `home`。

**Q7：UI 自动化启动 Chrome 失败？**
核对 Chrome 与 chromedriver 版本匹配、chromedriver 在 `PATH` 或 `CHROMEDRIVER_PATH` 已指定，且运行环境有图形化所需的依赖（服务器无头环境可用 headless chrome）。

**Q8：如何修改后端端口？**
改 `server/.env` 的 `PORT`（或 systemd 的 `Environment=PORT`），并同步改 Nginx `proxy_pass` 端口。
