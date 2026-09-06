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
│   │   ├── components/                 # 公共组件
│   │   │   └── workbench/              # 三栏工作台专用面板
│   │   ├── layouts/                    # 布局（带侧栏的 AppLayout）
│   │   ├── router/                     # vue-router 配置
│   │   ├── stores/                     # Pinia 状态管理
│   │   ├── types/                      # TypeScript 类型定义
│   │   ├── views/                      # 页面组件（按路由对应）
│   │   ├── App.vue                     # 根组件
│   │   └── main.ts                     # 入口文件
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts                  # Vite 配置（含 dev server proxy）
│
├── server/                             # 后端工程（Spring Boot 3）
│   ├── src/main/
│   │   ├── java/com/apiweb/
│   │   │   ├── ApiWebApplication.java  # 启动类
│   │   │   ├── audit/                  # 审计日志（AOP 自动落库）
│   │   │   ├── common/                 # 通用：Result、错误码、异常、基类
│   │   │   ├── config/                 # 配置：MyBatis-Plus / Redis / RabbitMQ / MinIO / 初始化
│   │   │   ├── controller/             # RESTful Controller 层
│   │   │   ├── engine/                 # 执行引擎核心（变量/断言/HTTP/场景/JMeter）
│   │   │   ├── entity/                 # MyBatis-Plus 实体（每张表一个）
│   │   │   ├── mapper/                 # MyBatis-Plus Mapper 接口
│   │   │   ├── mq/                     # RabbitMQ 生产者/消费者
│   │   │   ├── security/               # JWT 鉴权、UserContext、CurrentUser
│   │   │   ├── service/                # 业务服务层
│   │   │   └── util/                   # 工具类
│   │   └── resources/
│   │       ├── db/schema.sql           # MySQL 初始化脚本
│   │       ├── jmeter/                 # 嵌入式 JMeter 配置（4 个）
│   │       ├── log4j2.xml              # Spring Boot 日志
│   │       └── application.yml         # Spring 配置（含多 profile）
│   └── pom.xml                         # Maven 配置
│
├── docs/                               # 用户文档
│   ├── INSTALL.md                      # 安装文档
│   ├── USER_GUIDE.md                   # 操作手册
│   └── DEVELOPER.md                    # 本文件
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
| JWT (jjwt) | 0.12.5 | 无状态认证 |
| MinIO Java SDK | 8.5.9 | 对象存储 |
| Apache JMeter | 5.6.3 | 嵌入式性能测试引擎 |
| Selenium Java | 4.19.1 | UI 自动化 |
| Hutool | 5.8.27 | Java 工具集 |
| Lombok | 1.18.x | 代码简化 |
| Log4j2 | 2.22 | 日志（替换默认 logback） |

### 2.2 前端

| 技术 | 版本 | 作用 |
|---|---|---|
| Vue | 3.4+ | 框架（Composition API） |
| TypeScript | 5.x | 类型 |
| Vite | 5.x | 构建 / 开发服务器 |
| Ant Design Vue | 4.x | 组件库 |
| Pinia | 2.x | 状态管理 |
| vue-router | 4.x | 路由 |
| vue-draggable-plus | - | 拖拽（基于 SortableJS） |
| monaco-editor | 0.45.x | 代码编辑器（脚本编辑） |
| @antv/g2 / G2Plot | - | 报告图表 |
| dayjs | - | 时间处理（含 UTC） |
| axios | 1.x | HTTP 客户端 |

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

### 3.4 启动后端（开发模式）

```bash
cd server
mvn spring-boot:run
```

或用 IDE（IntelliJ IDEA）打开 `server/` 作为 Maven 项目，直接运行 `ApiWebApplication` 主类。

后端默认端口 `8080`，启动后可访问 http://localhost:8080/api。

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

```
com.apiweb.{module}/
├── entity/        数据实体（与表对应，TableName 注解）
├── mapper/        MyBatis-Plus Mapper 接口
├── service/       业务服务（接口 + 实现）
├── controller/    REST 控制器
├── dto/           业务 DTO（请求/响应）
└── ...
```

每个 Controller 类加 `@RestController` + `@RequestMapping("/api/xxx")`。

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

4. **创建 Service** `service/BugService.java`：

```java
@Service
@RequiredArgsConstructor
public class BugService {
    private final BugMapper bugMapper;

    public BugEntity create(BugEntity bug) {
        bug.setId(UUID.randomUUID().toString().replace("-", ""));
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

5. **创建 Controller** `controller/BugController.java`：

```java
@RestController
@RequestMapping("/api/bugs")
@RequiredArgsConstructor
public class BugController {
    private final BugService bugService;

    @GetMapping
    public Result<List<BugEntity>> list() {
        return Result.ok(bugService.list());
    }

    @PostMapping
    public Result<BugEntity> create(@RequestBody @Valid BugEntity bug) {
        return Result.ok(bugService.create(bug));
    }

    @GetMapping("/{id}")
    public Result<BugEntity> get(@PathVariable String id) {
        return Result.ok(bugService.get(id));
    }
}
```

6. **重启后端**，调用 `curl http://localhost:8080/api/bugs` 验证。

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

业务异常抛 `BizException(ErrorCode.XXX, "附加信息")`，由 `GlobalExceptionHandler` 统一转换为 `Result`。

### 4.4 鉴权

需要登录的接口走 JWT 流程：

1. `POST /api/auth/login` 获取 token
2. 后续请求 Header 携带 `Authorization: Bearer <token>`
3. `AuthInterceptor` 校验 token，解析出 userId，写入 `UserContext`（ThreadLocal）
4. 业务代码通过 `UserContext.getCurrentUserId()` 获取当前用户

### 4.5 审计日志

在 Controller 方法上加 `@AuditLog("操作描述")`，AOP 会自动记录：

```java
@PostMapping
@AuditLog("创建用例")
public Result<CaseEntity> create(@RequestBody CaseEntity entity) {
    return Result.ok(caseService.create(entity));
}
```

审计字段：操作人、操作时间、IP、操作类型、目标实体 ID、变更前后值。

### 4.6 RabbitMQ 异步任务

发送任务（`mq/TaskProducer.java`）：

```java
rabbitTemplate.convertAndSend(RabbitMQConfig.API_EXCHANGE, "api.task", message);
```

消费任务（`mq/ApiTaskConsumer.java`）：

```java
@RabbitListener(queues = RabbitMQConfig.API_QUEUE)
public void onMessage(EngineDtos.TaskMessage message) {
    caseRunner.execute(message);
}
```

新增队列：编辑 `config/RabbitMQConfig.java`，声明 `Queue` / `Exchange` / `Binding` Bean。

### 4.7 Quartz 定时任务

1. 实现 `Job` 接口：`job/MyJob.java implements Job`
2. 创建 Trigger / JobDetail：`service/ScheduleService.scheduleCron(...)`
3. 支持集群模式（多个节点同时跑只会触发一次）

### 4.8 嵌入式 JMeter 扩展

如需新增 JMeter 节点类型，编辑 `service/PerfExecutionService.java` 中的 `JmxBuilder`：

```java
// 示例：添加 HTTP Cookie Manager
sb.append("      <CookieManager guiclass=\"CookiePanel\" testclass=\"CookieManager\" testname=\"Cookie Manager\">\n");
sb.append("        <boolProp name=\"CookieManager.clearEachIteration\">false</boolProp>\n");
sb.append("      </CookieManager>\n      <hashTree/>\n");
```

字段对应关系参考 JMeter 源码：`TestElementGUI.getStringValue()` 输出的 `.jmx` XML。

---

## 5. 前端开发指南

### 5.1 目录约定

- **views/**：每个路由对应一个 Vue 文件
- **components/**：可复用组件
- **api/**：按域拆分的 API 调用函数
- **stores/**：Pinia store
- **router/**：路由表
- **types/**：TS 类型定义（对应后端 DTO）

### 5.2 API 调用约定

所有请求通过 `src/api/client.ts` 包装的 axios 实例：

```ts
import { http } from '@/api/client';

export const listCases = (projectId: string) =>
  http.get<CaseEntity[]>('/api/cases', { params: { projectId } });
```

响应统一解包 `data`：

```ts
// client.ts 中已设置 interceptor
http.interceptors.response.use(resp => resp.data.data);
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

`views/Workbench.vue` 是测试用例编辑的核心，由 3 个子面板组成：

- `components/workbench/CaseLibraryPanel.vue` —— 左侧模块树
- `components/workbench/CaseEditorPanel.vue` —— 中间用例编辑（表单 + Monaco）
- `components/workbench/ApiManagerPanel.vue` —— 右侧接口管理

子面板通过 `props / emits` 与父组件通信，避免 Pinia store 滥用。

### 5.6 拖拽

使用 `vue-draggable-plus`：

```vue
<VueDraggable v-model="stepList" :animation="150" handle=".drag-handle">
  <div v-for="step in stepList" :key="step.id" class="step-item">
    <DragOutlined class="drag-handle" />
    {{ step.name }}
  </div>
</VueDraggable>
```

### 5.7 Monaco Editor

封装在 `components/Monaco.vue`：

```vue
<Monaco v-model="code" language="javascript" height="300px" />
```

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
- API 返回 ISO 字符串，前端用 `dayjs.utc(str).local().format('YYYY-MM-DD HH:mm:ss')` 转本地

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

---

## 7. 扩展点

### 7.1 新增断言类型

1. `engine/AssertEvaluator.java` 添加评估分支
2. `client/src/components/AssertionEditor.vue` 添加 UI 选项

### 7.2 新增提取方式

1. `engine/Extractor.java` 添加字段提取逻辑
2. `client/src/components/ExtractEditor.vue` 添加 UI

### 7.3 新增报告图表

1. 在 `client/src/views/TestReportPage.vue` 引入 `@antv/g2` 或 G2Plot
2. 复用 `report.series` / `report.labels` 数据

### 7.4 新增任务类型

1. `engine/EngineDtos.java` 扩展 `TaskMessage`
2. 新增 `mq/XxxTaskConsumer.java` + `engine/XxxRunner.java`
3. `config/RabbitMQConfig.java` 声明新队列
4. Controller 提交任务时调用 `TaskProducer.send(...)`

### 7.5 新增数据库表

1. `db/schema.sql` 追加 DDL
2. 创建实体（继承 `BaseEntity`）+ Mapper（继承 `BaseMapper<T>`）
3. Service + Controller
4. 重新执行 `docker exec apiweb-mysql mysql ... < schema.sql` 或用 Flyway/Liquibase 做迁移

---

## 8. 测试

### 8.1 后端单元测试

```bash
cd server
mvn test
```

测试目录：`server/src/test/java/`。推荐覆盖：
- Service 业务方法（Mock Mapper）
- Util 工具类
- 关键 Controller（MockMvc）

### 8.2 前端单元测试（待补充）

`vitest` + `@vue/test-utils` 尚未接入。如需添加：

```bash
cd client
npm install -D vitest @vue/test-utils
```

### 8.3 接口联调

推荐用 Apifox / Postman 调试。导入 OpenAPI：访问 `http://localhost:8080/v3/api-docs`（如已集成 springdoc）。

### 8.4 端到端测试（待补充）

Playwright / Cypress 暂未接入。推荐补充路径：
- 登录 → 新建用例 → 调试 → 加入场景 → 执行 → 看报告

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
| 三栏工作台如何联动 | `client/src/views/Workbench.vue` + `client/src/components/workbench/*.vue` |
| Monaco 如何集成 | `client/src/components/Monaco.vue` |
| 虚拟滚动树如何实现 | `client/src/components/VirtualTree.vue` |

## 附录 B：常用命令速查

```bash
# 后端
mvn spring-boot:run                                # 开发模式启动
mvn clean package -DskipTests                      # 构建 jar
mvn test                                           # 跑单元测试

# 前端
npm run dev                                        # 开发服务器
npm run build                                      # 生产构建
npm run type-check                                 # TypeScript 检查（待补充）

# 数据库
docker exec -i apiweb-mysql mysql -uapiweb -papiweb123 api_web < server/src/main/resources/db/schema.sql
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
