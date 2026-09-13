# Api-Web 操作手册

> 本文档面向 **测试工程师 / 业务使用者**，介绍 Api-Web 的功能模块与日常使用流程。
> 阅读对象无需具备编码能力，只需熟悉 Web 浏览器操作。

---

## 目录

1. [快速上手](#1-快速上手)
2. [接口自动化](#2-接口自动化)
3. [场景编排](#3-场景编排)
4. [测试任务与报告](#4-测试任务与报告)
5. [性能测试（JMeter）](#5-性能测试jmeter)
6. [UI 自动化](#6-ui-自动化)
7. [环境与全局变量](#7-环境与全局变量)
8. [用户与权限](#8-用户与权限)
9. [审计日志](#9-审计日志)
10. [执行机资源池](#10-执行机资源池)
11. [CI/CD 流水线（Jenkins）](#11-cicd-流水线jenkins)
12. [常见使用问题](#12-常见使用问题)

---

## 1. 快速上手

### 1.1 登录

打开 http://localhost:5173 ，输入默认账号：

| 字段 | 值 |
|---|---|
| 账号 | admin |
| 密码 | admin@123 |

首次登录建议立即修改密码（右上角头像 → 个人设置）。

> **生产部署提示**：登录页默认不预填演示账号（`VITE_PREFILL_DEMO_ACCOUNT=false`），避免向最终用户泄露默认口令。本地演示可在 `client/.env.development` 中打开。

### 1.2 主界面布局

```
┌────────┬──────────────────────────────────────────────────┐
│        │  顶部栏：项目选择 | 快捷键帮助 | 用户菜单           │
│ 左侧   ├──────────────────────────────────────────────────┤
│ 侧边栏 │              主工作区                            │
│ (菜单) │                                                  │
│        │                                                  │
└────────┴──────────────────────────────────────────────────┘
```

左侧为深色侧边栏，共 6 个一级菜单（菜单过长时可上下滚动）：

| 一级菜单 | 二级菜单 |
|---|---|
| 接口自动化 | 接口定义 / 接口用例 / 测试执行 / 测试报告 |
| UI 自动化 | UI 用例 / UI 用例执行 / 测试报告 |
| 性能测试 | 创建用例 / 测试报告 |
| 环境配置 | 环境管理 / 全局变量 / 变量与模块 / 回收站 |
| 用户管理 | （仅管理员可见） |
| 操作日志 | （仅管理员可见） |

顶部栏右侧可切换当前项目、查看快捷键、进入个人中心（修改密码/退出登录）。

### 1.3 创建第一个项目

1. 进入 **项目管理 → 新建项目**
2. 输入项目名称（如 "电商订单系统"）和描述
3. 创建后会自动切换到该项目，左侧菜单显示项目内功能
4. 进入 **环境管理**，创建第一个环境（开发/测试/预发/生产），每个环境独立配置 host、headers、variables

---

## 2. 接口自动化

> 模块：左侧菜单「**接口自动化**」

### 2.1 模块树管理

接口用例按 **模块树** 组织（无限层级），支持拖拽排序。

- **新建模块**：右键根目录 → 新建模块
- **重命名**：双击模块名
- **删除**：右键 → 删除（子模块和用例会被一并删除或移动到回收站）
- **拖拽**：拖动模块到目标位置即可调整结构

### 2.2 新建接口定义

**接口定义** 用于维护可复用的接口元数据（URL、方法、headers、参数描述）。

1. 在工作台左侧点击「**接口定义**」标签
2. 右上角点击「**新建接口**」
3. 填写表单：

| 字段 | 必填 | 说明 |
|---|---|---|
| 名称 | ✅ | 用例内显示的名字 |
| 协议 | ✅ | HTTP / HTTPS |
| 方法 | ✅ | GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS |
| URL | ✅ | 支持 `{{baseUrl}}/api/path` 模板，运行时替换环境变量 |
| 请求头 | | JSON，例如 `{"Content-Type": "application/json"}` |
| 请求体 | | POST/PUT 时填写，JSON / form / 文本皆可 |
| 参数描述 | | Swagger 风格，自动生成 Mock 数据 |

4. 保存。接口定义可被多个用例引用。

**批量导入**（接口管理面板底部）：

- **Excel 导入**：上传 `.xlsx/.xls`，格式为两列——第一列「请求方式」（GET/POST/PUT/DELETE/PATCH），第二列「URL」。系统解析后批量创建接口。
- **Swagger 导入**：粘贴 Swagger JSON，批量导入接口定义。

### 2.3 新建用例

1. 在模块树选中目标模块
2. 点击「**新建用例**」
3. 切换到「**用例编辑**」标签

#### 2.3.1 工作台三栏结构

进入「接口自动化 → 接口用例」，页面顶部为配置栏（设置全局变量 / 调试记录 / 回收站），下方三栏：

```
┌─────────────┬─────────────────────────────┬─────────────────┐
│             │  用例名称 + 脚本/调试记录 Tab │                 │
│   用例库    │  ├ 前置步骤                  │   接口管理      │
│  (模块树+   │  ├ 测试步骤（可拖拽排序）     │  (目录树+Excel  │
│   用例列表) │  └ 后置步骤                  │   导入)         │
└─────────────┴─────────────────────────────┴─────────────────┘
```

中间「编写用例」面板要点：

- 用例名称下方「**脚本 / 调试记录**」两个入口
- 脚本分为「**前置步骤 / 测试步骤 / 后置步骤**」三个分区，各分区内可拖拽排序
  - 前置步骤：管理该接口用例的前置条件
  - 测试步骤：管理用例步骤，支持多个接口，可拖拽调整顺序
  - 后置步骤：执行完后清理测试数据等操作
- 步骤类型：HTTP 请求 / 等待时间 / IF 条件 / FOR 循环 / WHILE 循环 / JS 脚本
- 每条步骤左侧可**禁用/启用**；HTTP 步骤内嵌请求头 / Query / Body / 断言 / 提取五个子标签
- 步骤卡片**鼠标右键**可复制 / 粘贴该步骤

#### 2.3.2 编写请求参数

**Headers**（键值对编辑器）：
- 添加：点击「+ 添加」→ 输入 Key（如 `Authorization`）和 Value（如 `Bearer {{token}}`）
- 变量语法：`{{varName}}` 在执行时从环境/全局变量/用例变量取值
- 引用前序响应：`${response.header.X-Token}` 或 `${response.body.data.token}`

**Query 参数**：与 Headers 类似，拼接到 URL。

**Body**：支持 4 种格式
- `none`：无 body
- `json`：JSON 编辑器，自动校验语法
- `form`：键值对列表
- `raw`：纯文本（XML、text/plain 等）

**前置脚本**（Pre-request Script）：在请求前执行的 JS 代码，常用于：
- 生成时间戳、UUID
- 加密/签名
- 修改变量

```js
// 前置脚本示例
apiweb.variables.set('timestamp', Date.now());
apiweb.variables.set('nonce', apiweb.utils.uuid());
```

**后置脚本**（Post-response Script）：响应返回后执行。

#### 2.3.3 断言

点击「**断言**」标签，添加多条断言。支持的断言类型：

| 类型 | 表达式 | 示例 |
|---|---|---|
| 状态码等于 | `${response.status}` | 200 |
| JSONPath 等于 | `${response.body.code}` | 0 |
| JSONPath 包含 | `${response.body.message}` | "success" |
| 响应时间小于 | `${response.elapsed}` | 500（毫秒） |
| Header 包含 | `${response.header.Content-Type}` | "application/json" |
| 正则匹配 | `${response.body}` | `"orderId":\d+` |

任意一条断言失败则用例失败。

#### 2.3.4 提取（变量提取）

点击「**提取**」标签，把响应中的字段保存为变量供后续步骤使用：

| 提取方式 | 配置 |
|---|---|
| JSONPath | 路径如 `$.data.userId`，保存为变量 `userId` |
| 正则 | 表达式如 `"orderId":"(\d+)"`，保存为 `lastOrderId` |
| Header | Header 名如 `X-Token`，保存为 `authToken` |

提取的变量在当前场景的所有后续步骤中可用。

#### 2.3.5 调试用例

选中用例后点击「**调试**」按钮，自动弹出「**调试选项**」弹窗：

1. 选择**调试环境**（用例根据所选环境变量执行）
2. 支持对该环境的变量**新增 / 修改 / 删除**，也可**新建 / 删除**整个环境
3. 点击「**开始调试**」执行，结果自动保存到「调试记录」标签页

调试结果展示请求、响应、断言结果与提取变量；可在「脚本 / 调试记录」Tab 中切换查看历史记录。

### 2.4 用例版本管理

每次「保存」会生成一个新版本（v1 → v2 → ...）。

- **查看版本**：右键用例 → 版本历史
- **对比版本**：选两个版本 → 对比
- **回滚**：选中旧版本 → 设为当前

### 2.5 用例评审

多人协作时，可发起评审流程：

1. 右键用例 → 「**提交评审**」
2. 选择评审人（可多选）
3. 评审人在「**待办**」中查看并通过/驳回
4. 全部通过后，用例状态变为「**已通过**」，可加入场景执行

### 2.6 回收站

被删除的用例不会立即物理删除，进入回收站 30 天后自动清理。在「**回收站**」可恢复或永久删除。

---

## 3. 场景编排

> 模块：左侧菜单「**场景**」

场景 = 多个用例的编排（顺序 / 条件分支 / 循环）。

### 3.1 新建场景

1. 「场景」→ 「**新建场景**」
2. 拖拽左侧用例库中的用例到中间步骤列表
3. 步骤顺序用 vuedraggable 拖拽调整

### 3.2 步骤类型

每个步骤可以配置：

| 步骤类型 | 用途 |
|---|---|
| 用例 | 引用一个已保存的接口用例 |
| 条件 | If/Else 分支，基于前置步骤的断言结果 |
| 循环 | 对一组参数循环执行（数据驱动） |
| 等待 | 固定时长 sleep（毫秒） |
| 脚本 | 执行 JS 代码片段 |
| 事务 | 把多个步骤作为一个事务统计响应时间 |

### 3.3 数据驱动

把场景步骤标记为「循环」，导入 CSV 文件作为参数池：

```csv
username,password,expected
user01,pwd123,success
user02,pwd456,success
user03,wrongpwd,fail
```

执行时场景会对每行数据各执行一次，并产出 N 个子报告。

---

## 4. 测试任务与报告

### 4.1 创建测试任务

1. 「测试任务」→ 「**新建任务**」
2. 选择场景（可多选）
3. 选择环境
4. 选择触发方式：
   - **手动**：保存后手动点「执行」
   - **定时**：Quartz Cron 表达式，如 `0 0 2 * * ?`（每天凌晨 2 点）
   - **流水线**：被其他系统通过 Webhook 触发

### 4.2 任务执行

任务提交后异步执行：

1. 任务进入 RabbitMQ 队列 `apiweb.task.api.queue`
2. 后端消费者从队列取出，调用 `CaseRunner`
3. 执行机（也可能在主进程内）按步骤执行用例
4. 实时进度通过 SSE 推送到前端（每完成一个步骤推送一次）

> 说明：在 localdev 单机模式（未启用 RabbitMQ）下，UI 与性能任务会由后端进程内直接执行，无需 MQ，仍会正常生成报告。接口任务与场景始终在主进程内执行。

### 4.3 报告查看

任务执行完成后生成报告，包含：

- **概览**：总用例数 / 通过 / 失败 / 错误 / 跳过
- **耗时分布**：P50 / P90 / P99 响应时间
- **失败原因聚合**：相同错误聚合展示
- **明细列表**：每条用例的执行结果，可点击展开请求/响应/断言/提取

报告支持导出 **HTML**（默认）和 **PDF**（ECharts 渲染图表）。报告列表右侧「导出」菜单可任选其一；报告详情页工具栏也有 PDF 按钮。

三类报告分别对应：接口自动化 → 接口报告；UI 自动化 → UI 报告；性能测试 → 性能报告。三者均按当前项目（projectId）过滤展示，若页面无数据，请先确认该报告对应的执行确实已完成（而非仍在 pending/执行中）。报告状态枚举（`passed` / `failed` / `error` / `running`）由前端 `STATUS_LABELS` 映射为中文标签展示。

---

## 5. 性能测试（JMeter）

> 模块：左侧菜单「**性能测试**」
> **无需单独安装 JMeter** —— 平台已嵌入 Apache JMeter 5.6.3。

### 5.1 新建性能用例

1. 「性能用例」→ 「**新建性能用例**」
2. 填写基本信息：

| 字段 | 说明 |
|---|---|
| 名称 | 用例标识 |
| 并发线程数 | 同时模拟的用户数 |
| 加压时间 | Ramp-up 秒数，0 表示瞬间加压 |
| 持续时间 | 压测总时长（秒），0 表示按循环次数 |
| 循环次数 | 每个线程执行的循环数 |
| 加压模式 | `fixed`（固定并发） / `stepping`（阶梯加压，5 级） |
| 错误处理 | `continue` / `startnext` / `stopthread` / `stoptest` |
| 思考时间 | 步骤间 sleep（毫秒） |
| 用户变量 | 键值对列表，注入 JMeter 的 User Defined Variables |
| HTTP 步骤 | 多个 host/port/path/method/body |

### 5.2 触发压测

点击用例列表「**执行**」按钮 → 选择压测报告名 → 提交到 RabbitMQ `apiweb.task.perf.queue` → 后端消费者调用嵌入式 JMeter 执行。

### 5.3 性能报告

报告页包含：

- **TPS / 响应时间** 双 Y 轴折线图
- **响应时间分布** 柱状图（按 label 分组）
- **百分位统计**：min / avg / P90 / P95 / P99 / max
- **错误率 TOP10**：按错误消息聚合

---

## 6. UI 自动化

> 模块：左侧菜单「**UI 自动化**」

> ⚠️ 执行机需预装 Chrome 浏览器（版本匹配 ChromeDriver）。
> 若 Chrome 与默认 ChromeDriver 版本不一致，可由管理员通过环境变量 `WEBDRIVER_CHROME_DRIVER` 指定匹配版本的 chromedriver 路径（详见 INSTALL.md Q8）。

### 6.1 UI 用例结构

UI 用例 = 一组步骤，每步包含：
- **动作**：click / input / hover / select / assert_text / screenshot
- **定位方式**：CSS Selector / XPath / ID / Name / Link Text
- **目标元素**：定位表达式
- **值**：input 动作时填写的内容
- **断言**：可选，断言元素的文本/属性/可见性

### 6.2 UI 场景

UI 场景 = 多个 UI 用例的有序编排，与接口场景用法相同。

### 6.3 UI 报告

每个用例执行后自动截图，可在报告中查看执行轨迹（按步骤顺序展示截图）。

---

## 7. 环境与全局变量

### 7.1 环境

一个项目可有多个环境（开发 / 测试 / 预发 / 生产）。每个环境配置：

- **基础 URL**：如 `https://dev-api.example.com`
- **通用 Headers**：所有用例默认携带
- **环境变量**：键值对
- **数据库连接**：可选，用于 SQL 断言

切换环境：顶部导航右侧下拉框。

### 7.2 全局变量

跨项目共享的变量（如第三方 API key），在「**全局变量**」菜单管理。

变量优先级：**用例变量 > 环境变量 > 全局变量**。

---

## 8. 用户与权限

### 8.1 内置角色

平台固定三种角色（由后端 `Role` 枚举驱动，前端据其控制按钮可见性）：

| 角色代码 | 显示名 | 权限 |
|---|---|---|
| `admin` | 管理员 | 全部权限（含用户管理、审计日志、系统配置） |
| `member` | 业务用户 | 项目内读写（用例 / 场景 / 任务 / 报告） |
| `viewer` | 只读用户 | 查看所有数据，无修改权限（前端自动禁用写按钮） |

### 8.2 用户管理（管理员）

「系统管理 → 用户管理」可创建 / 禁用 / 重置密码用户。

### 8.3 错误码速查（前端弹错时定位）

| 弹错内容 | 码值 | 说明与处理建议 |
|---|---|---|
| `登录已过期，请重新登录` | `1002 UNAUTHORIZED` | token 过期或失效 → 系统已自动跳登录页，重新登录即可 |
| `用户名或密码错误` | `1006 LOGIN_FAILED` | 仅登录时出现，不会清登录态；检查账号或重置密码 |
| `权限不足` | `1003 FORBIDDEN` | 当前角色无权操作 → 联系管理员提升为 admin / member |
| `xxx 已被使用` / `xxx 不存在` | `1005 CONFLICT` / `1004 NOT_FOUND` | 名称冲突或资源不存在 → 改用唯一名称或刷新列表 |
| `xxx: 字段不能为空` | `1001 BAD_REQUEST` | 表单校验失败 → 按提示补全 |

错误码契约受后端 `LoginErrorCodeTest` / `AuthInterceptorTest` 与前端 `constants/index.ts` 的 `API_CODE` 常量双向锁死，发现提示文本与上表不符请通过 GitHub Issue 反馈。

---

## 9. 审计日志

所有写操作（新建/修改/删除）自动落库，可在「系统管理 → 审计日志」查询：

- 操作人、时间、IP
- 操作类型（CREATE/UPDATE/DELETE）
- 目标实体
- 变更前后值（Diff 展示）

---

## 10. 执行机资源池

> 高级特性：主进程可直接执行任务（默认行为）；如果想把执行和调度分离，可启动独立的执行机。

执行机通过 HTTP 心跳注册到平台：

```
POST /api/executors/heartbeat
{
  "name": "executor-01",
  "ip": "10.0.1.5",
  "capabilities": ["api", "ui", "perf"],
  "maxConcurrency": 10
}
```

平台每 30s 扫描心跳超时（默认 90s）的节点，从资源池剔除。

---

## 11. CI/CD 流水线（Jenkins）

接口自动化、UI 自动化、性能测试都可以接入 Jenkins：把「执行目标 + 质量门禁」配成一条流水线，
Jenkins 构建时触发它，构建结果由质量门禁决定成功或失败。

### 11.1 前置：配置 Jenkins 服务器（管理员）

进入 **CI/CD → Jenkins 服务器 → 新增服务器**：

| 字段 | 说明 |
| --- | --- |
| 名称 | 便于区分多套 Jenkins（如「测试环境 Jenkins」） |
| Jenkins 地址 | 形如 `http://jenkins.example.com:8080`，结尾不要带 `/` |
| 认证方式 | `Basic`（用户名 + API Token）或 `Bearer`（仅 Token） |
| 用户名 / API Token | Jenkins 用户 → 设置 → API Token。Token 加密存储，不会回显 |

保存后可用 **测试连接** 验证地址与凭据（成功会显示 Jenkins 版本号）；**Job 列表** 用于确认能否读到任务。

### 11.2 新建流水线

进入 **CI/CD → 流水线 → 新建流水线**：

1. **测试类型**：接口自动化 / UI 自动化 / 性能测试
2. **执行目标**：
   - 接口自动化 → 测试任务（`t_test_task`）
   - UI 自动化 → UI 场景或 UI 用例
   - 性能测试 → 性能用例
3. **执行方式**
   - **平台执行**：平台直接跑用例，生成报告并按门禁判定
   - **Jenkins 执行**：平台调用 Jenkins Remote API 触发 Job，跟踪构建结果（需选择服务器与 Job）
4. **质量门禁**：点击「添加规则」配置，例如：
   - 失败率 > 5%
   - 通过率 < 95%
   - P95 响应时间 > 2000ms
   - 错误率 > 1%（性能测试常用）
   - 吞吐量 < 50 tps（性能测试常用）

> 规则语义：**指标值 操作符 阈值 成立即判定违规**，流水线状态变为「未通过」，CI 构建随之失败。

### 11.3 在 Jenkins 中使用

流水线保存后点击 **CI Token**，平台会生成触发地址与 curl 示例；点击 **Jenkinsfile** 可一键复制
Jenkins Pipeline 脚本。典型用法：

- **方式一（推荐）**：新建 Jenkins「Pipeline」任务，把生成的 Jenkinsfile 粘贴进去。
  脚本会自动：触发流水线 → 轮询执行结果 → 门禁未通过时让构建失败。
- **方式二（Shell 步骤）**：在任意 Jenkins 任务的 Shell 中粘贴 curl 命令触发，自行根据返回状态判断。

也可在命令行直接触发：

```bash
# 触发（立即返回 runId）
curl -s -X POST 'http://<平台地址>/api/v1/webhook/ci/pipelines/trigger?token=<CI_TOKEN>&triggerBy=jenkins-1&wait=false'

# 轮询结果，status 变为 success/failed/error 即结束
curl -s 'http://<平台地址>/api/v1/webhook/ci/runs/<runId>?token=<CI_TOKEN>'
```

返回体中的 `gatePassed`（1=通过 / 0=未通过）即质量门禁结论，`summary` 是本次执行统计。

### 11.4 配置项（运维）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `apiweb.ci.public-base-url` | `http://localhost:8080` | 生成 Jenkinsfile / curl 示例时使用的平台外网地址 |
| `apiweb.ci.trigger-min-interval-seconds` | `5` | 同一流水线最小触发间隔，防 CI 重复触发 |
| `apiweb.ci.jenkins.*` | — | Jenkins Remote API 的超时、队列解析与构建轮询参数 |

> 免登录触发路径默认为 `/api/v1/webhook/ci/**`，已放进 `apiweb.security.permit-paths` 白名单；
> 若自定义了该白名单，请把上述路径一并加上。

---

## 12. 常见使用问题

### Q1: 调试时提示"环境变量 xxx 未定义"

A: 在「环境管理」或「全局变量」中添加该变量。注意 `{{var}}` 的语法（双花括号）。

### Q2: 提取的 JSONPath 返回 null

A: 打开调试窗口查看原始响应体，确认 JSONPath 语法正确。推荐用在线工具 [jsonpath.com](https://jsonpath.com) 测试。

### Q3: 性能测试启动后无响应

A: 检查 RabbitMQ 消费者是否启动。查看后端日志是否有 `started ApiWebApplication` 字样。

### Q4: UI 用例执行失败但浏览器实际能打开

A: 多半是 **元素定位方式** 不稳定：
- 优先用 `id` 或 `data-testid`（开发专门给测试用的属性）
- 避免用绝对 XPath（页面结构变动就失效）
- 添加显式等待：`assert_visible` 前先 `wait_for_element`

### Q5: 报告 PDF 导出失败

A: 浏览器可能拦截了下载。在浏览器设置中允许 `localhost:5173` 弹出下载。

### Q6: 时间显示与本地时间差 8 小时

A: 这是正常的——平台所有时间按 UTC 存储，前端通过 `dayjs.utc(str).local()` 按浏览器时区转换。如果显示 `Invalid Date`，说明后端返回了 `null` 字段（例如该用例未填写更新时间），请检查用例是否已保存。

### Q7: 报告列表显示英文状态码（如 `passed`、`failed`）

A: 页面应显示中文标签（通过/失败/错误）。若直接显示枚举英文，请清浏览器缓存后刷新——前端依赖 `STATUS_LABELS` 常量做映射，老版本 chunk 缓存可能导致映射缺失。

### Q8: 重置 / 清理测试数据

A: 平台未提供「一键清库」UI 入口（防止误操作）。开发或演示环境清空脏数据：

```bash
# 1. 清 MySQL 业务表（保留 admin 账号）
mysql -uapiweb -papiweb123 api_web <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM t_case_version; DELETE FROM t_case_step; DELETE FROM t_case_review; DELETE FROM t_case;
DELETE FROM t_scenario_step; DELETE FROM t_scenario;
DELETE FROM t_ui_scenario_step; DELETE FROM t_ui_scenario;
DELETE FROM t_test_task_run; DELETE FROM t_test_task;
DELETE FROM t_report_detail; DELETE FROM t_report_share_log; DELETE FROM t_report_share; DELETE FROM t_report;
DELETE FROM t_ui_report;
DELETE FROM t_perf_report;
DELETE FROM t_debug_record;
DELETE FROM t_perf_case;
DELETE FROM t_api_definition;
DELETE FROM t_ui_test_case;
DELETE FROM t_environment;
DELETE FROM t_module;
DELETE FROM t_global_variable;
DELETE FROM t_audit_log;
DELETE FROM t_ci_pipeline_run; DELETE FROM t_ci_pipeline;
DELETE FROM t_ci_jenkins_server;
DELETE FROM t_executor_node;
DELETE FROM t_recycle_bin_config;
DELETE FROM t_project;
DELETE FROM t_user WHERE username <> 'admin';
SET FOREIGN_KEY_CHECKS=1;
SQL

# 2. 清 Redis 缓存与执行机心跳
redis-cli FLUSHDB

# 3. 清 OSS 兜底文件（localdev profile 下）
rm -rf "$TMPDIR/apiweb-oss"/*   # Windows: %TEMP%\apiweb-oss\*
```

生产环境严禁执行上述脚本，请使用备份/恢复流程（`mysqldump` → 删表 → 重导）。
