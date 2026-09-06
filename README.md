# Api-Web 自动化测试平台

一个参考 MeterSphere 的**接口自动化 + UI 自动化 + 性能测试**一体化测试平台。前后端分离架构，开箱即用。

> 本仓库为 Vue 3 + Spring Boot 3 全栈重构版本。前一版（React + Fastify + SQLite）已归档至 `backup/pre-vue-java` 分支。

## ✨ 功能特性

| 模块 | 能力 |
|---|---|
| **接口自动化** | 三栏工作台（用例库 / 编写用例 / 接口管理）、多步骤用例（HTTP / IF / FOR / WHILE / SCRIPT）、断言 / 提取 / 变量、Swagger / Excel 导入、Mock、测试任务执行与报告、版本快照与评审流 |
| **UI 自动化** | 基于 Selenium 驱动真实 Chrome、步骤编排（8 种定位方式）、失败自动截图、执行计划与报告 |
| **性能测试（嵌入式 JMeter）** | 结构化用例编辑、固定/阶梯加压线程组、`.jmx` 导入导出、异步执行、实时进度、P90/P95/P99/TPS 等多维报告 |
| **权限与审计** | 管理员 / 成员 / 查看者三角色 RBAC，操作审计日志 |
| **执行机资源池** | 执行机通过心跳注册到资源池，按能力（api/ui/perf）自动派发 |

## 🧰 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 · Vite · TypeScript · Ant Design Vue 4 · Pinia · vue-router · vue-draggable-plus · Monaco Editor · ECharts |
| 后端 | Java 17 · Spring Boot 3 · MyBatis-Plus |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7.x（登录会话、执行机心跳、分布式锁） |
| 消息队列 | RabbitMQ 3.12（任务异步投递） |
| 调度 | Quartz Scheduler（集群模式，定时测试任务） |
| 对象存储 | MinIO / OSS（>1MB 的请求/响应体转存） |

## 🚀 本地开发

### 0. 前置依赖

- Node.js 20+
- Java 17+
- Maven 3.8+
- Docker / Docker Compose（用于拉起中间件）

### 1. 启动中间件（MySQL / Redis / RabbitMQ / MinIO）

```bash
docker compose up -d
```

启动后：

| 服务 | 地址 | 默认账号 |
|---|---|---|
| MySQL | `localhost:3306` | `apiweb / apiweb123`（数据库 `api_web`） |
| Redis | `localhost:6379` | 无密码 |
| RabbitMQ | `localhost:5672`（管理界面 `localhost:15672`） | `apiweb / apiweb123` |
| MinIO | `localhost:9000`（控制台 `localhost:9001`） | `apiweb / apiweb123` |

> 首次启动 MySQL 时，`server/src/main/resources/db/schema.sql` 会自动初始化表结构。

### 2. 启动后端（Spring Boot）

```bash
cd server
mvn spring-boot:run
# 或者：mvn package && java -jar target/api-web-server-*.jar
```

后端启动在 `http://localhost:8080`。

### 3. 启动前端（Vue 3 + Vite）

```bash
cd client
npm install
npm run dev
```

前端启动在 `http://localhost:5173`。登录账号：`admin / admin@123`。

## 📁 目录结构

```
codex-apiweb/
├── client/                  # 前端（Vue 3 + Vite + Ant Design Vue）
│   ├── src/
│   │   ├── api/             # 类型化的 axios 客户端
│   │   ├── components/      # 公共组件（VirtualTree、Monaco、KeyValueEditor、AssertionEditor 等）
│   │   ├── components/workbench/  # 工作台子面板（用例库/编辑器/接口管理/调试记录）
│   │   ├── layouts/         # AppLayout 主框架
│   │   ├── router/          # 路由 + JWT 守卫
│   │   ├── stores/          # Pinia（auth、project）
│   │   ├── types/           # 与后端实体对齐的 TypeScript 类型
│   │   ├── views/           # 全部页面
│   │   ├── App.vue / main.ts
│   │   └── ...
│   ├── vite.config.ts       # 含 /api → 后端的 dev proxy
│   └── package.json
│
├── server/                  # 后端（Spring Boot 3 + MyBatis-Plus）
│   ├── src/main/java/com/apiweb/
│   │   ├── ApiWebApplication.java
│   │   ├── audit/           # @AuditLog 注解 + AOP 切面
│   │   ├── common/          # Result / BizException / BaseEntity
│   │   ├── config/          # MybatisPlus / Redis / RabbitMQ / MinIO / Web / Quartz / Password
│   │   ├── controller/      # 14 个 RESTful Controller
│   │   ├── engine/          # 变量解析、断言评估、提取、HTTP 执行、用例执行器、控制器
│   │   ├── entity/          # 24 个 MyBatis-Plus 实体
│   │   ├── job/             # Quartz 定时任务
│   │   ├── mapper/          # 24 个 BaseMapper
│   │   ├── mq/              # TaskProducer / ApiTaskConsumer / UiTaskConsumer / PerfTaskConsumer
│   │   ├── security/        # JwtUtil / AuthInterceptor / UserContext / CurrentUser
│   │   ├── service/         # 业务服务（ExecutionSupport / OssService / UiExecutionService / PerfExecutionService / ExecutorHeartbeatService / TaskScheduleService）
│   │   └── util/            # JsonUtils
│   └── src/main/resources/
│       ├── application.yml  # MySQL / Redis / RabbitMQ / MinIO / Quartz 配置
│       └── db/schema.sql    # MySQL 8.0 建表脚本（24 张业务表）
│
├── docker-compose.yml       # 中间件：MySQL 8.0 / Redis 7 / RabbitMQ 3.12-management / MinIO
├── docs/                    # 文档占位（按需补充）
└── README.md
```

## 🏛️ 架构关键约定

### 1. 异步任务执行

- API / UI / 性能三类任务分别投递到独立 RabbitMQ 队列（`apiweb.task.api.queue` / `ui.queue` / `perf.queue`）
- 执行机通过 `POST /api/executors/heartbeat` 注册到资源池（Redis 60s TTL + MySQL 持久化）
- 平台侧定时（30s）扫描心跳超时节点，从资源池剔除
- 任务派发时按执行机能力（`api/ui/perf`）挑选

### 1.5 嵌入式 JMeter

- Apache JMeter 5.6.3 作为 Maven 依赖打入 jar（`ApacheJMeter_core` / `ApacheJMeter_http` / `ApacheJMeter_java` / `jorphan`）
- 应用启动时 `JmeterBootstrapper` 解压 4 个配置（`jmeter.properties` / `saveservice.properties` / `upgrade.properties` / `log4j2.xml`）到临时目录并调用 `JMeterUtils` 初始化
- `PerfExecutionService` 直接调用 `StandardJMeterEngine + SaveService.loadTree + ResultCollector` 在当前 JVM 内同步执行压测，**不再依赖外部 `bin/jmeter` 命令或 `JMETER_HOME` 环境变量**
- Spring Boot 默认 logback 替换为 log4j2（`spring-boot-starter-log4j2`），与 JMeter 内部日志体系打通

### 2. 大对象存储

- 调试记录 / 报告详情 / 性能测试 series 中超过 **1MB** 的 JSON 转存 MinIO
- 数据库只存引用路径（`minio://api-web/...`）
- 前端请求时由后端透明回填到响应中

### 3. 时间字段

- 数据库统一使用 `DATETIME`，连接串 `serverTimezone=UTC`
- 所有时间字段以 UTC 存储
- 前端展示时通过 `dayjs.utc(x).local()` 转换本地时区

### 4. Quartz 集群

- 数据库存 Quartz 11 张表（启动时自动创建）
- `@DisallowConcurrentExecution` 防止同一任务重叠
- Cron 表达是 Quartz 风格（与 Linux cron 略有不同）

## 📦 服务器部署（Linux）

```bash
# 在有 Docker 的目标机器上：
git clone <repo> codex-apiweb && cd codex-apiweb
docker compose up -d

# 启动后端
cd server && mvn package -DskipTests
java -jar target/api-web-server-*.jar

# 前端构建静态产物并由 Nginx 服务
cd ../client && npm install && npm run build
# dist/ 目录部署到 Nginx
```

## 🔐 默认账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin@123` | admin |

> ⚠️ 生产环境请立即修改默认密码。

## 📄 License

[MIT](LICENSE)