# Api-Web Linux 部署说明

本目录提供 Linux 服务器部署所需的脚本与配置。解压安装包后按下面步骤操作即可。

## 一、环境要求

| 依赖 | 说明 |
|---|---|
| Node.js | ≥ 20（含 npm），用于后端与构建 |
| 网络 | 安装时需访问 npm 仓库（`npm ci` 下载依赖） |
| JMeter + JDK | 可选，仅「性能测试」模块需要（见第四节） |
| Chrome + chromedriver | 可选，仅「UI 自动化」模块需要 |

## 二、快速部署（一条命令）

```bash
# 1) 解压（假设安装包为 api-web-<版本>-linux.tar.gz）
tar -xzf api-web-<版本>-linux.tar.gz -C /opt
cd /opt/api-web

# 2) 一键安装（依赖 + 迁移 + 构建；AUTO_SEED=1 免交互写演示数据）
bash deploy/install.sh

# 3) 启动（后端 API 4000 + 前端 8080 一体，无需 Nginx）
bash deploy/start.sh
```

浏览器访问 `http://<服务器IP>:8080`，默认账号 `admin / admin@123`（若已写入演示数据）。

> 若提示 `bash: deploy/install.sh: Permission denied`，用 `bash deploy/install.sh` 方式即可（无需 chmod）。

## 三、生产部署（systemd + Nginx）

```bash
# 1) 后端以 systemd 常驻（按实际路径修改 deploy/api-web.service 中的 WorkingDirectory / JWT_SECRET）
sudo cp deploy/api-web.service /etc/systemd/system/api-web.service
sudo systemctl daemon-reload
sudo systemctl enable --now api-web

# 2) Nginx 托管前端 + 反代 /api、/mock
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/api-web.conf
#    按实际路径/域名修改 root 与 server_name，然后：
sudo nginx -t && sudo systemctl reload nginx
```

## 四、可选运行时（功能模块依赖）

- **性能测试（JMeter）**：下载 JMeter 5.x 解压到 `server/jmeter/`，确保存在 `server/jmeter/bin/ApacheJMeter.jar`；或设置环境变量 `JMETER_HOME`。本机已内置常用第三方插件（阶梯/目标并发线程组、图形监听器、PerfMon 采集器等）。缺失的 PerfMon 测量包 / MQTT / AMQP / Dubbo 插件可按需把 jar 放入 `server/jmeter/lib/ext/` 后重启。
- **UI 自动化（Chrome）**：安装 Chrome + 匹配版本的 chromedriver，放到 `server/bin/` 或 `CHROMEDRIVER_PATH`。

## 五、环境变量（后端）

在 `server/.env` 或 systemd 的 `Environment=` 中配置：

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` / `HOST` | `4000` / `0.0.0.0` | 后端监听端口/地址 |
| `JWT_SECRET` | `api-web-secret-change-me` | **生产务必修改** |
| `DATABASE_URL` | `file:./dev.db` | SQLite 路径（相对 server 目录） |
| `PERF_TIMEOUT_MS` | `3600000` | 单次压测最长时长 |
| `PERF_MAX_CONCURRENCY` | `2` | 同时允许的压测进程数 |

## 六、目录约定

- 数据库文件：`server/dev.db`（SQLite 单文件，备份即复制该文件）。
- 压测工作目录：`server/.perf-runs/`（原始 JTL + 官方 HTML 报告，可定期清理）。

## 七、常见问题

- **后端起不来 / 端口占用**：`PORT` 与 `FRONTEND_PORT` 不要和已有服务冲突。
- **前端能打开但接口 502**：`start.sh` 方式下确认后端进程存活（`server/dist/index.js`）；Nginx 方式确认 `proxy_pass http://127.0.0.1:4000` 与后端监听地址一致。
- **登录提示 token 失效**：多为 `JWT_SECRET` 在重启后变化导致，保持其稳定即可。
