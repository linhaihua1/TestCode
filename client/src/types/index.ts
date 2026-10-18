/** 全局类型定义（与后端 Java 实体保持字段一致；改字段需前后端同步） */
export interface BaseEntity {
  /** 主键：后端使用 UUID（MyBatis-Plus ASSIGN_UUID） */
  id: string;
  /** 创建时间（ISO 字符串，后端以 UTC 存储，展示层按本地时区格式化） */
  createdAt: string;
  updatedAt: string;
}

/** 系统用户；role 决定写权限（viewer 只读，见 constants.ROLES） */
export interface User {
  id: string;
  username: string;
  /** admin：全部权限 / member：可编辑 / viewer：只读 */
  role: 'admin' | 'member' | 'viewer';
  createdAt: string;
  updatedAt: string;
}

/** 项目：所有测试资产与报告的隔离单元 */
export interface Project extends BaseEntity {
  name: string;
  description?: string;
}

/** 运行环境：执行时提供 baseUrl、变量与公共请求头 */
export interface Environment extends BaseEntity {
  projectId: string;
  name: string;
  baseUrl?: string;
  /** 环境变量（key-value 列表，可用 {{key}} 引用） */
  variables: KeyValueItem[];
  /** 公共请求头，执行时合并到每个请求 */
  headers: KeyValueItem[];
}

/** 通用的键值对结构（请求头、Query 参数、变量统一用它） */
export interface KeyValueItem {
  key: string;
  value: string;
  /** 是否启用；false 的项在组装请求时被忽略 */
  enabled?: boolean;
}

/** 项目级全局变量；encrypted 的敏感值后端密文存储 */
export interface GlobalVariable extends BaseEntity {
  projectId: string;
  name: string;
  type: string;
  value: string;
  encrypted: boolean;
  description?: string;
}

/** 模块树节点：用例/接口/UI/性能四类资产共用的目录树 */
export interface ModuleNode {
  key: string;
  title: string;
  /** 父节点 ID；为空表示根节点 */
  parentId?: string;
  /** 树归属的资产类型 */
  type: 'case' | 'api' | 'ui' | 'perf';
  sortOrder: number;
  children: ModuleNode[];
}

/** 接口测试用例：steps 为步骤数组，每个步骤可以是 HTTP 请求/等待/IF/FOR/WHILE/脚本 */
export interface CaseInfo extends BaseEntity {
  projectId: string;
  moduleId?: string;
  name: string;
  description?: string;
  /** 状态流转：draft 草稿 → reviewing 评审中 → pass 通过 / fail 不通过 */
  status: 'draft' | 'reviewing' | 'pass' | 'fail' | 'trash';
  priority: string;
  tags: string[];
  /** 步骤定义（见 CaseStep）；后端以 JSON 存储 */
  steps: any[];
  /** 版本号：每次保存自增，用于生成版本快照 */
  version: number;
  sortOrder: number;
  /** 软删除时间，非空表示已进入回收站 */
  deletedAt?: string;
}

export interface CaseVersion {
  id: string;
  caseId: string;
  version: number;
  snapshot: any;
  changeSummary?: string;
  createdBy?: string;
  createdAt: string;
}

export interface CaseReview {
  id: string;
  caseId: string;
  reviewerId?: string;
  reviewerName?: string;
  action: string;
  comment?: string;
  fromStatus?: string;
  toStatus?: string;
  createdAt: string;
}

export interface DebugRecord {
  id: string;
  caseId: string;
  caseNameSnapshot: string;
  environmentId?: string;
  executeMode: string;
  result: string;
  totalDuration: number;
  requestSummary: any;
  responseSummary: any;
  assertionResults: AssertionResult[];
  extractedVariables: any;
  stepResults: StepResult[];
  errorLog?: string;
  createdBy?: string;
  createdAt: string;
}

export interface ApiDefinition extends BaseEntity {
  projectId: string;
  name: string;
  method: string;
  path: string;
  headers: KeyValueItem[];
  query: KeyValueItem[];
  body?: string;
  description?: string;
  mockEnabled: boolean;
  mockResponse?: string;
  moduleId?: string;
  tags: string[];
}

export interface Scenario extends BaseEntity {
  projectId: string;
  name: string;
  description?: string;
  steps?: ScenarioStep[];
}

export interface ScenarioStep {
  id?: string;
  sortOrder: number;
  apiCaseId?: string;
  name?: string;
  assertions: AssertionResult[];
  /** 变量提取规则（见 ExtractRule） */
  extracts: ExtractRule[];
}

export interface TestTask extends BaseEntity {
  projectId: string;
  name: string;
  description?: string;
  caseIds: string[];
  environmentId?: string;
  executeMode: 'sequential' | 'parallel';
  /** 失败策略：stop_on_fail / continue_all / retry_then_stop */
  failStrategy?: 'stop_on_fail' | 'continue_all' | 'retry_then_stop';
  /** 并行池大小（1-200,仅 parallel 模式生效） */
  parallelPoolSize?: number;
  retryCount: number;
  timeoutMs: number;
  cronExpr?: string;
  enabled: boolean;
  notifyUrl?: string;
  variables: KeyValueItem[];
  baseUrl?: string;
  createdBy?: string;
  deletedAt?: string;
  // 执行机参数
  executorUrl?: string;
  executorHost?: string;
  executorPort?: number;
  // CI/CD webhook 字段
  webhookToken?: string;
  webhookEnabled?: boolean;
  webhookAutoExecute?: boolean;
  // 通知渠道
  notifyChannels?: NotifyChannel[];
}

/**
 * 通知渠道：失败时通知的渠道配置。
 */
export interface NotifyChannel {
  /** email / dingtalk / feishu / webhook */
  type: 'email' | 'dingtalk' | 'feishu' | 'webhook';
  /** 接收方:邮箱/手机号/群机器人 webhook URL */
  target: string;
  /** 钉钉/飞书可选加签密钥 */
  secret?: string;
}

/** 任务的一次执行记录（展开任务行时展示） */
export interface TestTaskRun {
  id: string;
  taskId: string;
  /** pending 排队 / running 执行中 / success 通过 / failed 失败 / error 异常 / skipped 跳过 */
  result: 'pending' | 'running' | 'success' | 'failed' | 'error' | 'skipped';
  /** 执行耗时（毫秒） */
  duration: number;
  startedAt: string;
  endedAt?: string;
  /** 用例级执行明细；数据量大时后端返回对象存储引用，前端需按引用再取 */
  details: any;
}

/**
 * 通用分页响应（与后端 common/PageResult 对齐）。
 *
 * <p>注意：分页接口返回的是本结构而非数组，调用方需取 {@code records}。</p>
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/** 测试报告主记录（接口/场景执行完成后生成） */
export interface Report {
  id: string;
  projectId: string;
  scenarioId?: string;
  taskId?: string;
  name: string;
  status: string;
  duration: number;
  startedAt: string;
  finishedAt?: string;
  triggerType?: 'manual' | 'schedule' | 'webhook' | 'api';
  triggerBy?: string;
  // 以下统计字段在执行完成后由后端回填，执行中可能为 0
  totalCases?: number;
  passedCases?: number;
  failedCases?: number;
  errorCases?: number;
  skippedCases?: number;
  totalAssertions?: number;
  passedAssertions?: number;
  failedAssertions?: number;
  avgResponseTime?: number;
  p95ResponseTime?: number;
  /** 执行时的环境快照，便于事后还原现场 */
  environmentSnapshot?: string;
  details?: ReportDetail[];
}

/**
 * 报告分享链接。
 */
export interface ReportShare {
  id: string;
  token: string;
  reportId: string;
  createdBy?: string;
  createdAt: string;
  expiresAt: string;
  revoked: boolean;
  accessCount: number;
  lastAccessedAt?: string;
  maxAccessCount?: number;
  /** 是否设置了密码 */
  hasPassword: boolean;
  allowedIps?: string;
  /** 分享 URL（前端拼接） */
  url?: string;
}

/**
 * 趋势统计 - 单日桶。
 */
export interface TrendBucket {
  date: string;
  totalCases: number;
  passedCases: number;
  failedCases: number;
  errorCases: number;
  skippedCases: number;
  totalAssertions: number;
  passedAssertions: number;
  failedAssertions: number;
  avgResponseTime: number;
  p95Max: number;
  reportCount: number;
  passRate: number;
}

/**
 * 趋势统计 - 整体汇总（顶栏卡片）。
 */
export interface TrendSummary {
  totalCases: number;
  passedCases: number;
  failedCases: number;
  errorCases: number;
  reportCount: number;
  passRate: number;
}

export interface ReportDetail {
  id: string;
  reportId: string;
  stepName: string;
  status: string;
  error?: string;
  assertions: AssertionResult[];
  extracts: ExtractResult[];
}

export interface AssertionResult {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string;
  source?: string;
  property?: string;
  operator: string;
  expected?: string;
  actual?: string;
  passed: boolean;
  message: string;
}

export interface ExtractResult {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string;
  variable: string;
  value: string;
}

/**
 * 变量提取**规则**（编写用例时配置，对应后端 EngineDtos.Extract）。
 *
 * <p>与 {@link ExtractResult}（执行后的提取结果）区分开：
 * 规则描述"怎么提取"，结果描述"提取到了什么"。
 */
export interface ExtractRule {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string;
  /** 提取方式，取值见 constants 的 EXTRACT_TYPES */
  type: string;
  /** 表达式（JSONPath / 正则 / Header 名 / Cookie 名 / XPath） */
  expression: string;
  /** 提取结果保存到的变量名，后续用 {{变量名}} 引用 */
  variable: string;
  /** 未命中时的默认值 */
  defaultValue?: string;
  /** 未命中时的策略：ignore（继续）/ stop（中断步骤） */
  failStrategy?: string;
}

/**
 * 接口用例步骤（编写用例面板）。
 *
 * <p>position 区分前置/测试/后置三个分区，enabled 控制是否参与执行。
 * type 对应 6 种步骤：HTTP 请求 / 等待时间 / IF / FOR / WHILE / JS 脚本。
 */
export interface CaseStep {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string;
  /** 步骤分区：前置 / 测试 / 后置（默认 TEST） */
  position?: 'PRE' | 'TEST' | 'POST';
  /** 是否启用（false = 跳过执行） */
  enabled?: boolean;
  type: 'http' | 'wait' | 'if' | 'for' | 'while' | 'script';
  name?: string;
  method?: string;
  url?: string;
  headers: KeyValueItem[];
  query: KeyValueItem[];
  body?: string;
  assertions: AssertionResult[];
  /** 变量提取规则（不是执行结果，见 ExtractRule / ExtractResult 区别） */
  extracts: ExtractRule[];
  condition?: string;
  loopCount?: number;
  /** 等待时间（毫秒），type=wait 时使用 */
  waitMs?: number;
  script?: string;
  __activeTab?: string;
}

export interface StepResult {
  stepName: string;
  status: string;
  durationMs: number;
  error?: string;
  assertions: AssertionResult[];
  extracts: ExtractResult[];
  requestSummary?: string;
  responseSummary?: string;
}

/** 性能测试用例：执行时被后端转换成 JMeter 的 .jmx 测试计划 */
export interface PerfCase extends BaseEntity {
  projectId: string;
  name: string;
  description?: string;
  /** 并发线程数（虚拟用户数） */
  threads: number;
  /** 加压时长（秒）：线程在该时间内逐步启动 */
  rampUp: number;
  /** 循环次数；配置了 duration 时由持续时间控制 */
  loops: number;
  /** 持续时间（秒）；大于 0 时启用调度器 */
  duration: number;
  /** 思考时间（毫秒）：每个请求之间的停顿 */
  thinkTime: number;
  /** 取样错误后的处理策略：continue / startnext / stopthread / stoptest */
  onSampleError: string;
  variables: KeyValueItem[];
  /** 压测请求步骤，每个步骤生成一个 HTTP 取样器 */
  steps: PerfStep[];
  /** 负载模型：fixed 固定并发 / stepping 阶梯加压 */
  profile: { loadProfile?: 'fixed' | 'stepping'; stepping?: number; concurrency?: number };
  deletedAt?: string;
}

export interface PerfStep {
  name?: string;
  protocol?: string;
  method?: string;
  host?: string;
  port?: string;
  path?: string;
  /** URL 查询参数 */
  queryParams?: KeyValueItem[];
  /** 请求头 */
  headers?: KeyValueItem[];
  /** 请求体（POST/PUT 时使用） */
  body?: string;
  /** 编码（默认 UTF-8） */
  encoding?: string;
  /** 是否跟随重定向 */
  followRedirects?: boolean;
  /** 是否使用 keep-alive */
  useKeepAlive?: boolean;
}

/** 性能测试报告；图表数据由 summary/series/labels/errors 四个 JSON 块驱动 */
export interface PerfReport {
  id: string;
  projectId: string;
  caseId?: string;
  name: string;
  status: 'success' | 'failed' | 'error';
  duration: number;
  startedAt: string;
  /** 汇总指标：样本数、错误数、平均/最小/最大、P90/P95/P99、TPS */
  summary: any;
  /** 时间序列点 [{ts, elapsed}]，画响应时间折线图 */
  series: any[];
  /** 按接口（label）聚合的统计，画分接口柱状图 */
  labels: any[];
  /** 错误 TOP 列表 [{message, count}] */
  errors: any[];
  /** 执行失败时的异常原因 */
  message?: string;
}

export interface UiTestCase extends BaseEntity {
  projectId: string;
  name: string;
  description?: string;
  baseUrl?: string;
  setupSteps: UiStep[];
  steps: UiStep[];
  teardownSteps: UiStep[];
}

export interface UiStep {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string;
  action:
    | 'open'
    | 'click'
    | 'input'
    | 'clear'
    | 'submit'
    | 'wait'
    | 'assert_text'
    | 'assert_title'
    | 'screenshot'
    | 'script';
  locator?: 'id' | 'name' | 'css' | 'xpath' | 'class' | 'tag' | 'link_text' | 'partial_link_text';
  selector?: string;
  value?: string;
  timeoutMs?: number;
}

export interface UiScenario extends BaseEntity {
  projectId: string;
  name: string;
  description?: string;
  /** 执行环境 ID（t_environment） */
  environmentId?: string;
  /** 执行参数 [{key,value}] */
  variables?: KeyValueItem[];
  /** 执行机完整地址 */
  executorUrl?: string;
  /** 执行机域名/IP */
  executorHost?: string;
  /** 执行机端口 */
  executorPort?: number;
  /** 失败重试次数 */
  retryCount?: number;
  /** 用例执行超时（毫秒） */
  timeoutMs?: number;
}

export interface UiReport {
  id: string;
  projectId: string;
  testCaseId?: string;
  name: string;
  status: string;
  duration: number;
  startedAt: string;
  details: any[];
}

export interface AuditLog {
  id: string;
  userId?: string;
  username?: string;
  action: string;
  entityType: string;
  entityId?: string;
  beforeJson?: any;
  afterJson?: any;
  ip?: string;
  createdAt: string;
}

export interface ExecutorNode {
  id: string;
  name?: string;
  capabilities: string[];
  status: 'online' | 'offline' | 'busy';
  lastHeartbeat: string;
  currentTask?: string;
}
/* ============================================================
 * CI/CD 流水线
 * ============================================================ */

/** 流水线测试类型：接口自动化 / UI 自动化 / 性能测试 */
export type PipelineTestTypeCode = 'api' | 'ui' | 'perf';

/** 流水线执行方式：平台本地执行 / 调用 Jenkins Job 执行 */
export type PipelineExecModeCode = 'local' | 'jenkins';

/** 流水线执行状态 */
export type PipelineRunStatusCode = 'pending' | 'running' | 'success' | 'failed' | 'error';

/** 流水线执行目标类型 */
export type PipelineTargetTypeCode = 'test_task' | 'ui_scenario' | 'ui_test' | 'perf_case';

/** 质量门禁规则：指标值 操作符 阈值 成立即判定违规（门禁不通过） */
export interface QualityGateRule {
  metric: string;
  operator: string;
  threshold: number;
}

/** 门禁判定明细（后端执行后回填） */
export interface QualityGateResult extends QualityGateRule {
  metricLabel?: string;
  operatorSymbol?: string;
  actual?: number;
  passed?: boolean;
}

/** CI/CD 流水线：把一类测试目标包装成可被 Jenkins 触发的流水线 */
export interface CiPipeline {
  id?: string;
  projectId: string;
  name: string;
  description?: string;
  /** 测试类型 */
  testType: PipelineTestTypeCode;
  /** 目标类型（由测试类型推导，也可显式指定） */
  targetType?: PipelineTargetTypeCode;
  /** 执行目标 ID */
  targetId?: string;
  /** 执行方式 */
  execMode?: PipelineExecModeCode;
  /** Jenkins 服务器 ID（execMode=jenkins 时必填） */
  jenkinsServerId?: string;
  /** Jenkins Job 名称（支持 folder/job） */
  jenkinsJobName?: string;
  /** Jenkins 构建参数（JSON 字符串） */
  jenkinsParams?: string;
  /** 质量门禁规则（JSON 字符串数组） */
  qualityGate?: string;
  /** 通知渠道（JSON 字符串数组） */
  notifyChannels?: string;
  enabled?: boolean;
  /** CI 免登录触发 Token（仅 rotate 接口返回） */
  webhookToken?: string;
  webhookEnabled?: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 流水线执行记录 */
export interface CiPipelineRun {
  id: string;
  pipelineId: string;
  projectId: string;
  pipelineName?: string;
  testType?: PipelineTestTypeCode;
  targetType?: PipelineTargetTypeCode;
  targetId?: string;
  execMode?: PipelineExecModeCode;
  status: PipelineRunStatusCode;
  triggerType?: string;
  triggerBy?: string;
  /** 本次执行产生的报告 ID（JSON 字符串数组） */
  reportIds?: string;
  /** 执行统计（JSON 字符串对象） */
  summary?: string;
  /** 门禁判定明细（JSON 字符串数组） */
  gateResult?: string;
  /** 1=通过 0=未通过 null=未判定 */
  gatePassed?: number | null;
  log?: string;
  jenkinsServerId?: string;
  jenkinsJobName?: string;
  jenkinsQueueUrl?: string;
  jenkinsBuildNo?: number;
  jenkinsBuildUrl?: string;
  jenkinsResult?: string;
  duration?: number;
  startedAt?: string;
  endedAt?: string;
}

/** Jenkins 服务器配置（apiToken 不在接口返回中） */
export interface JenkinsServer {
  id?: string;
  name: string;
  baseUrl: string;
  username?: string;
  /** 仅入参有效：填写即覆盖；出参恒为空 */
  apiToken?: string;
  /** basic=用户名+Token 的 Basic 认证；token=仅 Token */
  authType?: 'basic' | 'token';
  enabled?: boolean;
  description?: string;
}

/** 流水线可选执行目标（测试任务 / UI 场景 / UI 用例 / 性能用例） */
export interface CiTarget {
  id: string;
  name: string;
  targetType: PipelineTargetTypeCode;
}
