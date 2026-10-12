# Api-Web 开发文档

> 本文档面向 **后端 / 前端开发工程师**，介绍工程结构、本地开发流程、二次开发要点。
> 阅读对象应熟悉 Java 17+、Spring Boot 3、Vue 3、TypeScript。

---

## 目录

1. [工程结构](#1-工程结构)
2. [技术栈](#2-技术栈)
3. [本地开发环境搭建](#3-本地开发环境搭建)
4. [后端开发指南](#4-后端开发指南)
5. [前端开发指南](#5-前端开发指南)
6. [架构关键设计](#6-架构关键设计)
7. [扩展点](#7-扩展点)
8. [测试](#8-测试)
9. [调试技巧](#9-调试技巧)
10. [常见开发问题](#10-常见开发问题)

---

## 1. 工程结构

```
api-web/                                # 仓库根
├── client/                             # 前端工程（Vue 3 + Vite）
│   ├── src/
│   │   ├── api/                        # API 客户端（按域拆分）
│   │   ├── components/                 # 公共组件（VirtualTree / Monaco / KeyValueEditor / AssertionEditor / ExtractEditor / UiStepEditor ...）
│   │   │   └── workbench/              # 三栏工作台专用面板
│   │   ├── constants/                  # 枚举 / 标签 / 颜色 / HTTP 方法等前端唯一常量源
│   │   ├── hooks/                      # 组合式函数（useShortcuts 等）
│   │   ├── layouts/                    # 布局（带侧栏的 AppLayout）
│   │   ├── router/                     # vue-router 配置
│   │   ├── stores/                     # Pinia 状态管理
│   │   ├── styles/                     # 设计系统（tokens.css / global.css / antTheme.ts）
│   │   ├── types/                      # TypeScript 类型定义
│   │   ├── utils/                      # 工具（format / uid / kvRows / treeFilter / monacoWorkers）
│   │   ├── views/                      # 页面组件（26 个，按路由对应）
│   │   ├── App.vue                     # 根组件
│   │   └── main.ts                     # 入口文件
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vitest.config.ts                # 前端单测配置
│   └── vite.config.ts                  # Vite 配置（含 dev server proxy）
│
├── server/                             # 后端工程（Spring Boot 3）
│   ├── src/main/
│   │   ├── java/com/apiweb/
│   │   │   ├── ApiWebApplication.java  # 启动类
│   │   │   ├── audit/                  # 审计日志（AOP 自动落库）
│   │   │   ├── common/                 # 通用：Result / ErrorCode / BizException / PageResult / 基类
│   │   │   ├── config/                 # 配置：MyBatis-Plus / Redis / RabbitMQ / MinIO / Quartz / OpenApi / 各 *Properties
│   │   │   ├── controller/             # RESTful Controller 层（23 个）
│   │   │   ├── converter/              # MapStruct Entity ↔ DTO/VO 转换器
│   │   │   ├── dto/                    # 请求 DTO（XxxReq）
│   │   │   ├── vo/                     # 响应 VO（XxxVO）
│   │   │   ├── engine/                 # 执行引擎核心（变量/断言/HTTP/步骤执行器/流程控制器/JMeter）
│   │   │   ├── entity/                 # MyBatis-Plus 实体（31 个，每张表一个）
│   │   │   ├── job/                    # Quartz 定时任务
│   │   │   ├── mapper/                 # MyBatis-Plus Mapper 接口（31 个）
│   │   │   ├── mq/                     # RabbitMQ 生产者/消费者
│   │   │   ├── observability/          # TraceId 过滤器、慢 SQL 拦截器
│   │   │   ├── openapi/                # OpenAPI 文档补充
│   │   │   ├── report/                 # 报告分享等领域枚举
│   │   │   ├── security/               # JWT 鉴权、UserContext、CurrentUser、Role
│   │   │   ├── service/                # 业务服务层（30 个）
│   │   │   └── util/                   # 工具类
│   │   └── resources/
│   │       ├── application.yml         # Spring 配置（含 Flyway / 可观测性 / 各 apiweb.* 段）
│   │       ├── application-localdev.yml# 单机调试 profile
│   │       ├── db/schema.sql           # MySQL 初始化脚本（31 张业务表）
│   │       ├── db/migrate.sql          # 手工幂等迁移（旧库补表/补列）
│   │       ├── db/migration/V1__baseline.sql   # Flyway 基线
│   │       ├── jmeter/                 # 嵌入式 JMeter 配置（4 个）
│   │       └── log4j2.xml              # Spring Boot 日志
│   ├── src/test/java/com/apiweb/       # 单元 / 契约 / 架构测试（18 个测试类，102 个用例）
│   └── pom.xml                         # Maven 配置
│
├── docs/                               # 项目文档
│   ├── INSTALL.md                      # 安装文档
│   ├── USER_GUIDE.md                   # 操作手册
│   ├── DEVELOPER.md                    # 本文件
│   ├── 需求文档.md                      # 原始需求（PRD）
│   ├── 开发文档.md                      # 数据模型 / API / 算法 / 错误码
│   └── 企业级架构优化方案.md              # 分层演进的方案与进度
│
├── scripts/                            # 运维脚本
│   ├── start.sh                        # 一键启动
│   ├── stop.sh                         # 一键停止
│   ├── build.sh                        # 构建前后端
│   ├── deploy.sh                       # 生产部署（生成 docker-compose.prod.yml）
│   ├── logs.sh                         # 实时查看日志
│   └── reset-db.sh                     # 重置数据库（仅开发）
│
├── deploy/                             # 部署包（执行 deploy.sh 后生成）
├── docker-compose.yml                  # 开发用中间件编排
├── README.md                           # 项目说明
└── LICENSE
```

> **数据库结构变更**：`schema.sql` 用 `CREATE TABLE IF NOT EXISTS`，对已存在的旧库不会补列。
> 新结构变更统一走 **Flyway**：在 `server/src/main/resources/db/migration/` 下新增 `V{n}__xxx.sql`，启动时自动执行（`baseline-on-migrate: true` 兼容老库）。
> 历史遗留的 `db/migrate.sql` 为手工幂等脚本，作为旧库兜底保留。
> 结构漂移可用 `python server/scripts/check_schema_drift.py` 自动比对实体字段与数据库列。

---

## 2. 技术栈

### 2.1 后端

| 技术 | 版本 | 作用 |
|---|---|---|
| Java | 17 | 语言 |
| Spring Boot | 3.2.5 | Web 框架 |
| Spring AMQP | 3.x | RabbitMQ 集成 |
| Spring Data Redis | 3.x | Redis 客户端 |
| Spring Quartz | 3.x | 分布式定时任务 |
| MyBatis-Plus | 3.5.6 | ORM（基于 MyBatis） |
| MySQL Connector | 8.x | JDBC 驱动 |
| Flyway | 9.x | 数据库版本化迁移（`baseline-on-migrate`） |
| MapStruct | 1.5.5 | Entity ↔ DTO/VO 编译期转换 |
| JWT (jjwt) | 0.12.5 | 无状态认证 |
| MinIO Java SDK | 8.5.9 | 对象存储 |
| Apache JMeter | 5.6.3 | 嵌入式性能测试引擎 |
| Selenium Java | 4.19.1 | UI 自动化 |
| Hutool | 5.8.27 | Java 工具集 |
| Lombok | 1.18.x | 代码简化 |
| Log4j2 | 2.22 | 日志（替换默认 logback） |
| Spring Boot Actuator + Micrometer | 3.x | 健康检查 / 指标（Prometheus） |
| Micrometer Tracing + OpenTelemetry | 1.x | TraceId 链路追踪（可导出 OTLP） |
| springdoc-openapi | 2.x | OpenAPI 文档 + Swagger UI |
| ArchUnit | 1.x | 架构约束测试（分层 / 命名） |
| JaCoCo | 0.8.x | 测试覆盖率报告与门槛 |

### 2.2 前端

| 技术 | 版本 | 作用 |
|---|---|---|
| Vue | 3.4+ | 框架（Composition API） |
| TypeScript | 5.x | 类型 |
| Vite | 5.x | 构建 / 开发服务器 |
| Ant Design Vue | 4.x | 组件库 |
| Pinia | 2.x | 状态管理 |
| vue-router | 4.x | 路由 |
| vuedraggable | 4.x | 拖拽（基于 SortableJS） |
| monaco-editor | 0.45.x | 代码编辑器（脚本编辑） |
| echarts | 5.x | 报告图表（配 vue-echarts） |
| xlsx | 0.18.x | Excel 导入解析 |
| dayjs | - | 时间处理（含 UTC） |
| axios | 1.x | HTTP 客户端 |
| ESLint | 9.x | 代码规范（`npm run lint`） |
| Prettier | 3.x | 代码格式化（`eslint-config-prettier` 协同） |
| Vitest + @vue/test-utils | 2.x / 2.x | 前端单元测试（`npm run test`） |

---

## 3. 本地开发环境搭建

### 3.1 克隆代码

```bash
git clone <repo-url> api-web
cd api-web
```

### 3.2 启动中间件（推荐用 Docker）

```bash
docker compose up -d
```

如未安装 Docker，可手动安装 MySQL/Redis/RabbitMQ/MinIO（详见 INSTALL.md §3）。

### 3.3 初始化数据库

```bash
# 方式 A：通过 docker exec
docker exec -i apiweb-mysql mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql

# 方式 B：手动
mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql
```

> **数据库版本化迁移（Flyway）**：后端启动时 Flyway 会自动执行 `db/migration/` 下的迁移脚本（当前为 `V1__baseline.sql`，31 张业务表）。`baseline-on-migrate: true` 表示遇到已有数据的旧库会先把当前状态记为 V1 基线、不重复执行 V1，因此 **老库无需手工导入 schema**。
>
> - 新增结构变更：在 `db/migration/` 下新建 `V2__xxx.sql`，启动即生效。
> - `schema.sql` 仅用于全新库初次建表（Docker 首次启动由 `docker-entrypoint-initdb.d` 自动导入，也可手工执行）。
> - 历史遗留的 `db/migrate.sql` 为手工幂等脚本，作为旧库兜底保留：
>   `mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/migrate.sql`
> - 结构漂移可用 `python server/scripts/check_schema_drift.py` 自动比对实体字段与数据库列。

### 3.4 启动后端（开发模式）

```bash
cd server
mvn spring-boot:run
```

或用 IDE（IntelliJ IDEA）打开 `server/` 作为 Maven 项目，直接运行 `ApiWebApplication` 主类。

后端默认端口 `8080`，启动后可访问 http://localhost:8080/api。

> **localdev 模式（单机无 Docker 调试）**：本机没有 RabbitMQ/MinIO/Quartz 集群时，用 `localdev` profile 即可只依赖 MySQL + Redis 拉起后端：
>
> ```bash
> java -jar target/api-web-server-*.jar --spring.profiles.active=localdev
> ```
>
> 该 profile 会禁用 RabbitMQ 消费端、Quartz 集群、MinIO（对象存储降级为本地文件系统），并排除相应自动配置，避免无 MQ 时反复重连刷屏。
>
> **任务执行回退机制（关键）**：三个 MQ 消费者（`ApiTaskConsumer` / `UiTaskConsumer` / `PerfTaskConsumer`）均标注 `@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)`。当 `apiweb.mq.enabled=false`（localdev 下）时，消费者 Bean 不会注册，`UiController` / `PerfController` 的 run 接口会改走 `@Value("${apiweb.mq.enabled:true}")` 判断，false 时直接调用 `uiExecutionService.executeAsync` / `perfExecutionService.executeAsync`（`@Async`）在进程内同步执行，避免 UI / 性能任务因无 MQ 消费端而永远停在 pending。`@EnableAsync` 已在 `ApiWebApplication` 上开启。

### 3.5 启动前端（开发模式）

```bash
cd client
npm install
npm run dev
```

前端开发服务器默认 `5173`，访问 http://localhost:5173 。

`vite.config.ts` 已配置 `/api` 代理到 `http://localhost:8080`，无需关心跨域。

### 3.6 推荐 IDE 配置

- **IntelliJ IDEA**：安装 Lombok / MyBatisX / Vue 插件
- **VS Code**：安装 Volar / ESLint / Prettier / Vue Language Features 插件

---

## 4. 后端开发指南

### 4.1 包结构与命名约定

工程采用**按层分包 + 层内按域再分包**（`dto` / `vo` / `converter` 的内部按业务域建子包，如 `dto/casee`、`vo/apidefinition`）：

```
com.apiweb/
├── controller/           REST 控制器（只做参数校验 + 调 Service + 返回 VO，禁止注入 Mapper）
├── service/              业务服务（业务逻辑 + @Transactional + 跨域编排）
├── mapper/               MyBatis-Plus Mapper 接口
├── entity/               数据实体（与表对应，@TableName 注解）
├── dto/{domain}/         请求 DTO（XxxReq，带 @NotBlank/@Size 校验注解）
├── vo/{domain}/          响应 VO（XxxVO）
├── converter/            MapStruct 转换器（XxxConverter，Entity ↔ DTO/VO）
└── ...
```

**JSON 字段的双向约定（重要）**：实体里以 `String` 存储 JSON 快照的字段（如用例步骤 `steps`、接口参数 `query`/`headers`、环境变量 `variables`），在

- **入参 DTO** 上必须加 `@JsonDeserialize(using = JsonStringDeserializer.class)`，否则前端传数组/对象时反序列化失败，接口返回 `1001 请求体格式错误`；
- **出参 VO** 上必须把字段声明为 `Object`，并在对应 `XxxConverter` 的 `@AfterMapping` 里用 `JsonUtils.parseObject` 解析成结构化对象，否则前端拿到的是字符串、按数组使用时崩溃。

新增此类字段时两侧都要处理，且补一条对应单测。

每个 Controller 类加 `@RestController` + `@RequestMapping("/api/v1/xxx")`（除 `MockController` 挂在 `/api/mock` 作为对外 Mock 入口）。

### 4.2 添加一个 CRUD 模块（标准流程）

假设要新增一个 `缺陷` 模块，步骤：

1. **创建数据库表**（在 `db/schema.sql` 中追加）：

```sql
CREATE TABLE t_bug (
    id VARCHAR(32) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    severity VARCHAR(20),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    deleted_at DATETIME,
    INDEX idx_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

2. **创建实体** `entity/BugEntity.java`：

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bug")
public class BugEntity extends BaseEntity {
    private String title;
    private String severity;
}
```

`BaseEntity` 已包含 `id`、`createdAt`、`updatedAt`、`deletedAt` 等通用字段。

3. **创建 Mapper** `mapper/BugMapper.java`：

```java
public interface BugMapper extends BaseMapper<BugEntity> {}
```

继承 `BaseMapper<T>` 自动获得 CRUD，无需写 XML。

4. **创建 Service** `service/BugService.java`（业务逻辑 + 事务收敛于此，向上返回 Entity）：

```java
@Service
@RequiredArgsConstructor
public class BugService {
    private final BugMapper bugMapper;

    @Transactional
    public BugEntity create(BugReq req) {
        BugEntity bug = new BugEntity();
        bug.setTitle(req.getTitle());
        bug.setSeverity(req.getSeverity());
        bugMapper.insert(bug);
        return bug;
    }

    public BugEntity get(String id) {
        return bugMapper.selectById(id);
    }

    public List<BugEntity> list() {
        return bugMapper.selectList(null);
    }
}
```

5. **创建 DTO / VO / Converter**（`dto/bug/BugReq.java`、`vo/bug/BugVO.java`、`converter/BugConverter.java`）：

```java
public interface BugConverter {
    BugVO toVO(BugEntity entity);
    List<BugVO> toVOList(List<BugEntity> list);
}
```

DTO 带校验注解（`@NotBlank` / `@Size`），VO 只暴露对外字段；MapStruct 在编译期生成实现类。

6. **创建 Controller** `controller/BugController.java`（只做「参数校验 + 调 Service + 返回 VO」，**不注入 Mapper、不直接出入参 Entity**）：

```java
@RestController
@RequestMapping("/api/v1/bugs")  // 注意统一 /api/v1 前缀
@RequiredArgsConstructor
public class BugController {
    private final BugService bugService;
    private final BugConverter bugConverter;

    @GetMapping
    public Result<List<BugVO>> list() {
        return Result.ok(bugConverter.toVOList(bugService.list()));
    }

    @PostMapping
    public Result<BugVO> create(@RequestBody @Valid BugReq req) {
        return Result.ok(bugConverter.toVO(bugService.create(req)));
    }

    @GetMapping("/{id}")
    public Result<BugVO> get(@PathVariable String id) {
        return Result.ok(bugConverter.toVO(bugService.get(id)));
    }
}
```

7. **重启后端**，调用 `curl http://localhost:8080/api/v1/bugs` 验证（`architecture/LayeredArchitectureTest` 会校验分层约束）。

### 4.3 通用响应格式

所有 Controller 返回 `Result<T>`：

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}
```

- `code = 0`：成功
- `code != 0`：失败，`message` 为错误描述
- HTTP 状态码：成功 200，业务失败 400，未授权 401，权限不足 403，未找到 404，服务异常 500
- 所有 Controller 统一加 `/api/v1` 前缀（`@RequestMapping("/api/v1/xxx")`），前端 `axios.baseURL = /api/v1`，vite dev proxy `/api` → `http://localhost:8080`

业务异常抛 `BizException(ErrorCode.XXX, "附加信息")`，由 `GlobalExceptionHandler` 统一转换为 `Result`。

### 4.3.1 业务码契约（前后端必须一致，禁止裸字符串）

`server/src/main/java/com/apiweb/common/ErrorCode.java` 定义了所有业务码，**`code` 是前后端 / DB / 审计日志的协议位**：

| 码值 | 常量 | 含义 | 前端处理 |
|---|---|---|---|
| `0` | `SUCCESS` | 成功 | 取 `data` |
| `1001` | `BAD_REQUEST` | 参数校验失败 | 弹错 |
| `1002` | `UNAUTHORIZED` | 登录态失效（token 过期 / 缺失 / 无效） | 清登录态并跳 `/login` |
| `1003` | `FORBIDDEN` | 无权限（被 `@RequireRole` 拒绝） | 提示「权限不足」 |
| `1004` | `NOT_FOUND` | 资源不存在 | 弹错 |
| `1005` | `CONFLICT` | 名称冲突 | 弹错 |
| `1006` | `LOGIN_FAILED` | 账号或密码错误 | 弹错，**不动登录态** |
| `2001+` | 业务校验（如 `CASE_NAME_EMPTY`） | 业务规则违反 | 弹错 |
| `3001+` | 解析 / 导入失败（`SWAGGER_PARSE_FAILED` 等） | 数据格式问题 | 弹错 |
| `4001+` | 执行失败（`DEBUG_EXECUTION_FAILED` 等） | 运行时异常 | 弹错 |

> **关键纪律**：`1002` 与 `1006` 严格区分。前者必须返回 HTTP 401 + `Result(code=1002)` 结构，由 `AuthInterceptor` 写出；后者是普通业务错误，HTTP 200 + `Result(code=1006)`。`server/src/test/java/com/apiweb/security/LoginErrorCodeTest.java` 与 `AuthInterceptorTest.java` 已锁死这两条契约，修改任意一处会被测试拦截。

修改 / 新增错误码时，必须同步前端 `client/src/constants/index.ts` 的 `API_CODE` 常量，并提交一节使用该码的前端分支处理。

### 4.3.2 角色与权限注解

- 角色枚举 `Role`：admin / member / viewer；常量 `ADMIN_CODE` / `MEMBER_CODE` / `VIEWER_CODE` 是 `String` 常量，供 `@RequireRole` 注解使用（注解属性必须编译期常量）。
- 类级 `@RequireRole("admin")` 限制接口仅管理员可访问；方法级 `@RequireRole` 优先级更高。
- 非 admin 访问被拦截时返回 HTTP 403 + `Result(code=1003)`。
- 切换为 admin 的角色判断：`UserContext.getCurrentUser().isAdmin()`。

### 4.4 鉴权

需要登录的接口走 JWT 流程：

1. `POST /api/v1/auth/login` 获取 token
2. 后续请求 Header 携带 `Authorization: Bearer <token>`
3. `AuthInterceptor` 校验 token，解析出 userId，写入 `UserContext`（ThreadLocal）
4. 业务代码通过 `UserContext.getCurrentUserId()` 获取当前用户

**401 响应体**：当 token 缺失 / 无效 / 过期时，`AuthInterceptor` 返回 HTTP 401 + `Result.fail(1002, "登录已过期，请重新登录")` 的统一 JSON 结构。前端 `api/client.ts` 拦截器会清 localStorage 的 `token` / `user` 并跳 `/login`。该响应体格式受 `AuthInterceptorTest` 保护，禁止退化为手写 JSON 或其它业务码。

### 4.5 审计日志

在 Controller 方法上加 `@AuditLog(action = "...", entityType = "...")`，`AuditAspect` 会自动写入 `t_audit_log`：

```java
@PostMapping
@AuditLog(action = "create", entityType = "case")
public Result<CaseVO> create(@RequestBody @Valid CaseReq req) {
    return Result.ok(caseConverter.toVO(caseService.create(req)));
}
```

- `action`：动作名，建议动词（`create` / `update` / `delete` / `execute` / `import` / `export`）；失败时 AOP 自动追加 `:error` 后缀。
- `entityType`：实体类型，对应业务表名（如 `case` / `project` / `api_definition`）。
- 两者均为必填。

审计字段：操作人、操作时间、IP、操作类型、目标实体 ID、变更前后值。

### 4.6 RabbitMQ 异步任务

交换机 / 队列 / 路由键全部来自 `MqProperties`（`apiweb.mq.*`），代码中不硬编码名称。

发送任务（`mq/TaskProducer.java`）：

```java
taskProducer.sendApiTask(runId, projectId, taskId, caseIds);   // 另有 sendUiTask / sendPerfTask
// 内部：rabbitTemplate.convertAndSend(mq.getExchange(), mq.getRoutingKey().getApi(), message);
```

消费任务（`mq/ApiTaskConsumer.java`）：

```java
@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class ApiTaskConsumer {
    @RabbitListener(queues = "${apiweb.mq.queue.api}")
    public void onMessage(@Payload EngineDtos.TaskMessage message, Channel channel) {
        // 手动 ack（listener.simple.acknowledge-mode=manual）
    }
}
```

新增队列：在 `application.yml` 的 `apiweb.mq` 段加队列/路由键，并在 `config/RabbitMQConfig.java` 声明 `Queue` / `Exchange` / `Binding` Bean。

> **MQ 开关**：三个消费者均标注 `@ConditionalOnProperty(name = "apiweb.mq.enabled", havingValue = "true", matchIfMissing = true)`。`application.yml` 默认 `apiweb.mq.enabled: true`，`application-localdev.yml` 置为 `false`。关闭后消费者不注册，UI / 性能任务由 Controller 改走进程内 `@Async` 回退执行（见 §3.4）。

### 4.7 Quartz 定时任务

现有两个 Job：`job/TestTaskScheduleJob.java`（定时测试任务）、`job/RecycleBinAutoCleanJob.java`（回收站自动清理）。

新增定时任务的步骤：

1. 实现 `Job` 接口：`job/MyJob.java implements Job`
2. 在调度服务里注册 JobDetail + CronTrigger（参考 `service/TaskScheduleService.java` 的 `schedule(...)`；它用 `ObjectProvider<Scheduler>` 惰性获取 Scheduler，**localdev 下 Quartz 未启用时静默跳过**，不会因缺少 bean 启动失败）
3. Cron 表达式可配（如回收站清理走 `apiweb.recycle-bin.clean-cron`）
4. 支持集群模式（多个节点同时跑只会触发一次）

### 4.8 嵌入式 JMeter 扩展

如需新增 JMeter 节点类型，编辑 `service/PerfExecutionService.java` 中的 `JmxBuilder`：

```java
// 示例：添加 HTTP Cookie Manager
sb.append("      <CookieManager guiclass=\"CookiePanel\" testclass=\"CookieManager\" testname=\"Cookie Manager\">\n");
sb.append("        <boolProp name=\"CookieManager.clearEachIteration\">false</boolProp>\n");
sb.append("      </CookieManager>\n      <hashTree/>\n");
```

字段对应关系参考 JMeter 源码：`TestElementGUI.getStringValue()` 输出的 `.jmx` XML。

> **JMX 关键字段约定**（已踩坑记录）：
> - ThreadGroup 必须包含 `elementProp name="ThreadGroup.main_controller" elementType="LoopController"`（含 loops / scheduler），否则 JMeter 执行时抛异常。
> - `HTTPSamplerProxy` 中 URL 用 `stringProp name="HTTPSampler.domain"` 里的 `host` 字段（**不是** `domain`）；Query / Body 作为 `elementProp name="HTTPsampler.Arguments"` 属性；`boolProp name="HTTPSampler.postBodyRaw"` 必须放在 HTTPSamplerProxy 内部；HeaderManager 作为其 hashTree 子节点。
> - `.jmx` 导入导出由 `service/JmxImportExportService.java` 统一处理，修改节点结构时务必同步该服务。

### 4.8.1 报告生成（ReportGenerationService）

接口任务执行完成后，`service/ReportGenerationService.java` 负责从本次运行结果汇总生成 `t_report` + `t_report_detail` 并回填统计字段（total / passed / failed / skipped 等）。三类报告对应关系：

| 报告类型 | 数据表 | 生成入口 |
|---|---|---|
| 接口报告 | `t_report` / `t_report_detail` | `ReportGenerationService`（任务执行后调用） |
| 场景报告 | `t_report` / `t_report_detail` | `ScenarioController.execute`（先插 report 再插 detail） |
| UI 报告 | `t_ui_report` | `UiExecutionService` |
| 性能报告 | `t_perf_report` | `PerfExecutionService` |

> 注意：场景报告插入前必须显式设置 `status` 与统计默认值，否则 `t_report.status` 的 NOT NULL 约束会抛异常。

### 4.9 UI 自动化执行（UiExecutionService）

`service/UiExecutionService.java` 基于 Selenium 驱动真实 Chrome。ChromeDriver 版本需与执行机安装的 Chrome 浏览器版本匹配，否则会报 session 创建失败。若执行机 Chrome 与默认驱动版本不一致，可通过环境变量指定匹配的 chromedriver 路径：

```bash
export WEBDRIVER_CHROME_DRIVER=/path/to/chromedriver   # Windows: set WEBDRIVER_CHROME_DRIVER=C:\path\to\chromedriver.exe
```

读取优先级：环境变量 `WEBDRIVER_CHROME_DRIVER` > 系统属性 `webdriver.chrome.driver`。

---

## 5. 前端开发指南

### 5.1 目录约定

- **views/**：每个路由对应一个 Vue 文件
- **components/**：可复用组件（含 `workbench/` 工作台子面板）
- **layouts/**：布局组件（`AppLayout.vue` 左侧侧边栏框架）
- **api/**：按域拆分的 API 调用函数
- **stores/**：Pinia store
- **router/**：路由表
- **types/**：TS 类型定义（对应后端 DTO）
- **styles/**：设计系统 —— `tokens.css`（设计令牌）/ `global.css`（全局样式与工具类）/ `antTheme.ts`（antd 主题）
- **utils/**：工具函数（如 `uid.ts` 拖拽排序标识）
- **hooks/**：组合式函数（如 `useShortcuts.ts` 全局快捷键）

> 组件样式统一使用 `styles/tokens.css` 中的 CSS 变量（`--c-*`、`--bg-*`、`--bd-*`、`--sp-*`、`--rd-*`、`--sd-*`），不要硬编码色值。

### 5.2 API 调用约定

所有请求通过 `src/api/client.ts` 包装的 axios 实例：

```ts
import { http } from '@/api/client';

export const listCases = (projectId: string) =>
  http.get<CaseEntity[]>('/cases', { params: { projectId } });
// 实际请求 URL: /api/v1/cases?projectId=xxx
//   - 前缀 /api/v1 由 client.ts 的 baseURL 自动拼接
//   - 调用方传路径以 / 开头，不重复 /api/v1 前缀
```

响应统一解包 `data`，并处理业务码：

```ts
// client.ts 中已设置 interceptor
//   - code=0   → 返回 data.data
//   - code=1002 或 HTTP 401 → 清 localStorage('token'/'user')，window.location 跳 /login
//   - code=1003 → 弹「权限不足」提示
//   - code=1006 → 弹「用户名或密码错误」（业务校验错误，不影响登录态）
//   - 其它 code → throw new Error(message)
```

### 5.3 状态管理

Pinia store 用 setup 风格：

```ts
export const useProjectStore = defineStore('project', () => {
  const current = ref<Project | null>(null);
  const setCurrent = (p: Project) => { current.value = p; };
  return { current, setCurrent };
});
```

### 5.4 添加一个新页面

1. 在 `views/` 创建 `MyPage.vue`
2. 在 `router/index.ts` 注册路由：

```ts
{
  path: 'my-page',
  component: () => import('@/views/MyPage.vue'),
  meta: { title: '我的页面', icon: 'FileOutlined' },
}
```

3. 在 `AppLayout.vue` 侧边栏菜单加菜单项（如果需要单独入口）

### 5.5 三栏工作台（核心页面）

`views/Workbench.vue` 是测试用例编辑的核心，顶部为配置栏（全局变量/调试记录/回收站），下方由 3 个子面板组成：

- `components/workbench/WorkbenchLeftPanel.vue` —— 左侧用例库（搜索 + 模块树 + 用例列表）
- `components/workbench/CaseLibraryPanel.vue` —— 用例库目录/用例列表子面板
- `components/workbench/CaseEditorPanel.vue` —— 中间编写用例（脚本/调试记录双 Tab + 前置/测试/后置三分区）
- `components/workbench/ApiManagerPanel.vue` —— 右侧接口管理（目录树 + Excel 导入 + 拖拽生成步骤）
- `components/workbench/StepCard.vue` —— 单步骤卡片（禁用/启用开关、右键复制粘贴、多种步骤类型）
- `components/workbench/DebugModal.vue` —— 调试弹窗（环境变量选择与增删改）
- `components/workbench/DebugRecordsModal.vue` —— 单用例调试记录弹窗

> **接口拖拽生成步骤**：接口管理面板的接口项带 `draggable="true"`，拖拽时通过 `dataTransfer.setData('application/api-definition', ...)` 传参；用例编辑面板的三个步骤分区监听 `drop`，`addApiStep` 据此生成一条 HTTP 步骤（含默认状态码断言）。

子面板通过 `props / emits` 与父组件通信，避免 Pinia store 滥用。

### 5.6 拖拽

使用 `vuedraggable`（基于 SortableJS）。注意 vuedraggable 4.x **强制 `#item` 插槽 + `item-key`**，
可拖拽数据需带唯一 `_id` 字段（新增行时用 `utils/uid.ts` 的 `uid()` 生成，旧数据用 `ensureIds()` 补齐）：

```vue
<draggable v-model="stepList" item-key="_id" :animation="150" handle=".drag-handle">
  <template #item="{ element: step }">
    <div class="step-item">
      <DragOutlined class="drag-handle" />
      {{ step.name }}
    </div>
  </template>
</draggable>
```

### 5.7 Monaco Editor

封装在 `components/Monaco.vue`：

```vue
<Monaco v-model="code" language="javascript" height="300px" />
```

Worker 配置在 `src/utils/monacoWorkers.ts`：

- 注册 `window.MonacoEnvironment.getWorker(...)`，根据 `workerId + label` 返回对应 worker 构造器；
- Vite 通过 `?worker` 后缀处理 worker bundle，开发服务器启动时会复制到 `/monacoeditorwork/` 子路径；
- 漏配该文件会在控制台看到「Could not find worker」错误，且编辑器失去语法高亮与补全。

### 5.8 虚拟滚动树

`components/VirtualTree.vue`：基于 Ant Design Vue Tree 二次封装，支持 10 万级节点流畅滚动。

```vue
<VirtualTree :data="treeData" :load="loadChildren" @select="onSelect" />
```

---

## 6. 架构关键设计

### 6.1 时间字段处理

**后端约定**：
- 所有 `DATETIME` 字段存储 UTC 时间
- JDBC 连接串强制 `serverTimezone=UTC`
- 实体类用 `Instant` 类型，Jackson 自动序列化 ISO-8601 字符串
- 序列化统一：`application.yml` 设置 `spring.jackson.time-zone: UTC`

**前端约定**：
- API 返回 ISO 字符串，前端通过 `utils/format.ts` 的 `formatDateTime(iso)` 转本地时间
- `formatDateTime` 内部 `dayjs.utc(str).local().format('YYYY-MM-DD HH:mm:ss')`，已注册 utc 插件
- **禁止在页面里直接 `dayjs.utc(iso)`** —— 各页面曾复制过该逻辑，因依赖加载顺序出现 `Invalid Date`，统一收敛到 `utils/format.ts`

### 6.2 大对象存储

> 当请求/响应体超过 1 MB 时，转存 MinIO，DB 只存引用。

后端 `service/OssService.java` 提供：
- `String putOss(String content)` —— 上传字符串，返回 `minio://bucket/path` 路径
- `String getOss(String ref)` —— 通过引用获取原始内容

用法（在 Service 中）：

```java
if (responseBody.length() > 1024 * 1024) {
    String ref = ossService.putOss(responseBody);
    entity.setBodyRef(ref);
    entity.setBody(null);  // 不再存数据库
} else {
    entity.setBody(responseBody);
}
```

前端透明：HTTP 接口返回的字段已经是 OSS 路径，调试回显时调用 `get-oss?ref=xxx` 反向取回。

localdev profile 下 MinIO 不可用时，`OssService` 自动降级到本地文件系统（`OSS_LOCAL_ROOT` 环境变量覆盖，缺省 `${java.io.tmpdir}/apiweb-oss`），无需额外配置。

### 6.3 异步任务调度

```
                          ┌──────────────────┐
                          │  Controller      │
                          │  (提交任务)       │
                          └────────┬─────────┘
                                   │
                                   ▼
                          ┌──────────────────┐
                          │  TaskProducer    │
                          │  (RabbitMQ 投递) │
                          └────────┬─────────┘
                                   │
                       api.ui.perf 三类 exchange
                                   │
                                   ▼
                          ┌──────────────────┐
                          │  *TaskConsumer   │
                          │  @RabbitListener │
                          └────────┬─────────┘
                                   │
                                   ▼
                          ┌──────────────────┐
                          │  CaseRunner      │
                          │  / UiRunner      │
                          │  / PerfRunner    │
                          └────────┬─────────┘
                                   │
                                   ▼
                          ┌──────────────────┐
                          │  t_report /      │
                          │  t_ui_report /   │
                          │  t_perf_report   │
                          └──────────────────┘
```

进度推送（可选）：执行器把进度消息发到 Redis Pub/Sub，前端通过 SSE 订阅。

`localdev` profile 下 MQ 消费者不注册，UI / 性能任务改走 `@Async` 进程内执行（详见 §3.4）。

### 6.4 执行机资源池

```
执行机 ──heartbeat(每30s)──> Redis (TTL=90s)
                                │
                                ▼
                          TaskProducer（派发时）
                                │
                                ├─ 查询 Redis 中 cap=api 且 idle 的节点
                                ├─ 通过 WebSocket 发送任务消息
                                └─ 任务结果回写 MySQL
```

默认情况下，主进程直接执行任务（`PerfExecutionService` 等都跑在主进程 JVM 内）。如需横向扩展，启动独立执行机进程，注册到同一 Redis。

### 6.5 Quartz 集群

- 数据库表 `QRTZ_*` 由 Quartz 自动建（`application.yml` 中 `spring.quartz.job-store-type: jdbc`）
- 多节点部署时，只有一个节点会触发到期的 Trigger（基于数据库行锁）
- Trigger 持久化在 DB，节点重启不丢失

### 6.6 零硬编码（配置外置 + 领域枚举）

> 自提交 `9c908a4` 起，工程不允许出现魔法值：可变参数走 `application.yml` 的 `apiweb.*` 段；参与判断 / 落库 / 传输的状态值必须引用枚举常量。

**后端配置类**（`com/apiweb/config/*.Properties.java`）：

| 类 | 前缀 | 覆盖内容 |
|---|---|---|
| `MqProperties` | `apiweb.mq` | 交换机、结果交换机、队列名、路由键、`enabled` |
| `BootstrapProperties` | `apiweb.bootstrap` | 初始化管理员开关 / 用户名 / 密码 |
| `ExecutorProperties` | `apiweb.executor` | 心跳超时、巡检间隔、Redis key 前缀、默认能力 |
| `EngineProperties` | `apiweb.engine` | UI 等待 / 元素超时、WHILE 上限、性能轮询间隔 |
| `OssProperties` | `apiweb.oss` | bucket、大 body 阈值、本地兜底目录 |
| `CiProperties` | `apiweb.ci` | CI/CD public-base-url、最小触发间隔、Jenkins Remote API 超时 |

所有 `*Properties` 都支持同名环境变量覆盖（`${MQ_ENABLED:false}` 风格），可无缝切换 dev / test / prod。

**领域枚举**（即协议位，**改动等于改协议**）：

| 包 | 枚举 | 取值 |
|---|---|---|
| `engine` | `RunStatus` | pending / running / success / failed / error / skipped |
| `engine` | `ReportStatus` | running / passed / failed / error |
| `engine` | `TriggerType` | manual / schedule / webhook / api |
| `engine` | `ExecutorStatus` | online / busy / offline |
| `engine` | `TaskType` | api / ui / perf |
| `engine` | `FailStrategy` | stop_on_fail / continue_all / retry_then_stop |
| `engine` | `ExecuteMode` | sequential / parallel |
| `engine` | `UiSegment` | setup / test / teardown / error |
| `security` | `Role` | admin / member / viewer（附 `*_CODE` 常量供注解） |
| `report` | `ShareAccessStatus` | success / not_found / revoked / expired / rate_limited / wrong_password / ip_blocked |
| `service` | `ExtractType` | jsonpath / regex / header / cookie / body |
| `engine.step` | `ExtractType.DEFAULT = JSONPATH` | 默认提取方式 |

修改 / 新增枚举时：

1. **必须** 同步前端常量（`client/src/constants/index.ts` 的 `STATUS_LABELS` 等）；
2. **必须** 补一节单测（参考 `server/src/test/java/com/apiweb/engine/EngineEnumsTest.java` / `RoleTest.java` / `ShareAccessStatusTest.java` / `security/LoginErrorCodeTest.java`）；
3. 提交前跑 `mvn test` 确认全绿。

**前端常量集中**（`client/src/constants/index.ts`）：枚举、标签、颜色、分页、HTTP 方法、提取方式、角色 → 全部在唯一来源处声明；时间与耗时格式化统一走 `src/utils/format.ts`。禁止在页面里写字面量。

---

## 7. 扩展点

### 7.1 新增断言类型

1. `engine/AssertEvaluator.java` 添加评估分支
2. `client/src/components/AssertionEditor.vue` 添加 UI 选项

### 7.2 新增提取方式

1. `engine/Extractor.java` 添加字段提取逻辑
2. `client/src/components/ExtractEditor.vue` 添加 UI

### 7.3 新增报告图表

1. 在 `client/src/views/TestReportPage.vue` 引入 ECharts（配 `vue-echarts`）
2. 复用 `report.series` / `report.labels` 数据

### 7.4 新增任务类型

1. `engine/EngineDtos.java` 扩展 `TaskMessage`
2. 新增 `mq/XxxTaskConsumer.java` + `engine/XxxRunner.java`
3. `config/RabbitMQConfig.java` 声明新队列
4. Controller 提交任务时调用 `TaskProducer.send(...)`

### 7.5 新增数据库表

1. 在 `db/migration/` 新建 `V{n}__xxx.sql` 写 DDL（同时把同样的 DDL 追加到 `db/schema.sql`，保证全新库一致）
2. 创建实体（继承 `BaseEntity`）+ Mapper（继承 `BaseMapper<T>`）
3. Service + Controller
4. 重启后端，Flyway 自动执行迁移（无需手工导入）

---

## 8. 测试

### 8.1 后端单元测试

```bash
cd server
mvn test
```

测试目录：`server/src/test/java/`。当前 **18 个测试类 / 102 个用例**（`mvn test` 全绿）：

| 测试类 | 锁死的契约 |
|---|---|
| `architecture/LayeredArchitectureTest` | 分层依赖方向（Controller 不注入 Mapper、Service 不反向依赖 Controller 等） |
| `architecture/NamingConventionTest` | 类 / 包命名规范 |
| `common/CommonContractTest` | `Result` / `ErrorCode` 响应契约 |
| `converter/UserConverterTest` | MapStruct Entity ↔ VO 转换 |
| `engine/EngineEnumsTest` | `RunStatus` / `ReportStatus` / `TriggerType` / `ExecutorStatus` / `TaskType` / `FailStrategy` / `ExecuteMode` / `UiSegment` 的 `getCode()` |
| `engine/CiEnumsTest` | CI 流水线的状态枚举 |
| `engine/step/ExtractTypeTest` | 提取方式 code + JSONPATH 为默认 |
| `engine/step/StepTypeTest` | 步骤类型枚举 code |
| `observability/TraceContextTest` | TraceId MDC 上下文读写 |
| `observability/TraceIdFilterTest` | TraceId 过滤器注入 |
| `observability/SlowSqlInterceptorTest` | 慢 SQL 阈值判定 |
| `report/ShareAccessStatusTest` | 分享访问结果码 |
| `security/RoleTest` | admin / member / viewer code + `*_CODE` 常量 |
| `security/LoginErrorCodeTest` | `1002` ≠ `1006`，且分别锁定码值 |
| `security/AuthInterceptorTest` | 401 响应体格式（`Result(code=1002)` JSON） |
| `security/JwtUtilTest` | JWT 签发 / 解析 / 过期 |
| `service/QualityGateTest` | CI 质量门禁规则语义 |
| `service/UserServiceTest` | 用户业务逻辑（Mock Mapper） |

**推荐新模块测试覆盖**：
- Service 业务方法（`@MockBean` Mapper）
- Util 工具类（`Assertions.*` 工具断言）
- 关键 Controller（`MockMvc`）
- **新枚举 / 新错误码** 必须有 `*Test` 锁死 `getCode()`（否则提交前可能被 `mvn test` 拦截要求补）

### 8.2 前端类型检查 / 规范检查

```bash
cd client
npx vue-tsc --noEmit   # 或 npm run type-check
npm run lint           # ESLint（.ts/.vue）
npm run lint:fix       # 自动修复
```

类型检查覆盖 `.vue` 与 `.ts`，`npm run build` 也会触发（`vue-tsc --noEmit && vite build`）。

### 8.3 前端单元测试

已接入 **Vitest + @vue/test-utils**，当前 4 个测试文件 / 42 个用例：

```bash
cd client
npm run test           # vitest run（单次）
npm run test:watch     # 监听模式
npm run test:coverage  # 覆盖率
```

测试文件放在被测模块旁（`src/utils/*.test.ts`），当前覆盖 `utils/format`、`utils/kvRows`、`utils/treeFilter`、`utils/uid` 等纯函数。新增复杂组件逻辑时，建议把可测逻辑下沉为纯函数再补测。

### 8.4 接口联调

推荐用 Apifox / Postman 调试。已集成 springdoc，可直接导入 OpenAPI：

- 文档 JSON：`http://localhost:8080/v3/api-docs`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/actuator/health`（其余 actuator 端点需认证）

### 8.5 端到端 / 功能回归

仓库根目录提供 `tools_functional_test.py`，按「登录 → 各业务域 CRUD → 执行 → 清理」的顺序跑一遍主流程，可作为发布前的端到端回归：

```bash
# 需后端已在 localhost:8080 运行
python tools_functional_test.py
```

> 历史上曾用于排查 Vue `Transition` 报警导致内容区空白的多根节点扫描脚本改动已随重构下线；如遇页面离开后内容区空白，可直接用 `npm run build` 的 vue-tsc 报错与浏览器控制台的 Vue 警告定位（多半是页面组件返回了多个根节点，需把 `<a-modal>` 等移入根 `<div>`）。

---

## 9. 调试技巧

### 9.1 后端日志级别

`server/src/main/resources/log4j2.xml`：

```xml
<Logger name="com.apiweb" level="DEBUG"/>  <!-- 业务包调到 DEBUG -->
<Logger name="org.apache.jmeter" level="TRACE"/>  <!-- JMeter 内部 -->
```

或运行时通过 Actuator 动态调整（如已引入 `spring-boot-starter-actuator`）。

### 9.2 前端 DevTools

- **Vue Devtools** 浏览器插件：查看组件树、Pinia store、路由
- **Network**：过滤 `api/` 看后端调用
- **Console**：`window.apiweb` 全局对象暴露调试工具（开发模式可用）

### 9.3 JMeter 调试

临时把 `jmeter/log4j2.xml` 中 JMeter 的日志调到 `TRACE`，可看到：
- 每个 Sampler 的入参出参
- 引擎线程池状态
- 断言评估过程

### 9.4 慢 SQL 排查

`application.yml`：

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 打印 SQL
```

或集成 P6Spy 输出真实参数。

---

## 10. 常见开发问题

### Q1: Maven 依赖下载慢

A: 配置国内镜像（编辑 `~/.m2/settings.xml`）：

```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/central</url>
</mirror>
```

### Q2: 前端 `npm install` 报错 node-sass

A: 项目已不依赖 node-sass（用 Vite + 原生 ESM）。如确实需要：删除 `node_modules` + `package-lock.json` 重装。

### Q3: 修改后端代码不生效

A: IDE 默认不会自动重启。`spring-boot-devtools` 可热部署：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-devtools</artifactId>
  <scope>runtime</scope>
  <optional>true</optional>
</dependency>
```

### Q4: Vite 代理 404

A: 检查 `vite.config.ts` 中 `server.proxy` 配置：

```ts
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true }
}
```

### Q5: TypeScript 类型报错无法运行

A: Vite 默认会跳过类型检查直接打包。如需严格：

```bash
npm run build      # 会触发 vue-tsc 类型检查
```

或在 `vite.config.ts` 中开启 `esbuild: { tsconfigRaw: { compilerOptions: { strict: true } } }`。

### Q6: 跨域（CORS）报错

A: 后端 `config/WebConfig.java` 已配置 CORS 放行 `*`。如仍报错：

- 检查请求是否带了 `Authorization` 头（预检请求 OPTIONS 必须通过）
- 浏览器禁用第三方 cookie 后，前端不能用 `withCredentials: true`

### Q7: 部署后中文乱码

A: 三处需统一 UTF-8：
- MySQL：`CHARACTER SET utf8mb4`（schema.sql 已配置）
- JDBC URL：`characterEncoding=utf8mb4`（application.yml 已配置）
- Tomcat / Spring Boot：`server.servlet.encoding.charset: UTF-8`（已配置）

### Q8: 嵌入式 JMeter 启动失败

A: 检查 `logs/backend.log`，常见原因：
- 找不到 `jmeter.properties` 等资源 —— 确认 `resources/jmeter/` 4 个文件齐全
- log4j2 配置冲突 —— 确认 Spring Boot 已切换到 `spring-boot-starter-log4j2`
- 依赖冲突 —— 排除 `spring-boot-starter-logging`，避免 logback / log4j2 双日志框架

---

## 附录 A：核心源码导读

| 想了解 | 看这些文件 |
|---|---|
| HTTP 请求如何执行 | `server/src/main/java/com/apiweb/engine/HttpExecutor.java` |
| 变量如何替换 | `server/src/main/java/com/apiweb/engine/VariablesResolver.java` |
| 断言如何评估 | `server/src/main/java/com/apiweb/engine/AssertEvaluator.java` |
| JMeter 如何集成 | `server/src/main/java/com/apiweb/engine/JmeterBootstrapper.java` |
| JWT 如何校验 | `server/src/main/java/com/apiweb/security/AuthInterceptor.java` |
| 审计如何记录 | `server/src/main/java/com/apiweb/audit/AuditAspect.java` |
| 接口报告如何生成 | `server/src/main/java/com/apiweb/service/ReportGenerationService.java` |
| 步骤如何同步落库 | `server/src/main/java/com/apiweb/controller/CaseController.java`（`syncStepsFromJson`） |
| 场景如何执行 | `server/src/main/java/com/apiweb/controller/ScenarioController.java` |
| .jmx 导入导出 | `server/src/main/java/com/apiweb/service/JmxImportExportService.java` |
| 三栏工作台如何联动 | `client/src/views/Workbench.vue` + `client/src/components/workbench/*.vue` |
| Monaco 如何集成 | `client/src/components/Monaco.vue` + `client/src/utils/monacoWorkers.ts` |
| 虚拟滚动树如何实现 | `client/src/components/VirtualTree.vue` |
| DTO/VO 如何转换 | `server/src/main/java/com/apiweb/converter/*.java`（MapStruct）+ `server/src/main/java/com/apiweb/dto/**`、`vo/**` |
| 链路追踪 / 慢 SQL | `server/src/main/java/com/apiweb/observability/*.java` |
| 架构约束如何保证 | `server/src/test/java/com/apiweb/architecture/*.java`（ArchUnit） |

## 附录 B：常用命令速查

```bash
# 后端
mvn spring-boot:run                                # 开发模式启动
mvn clean package -DskipTests                      # 构建 jar
mvn test                                           # 跑单元测试

# 前端
npm run dev                                        # 开发服务器
npm run build                                      # 生产构建（含 vue-tsc 类型检查）
npx vue-tsc --noEmit                               # 仅类型检查
npm run lint                                       # ESLint 检查
npm run test                                       # Vitest 单元测试

# 数据库
docker exec -i apiweb-mysql mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql   # 全新库建表
# 结构变更走 Flyway：在 server/src/main/resources/db/migration/ 新增 V{n}__xxx.sql，重启后端自动执行
docker exec apiweb-mysql mysqldump -uapiweb -papiweb123 api_web > backup.sql

# Redis
docker exec apiweb-redis redis-cli                  # 进入 Redis CLI
docker exec apiweb-redis redis-cli ping             # 健康检查

# RabbitMQ
docker exec apiweb-rabbitmq rabbitmqctl status
docker exec apiweb-rabbitmq rabbitmqctl list_queues

# 一键
./scripts/start.sh              # 启动整套
./scripts/stop.sh               # 停止整套
./scripts/logs.sh               # 看日志
./scripts/reset-db.sh           # 重置数据库
./scripts/build.sh              # 构建产物
./scripts/deploy.sh             # 生产部署
```
