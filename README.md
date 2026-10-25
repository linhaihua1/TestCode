# Api-Web 自动化测试平台

一个参考 MeterSphere 的**接口自动化 + UI 自动化 + 性能测试**一体化测试平台。前后端分离架构，开箱即用。

> 本仓库为 Vue 3 + Spring Boot 3 全栈重构版本。前一版（React + Fastify + SQLite）已归档至 `backup/pre-vue-java` 分支。

## ✨ 功能特性

| 模块 | 能力 |
|---|---|
| **接口自动化** | 三栏工作台（用例库 / 编写用例 / 接口管理）、多步骤用例（HTTP / IF / FOR / WHILE / SCRIPT）、断言 / 提取 / 变量、Swagger / Excel 导入、Mock、调试记录、版本快照与评审流 |
| **场景编排** | 多用例编排（顺序 / 条件 / 循环 / 等待 / 脚本 / 事务），数据驱动与子报告 |
| **测试任务与报告** | 任务 CRUD、用例挑选与排序、串行 / 并行执行、重试 / 超时 / 失败策略、定时任务、报告生成与 PDF / HTML 导出、分享链接 |
| **UI 自动化** | 基于 Selenium 驱动真实 Chrome、步骤编排（多种定位方式）、失败自动截图、执行计划与报告 |
| **性能测试（嵌入式 JMeter）** | 结构化用例编辑、固定/阶梯加压线程组、`.jmx` 导入导出、异步执行、实时进度、P90/P95/P99/TPS 等多维报告 |
| **CI/CD 集成** | Jenkins 服务器配置与连通性校验、流水线（平台执行 / Jenkins 执行）、质量门禁、CI Token 触发与 Jenkinsfile 一键复制 |
| **权限与审计** | 管理员 / 成员 / 查看者三角色 RBAC，`@AuditLog` AOP 操作审计日志 |
| **执行机资源池** | 执行机通过心跳注册到资源池，按能力（api/ui/perf）自动派发 |
| **可观测性** | Actuator 健康检查、Prometheus 指标、TraceId 链路追踪（MDC）、慢 SQL 监控、Swagger UI API 文档 |

## 🧰 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 · Vite · TypeScript · Ant Design Vue 4 · Pinia · vue-router · vuedraggable · Monaco Editor · ECharts |
| 前端工程 | ESLint · Prettier · Vitest + @vue/test-utils |
| 后端 | Java 17 · Spring Boot 3.2 · MyBatis-Plus · MapStruct |
| 数据库 | MySQL 8.0 |
| 数据库迁移 | Flyway（`baseline-on-migrate`，兼容老库） |
| 缓存 | Redis 7.x（登录会话、执行机心跳、分布式锁） |
| 消息队列 | RabbitMQ 3.12（任务异步投递） |
| 调度 | Quartz Scheduler（集群模式，定时测试任务） |
| 对象存储 | MinIO / OSS（>1MB 的请求/响应体转存） |
| API 文档 | springdoc-openapi（`/v3/api-docs`、`/swagger-ui.html`） |
| 可观测性 | Spring Boot Actuator · Micrometer + Prometheus · Micrometer Tracing + OpenTelemetry（OTLP） |
| 代码质量 | ArchUnit（架构约束约束测试） · JaCoCo（覆盖率门槛） |

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

> 首次启动 MySQL 时，`server/src/main/resources/db/schema.sql` 会自动初始化表结构（31 张业务表）。
> 后续结构变更由 Flyway 管理：新增迁移脚本放到 `server/src/main/resources/db/migration/`（当前为 `V1__baseline.sql`），启动时自动执行。

### 2. 启动后端（Spring Boot）

```bash
cd server
mvn spring-boot:run
# 或者：mvn package && java -jar target/api-web-server-*.jar
```

后端启动在 `http://localhost:8080`。

> **localdev 模式（无 Docker / RabbitMQ / MinIO 的单机调试）**：本机只有 MySQL + Redis 时，用 `localdev` profile 拉起后端，会自动禁用 RabbitMQ 消费端、Quartz 集群、MinIO（对象存储降级为本地文件系统），并改由进程内 `@Async` 直接执行 UI / 性能任务：
>
> ```bash
> cd server
> mvn -DskipTests package
> java -jar target/api-web-server-*.jar --spring.profiles.active=localdev --server.port=8080
> ```
>
> 旧库结构漂移可用 `mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/migrate.sql` 补齐（幂等，需手动执行）。

### 3. 启动前端（Vue 3 + Vite）

```bash
cd client
npm install
npm run dev
```

前端启动在 `http://localhost:5173`。登录账号：`admin / admin@123`。

## 🧭 菜单结构

左侧深色侧边栏，按角色动态显示：

| 一级菜单 | 二级菜单 |
|---|---|
| 接口自动化 | 接口定义 / 接口用例 / 场景编排 / 测试执行 / 测试报告 |
| UI 自动化 | UI 用例 / UI 用例执行 / 测试报告 |
| 性能测试 | 创建用例 / 测试报告 |
| 环境配置 | 环境管理 / 全局变量 / 变量与模块 / 回收站 |
| CI/CD | 流水线 / Jenkins 服务器（仅管理员） |
| 用户管理 | （仅管理员） |
| 操作日志 | （仅管理员） |

> 菜单按角色动态显示：**查看者** 仅可见「接口自动化」「UI 自动化」「CI/CD → 流水线」；**成员** 额外可见「性能测试」「环境配置」；**管理员** 再额外可见「CI/CD → Jenkins 服务器」「用户管理」「操作日志」。

## 📁 目录结构

```
TestCode/
├── client/                  # 前端（Vue 3 + Vite + Ant Design Vue）
│   ├── src/
│   │   ├── api/             # 类型化的 axios 客户端
│   │   ├── components/      # 公共组件（VirtualTree、Monaco、KeyValueEditor、AssertionEditor、ExtractEditor、UiStepEditor 等）
│   │   ├── components/workbench/  # 工作台子面板（用例库/编辑器/接口管理/调试弹窗/调试记录）
│   │   ├── constants/       # 枚举、标签、颜色、HTTP 方法等前端唯一常量源
│   │   ├── hooks/           # 组合式函数（如 useShortcuts）
│   │   ├── layouts/         # AppLayout 主框架
│   │   ├── router/          # 路由 + JWT 守卫
│   │   ├── stores/          # Pinia（auth、project）
│   │   ├── styles/          # 设计系统（tokens.css / global.css / antTheme.ts）
│   │   ├── types/           # 与后端 DTO/VO 对齐的 TypeScript 类型
│   │   ├── utils/           # 工具函数（format、uid、kvRows、treeFilter、monacoWorkers）
│   │   ├── views/           # 全部页面（26 个）
│   │   ├── App.vue / main.ts
│   │   └── ...
│   ├── vitest.config.ts     # 前端单测配置
│   ├── vite.config.ts       # 含 /api → 后端的 dev proxy
│   └── package.json
│
├── server/                  # 后端（Spring Boot 3 + MyBatis-Plus）
│   ├── src/main/java/com/apiweb/
│   │   ├── ApiWebApplication.java
│   │   ├── audit/           # @AuditLog 注解 + AOP 切面
│   │   ├── common/          # Result / ErrorCode / BizException / PageResult / BaseEntity
│   │   ├── config/          # MybatisPlus / Redis / RabbitMQ / MinIO / Web / Quartz / Password / OpenApi / *Properties
│   │   ├── controller/      # 23 个 RESTful Controller
│   │   ├── converter/       # MapStruct Entity ↔ DTO/VO 转换器
│   │   ├── dto/             # 请求 DTO（XxxReq）
│   │   ├── vo/              # 响应 VO（XxxVO）
│   │   ├── engine/          # 变量解析、断言评估、提取、HTTP 执行、用例执行器、步骤执行器、流程控制器
│   │   ├── entity/          # 31 个 MyBatis-Plus 实体
│   │   ├── job/             # Quartz 定时任务
│   │   ├── mapper/          # 31 个 BaseMapper
│   │   ├── mq/              # TaskProducer / ApiTaskConsumer / UiTaskConsumer / PerfTaskConsumer
│   │   ├── observability/   # TraceId 过滤器、慢 SQL 拦截器
│   │   ├── openapi/         # OpenAPI 文档补充配置
│   │   ├── report/          # 报告分享等
│   │   ├── security/        # JwtUtil / AuthInterceptor / UserContext / CurrentUser / Role
│   │   ├── service/         # 业务服务（30 个，含 ExecutionSupportService / ReportGenerationService / JmxImportExportService / CiExecutionService 等）
│   │   └── util/            # JsonUtils 等
│   ├── src/main/resources/
│   │   ├── application.yml          # MySQL / Redis / RabbitMQ / MinIO / Quartz / Flyway / 可观测性配置
│   │   ├── application-localdev.yml # 单机调试 profile
│   │   ├── log4j2.xml
│   │   ├── jmeter/                  # 嵌入式 JMeter 配置（4 个）
│   │   └── db/
│   │       ├── schema.sql           # MySQL 8.0 建表脚本（31 张业务表）
│   │       ├── migrate.sql          # 手工幂等迁移脚本（旧库补表/补列）
│   │       └── migration/V1__baseline.sql   # Flyway 基线
│   └── src/test/java/com/apiweb/     # 37 个测试文件 / 40 个测试类 / 201 个用例
│
├── docker-compose.yml       # 中间件：MySQL 8.0 / Redis 7 / RabbitMQ 3.12-management / MinIO
├── scripts/                 # 运维脚本（start / stop / build / deploy / logs / reset-db）
├── docs/                    # 项目文档（见下表）
└── README.md
```

## 📚 文档

| 文档 | 面向 | 内容 |
|---|---|---|
| [docs/INSTALL.md](docs/INSTALL.md) | 运维 / 部署 | 系统要求、Docker Compose 与手动安装、生产部署、故障排查 |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | 测试工程师 / 业务使用者 | 功能模块与日常使用流程、常见问题 |
| [docs/DEVELOPER.md](docs/DEVELOPER.md) | 开发工程师 | 工程结构、本地开发、二次开发要点、测试与调试 |
| [docs/需求文档.md](docs/需求文档.md) | 产品 | 原始需求与功能规格（PRD） |
| [docs/开发文档.md](docs/开发文档.md) | 研发 | 数据模型、API 规范、关键算法与错误码定义 |
| [docs/企业级架构优化方案.md](docs/企业级架构优化方案.md) | 架构 | 分层 / 可观测 / 可回归的分阶段演进方案与进度 |

## 🏛️ 架构关键约定

### 1. 异步任务执行

- API / UI / 性能三类任务分别投递到独立 RabbitMQ 队列（`apiweb.task.api.queue` / `ui.queue` / `perf.queue`）
- 三个消费者均标注 `@ConditionalOnProperty(name = "apiweb.mq.enabled", ...)`，`localdev` 下不注册
- 执行机通过 `POST /api/v1/executors/heartbeat` 注册到资源池（Redis TTL + MySQL 持久化）
- 平台侧定时扫描心跳超时节点，从资源池剔除
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
- 前端展示时统一走 `utils/format.ts` 的 `formatDateTime()` 转换本地时区

### 4. 分层与契约

- 分层为 Controller → Service → Mapper，**Controller 不直接注入 Mapper**（由 ArchUnit 架构测试约束）
- API 出入参使用 `dto/`、`vo/`，通过 MapStruct `converter/` 转换，Entity 不直接出入参
- 响应统一 `Result<T>`：`{ code, message, data }`，基础路径 `/api/v1`

### 5. Quartz 集群

- 数据库存 Quartz 表（启动时自动创建）
- `@DisallowConcurrentExecution` 防止同一任务重叠
- Cron 表达式是 Quartz 风格（与 Linux cron 略有不同）

## 📦 服务器部署（Linux）

```bash
# 在有 Docker 的目标机器上：
git clone <repo> TestCode && cd TestCode
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
