/** 全局类型定义（与后端 Java 实体保持字段一致） */
export interface BaseEntity {
  id: string
  createdAt: string
  updatedAt: string
}

export interface User {
  id: string
  username: string
  role: 'admin' | 'member' | 'viewer'
  createdAt: string
  updatedAt: string
}

export interface Project extends BaseEntity {
  name: string
  description?: string
}

export interface Environment extends BaseEntity {
  projectId: string
  name: string
  baseUrl?: string
  variables: KeyValueItem[]
  headers: KeyValueItem[]
}

export interface KeyValueItem {
  key: string
  value: string
  enabled?: boolean
}

export interface GlobalVariable extends BaseEntity {
  projectId: string
  name: string
  type: string
  value: string
  encrypted: boolean
  description?: string
}

export interface ModuleNode {
  key: string
  title: string
  parentId?: string
  type: 'case' | 'api' | 'ui' | 'perf'
  sortOrder: number
  children: ModuleNode[]
}

export interface CaseInfo extends BaseEntity {
  projectId: string
  moduleId?: string
  name: string
  description?: string
  status: 'draft' | 'reviewing' | 'pass' | 'fail' | 'trash'
  priority: string
  tags: string[]
  steps: any[]
  version: number
  sortOrder: number
  deletedAt?: string
}

export interface CaseVersion {
  id: string
  caseId: string
  version: number
  snapshot: any
  changeSummary?: string
  createdBy?: string
  createdAt: string
}

export interface CaseReview {
  id: string
  caseId: string
  reviewerId?: string
  reviewerName?: string
  action: string
  comment?: string
  fromStatus?: string
  toStatus?: string
  createdAt: string
}

export interface DebugRecord {
  id: string
  caseId: string
  caseNameSnapshot: string
  environmentId?: string
  executeMode: string
  result: string
  totalDuration: number
  requestSummary: any
  responseSummary: any
  assertionResults: AssertionResult[]
  extractedVariables: any
  stepResults: StepResult[]
  errorLog?: string
  createdBy?: string
  createdAt: string
}

export interface ApiDefinition extends BaseEntity {
  projectId: string
  name: string
  method: string
  path: string
  headers: KeyValueItem[]
  query: KeyValueItem[]
  body?: string
  description?: string
  mockEnabled: boolean
  mockResponse?: string
  moduleId?: string
  tags: string[]
}

export interface Scenario extends BaseEntity {
  projectId: string
  name: string
  description?: string
  steps?: ScenarioStep[]
}

export interface ScenarioStep {
  id?: string
  sortOrder: number
  apiCaseId?: string
  name?: string
  assertions: AssertionResult[]
  extracts: ExtractResult[]
}

export interface TestTask extends BaseEntity {
  projectId: string
  name: string
  description?: string
  caseIds: string[]
  environmentId?: string
  executeMode: 'sequential' | 'parallel'
  /** 失败策略：stop_on_fail / continue_all / retry_then_stop */
  failStrategy?: 'stop_on_fail' | 'continue_all' | 'retry_then_stop'
  /** 并行池大小（1-200,仅 parallel 模式生效） */
  parallelPoolSize?: number
  retryCount: number
  timeoutMs: number
  cronExpr?: string
  enabled: boolean
  notifyUrl?: string
  variables: KeyValueItem[]
  baseUrl?: string
  createdBy?: string
  deletedAt?: string
  // CI/CD webhook 字段
  webhookToken?: string
  webhookEnabled?: boolean
  webhookAutoExecute?: boolean
  // 通知渠道
  notifyChannels?: NotifyChannel[]
}

/**
 * 通知渠道：失败时通知的渠道配置。
 */
export interface NotifyChannel {
  /** email / dingtalk / feishu / webhook */
  type: 'email' | 'dingtalk' | 'feishu' | 'webhook'
  /** 接收方:邮箱/手机号/群机器人 webhook URL */
  target: string
  /** 钉钉/飞书可选加签密钥 */
  secret?: string
}

export interface TestTaskRun {
  id: string
  taskId: string
  result: 'pending' | 'running' | 'success' | 'failed' | 'error' | 'skipped'
  duration: number
  startedAt: string
  endedAt?: string
  details: any
}

export interface Report {
  id: string
  projectId: string
  scenarioId?: string
  taskId?: string
  name: string
  status: string
  duration: number
  startedAt: string
  finishedAt?: string
  triggerType?: 'manual' | 'schedule' | 'webhook' | 'api'
  triggerBy?: string
  totalCases?: number
  passedCases?: number
  failedCases?: number
  errorCases?: number
  skippedCases?: number
  totalAssertions?: number
  passedAssertions?: number
  failedAssertions?: number
  avgResponseTime?: number
  p95ResponseTime?: number
  environmentSnapshot?: string
  details?: ReportDetail[]
}

/**
 * 报告分享链接。
 */
export interface ReportShare {
  id: string
  token: string
  reportId: string
  createdBy?: string
  createdAt: string
  expiresAt: string
  revoked: boolean
  accessCount: number
  lastAccessedAt?: string
  maxAccessCount?: number
  /** 是否设置了密码 */
  hasPassword: boolean
  allowedIps?: string
  /** 分享 URL（前端拼接） */
  url?: string
}

/**
 * 趋势统计 - 单日桶。
 */
export interface TrendBucket {
  date: string
  totalCases: number
  passedCases: number
  failedCases: number
  errorCases: number
  skippedCases: number
  totalAssertions: number
  passedAssertions: number
  failedAssertions: number
  avgResponseTime: number
  p95Max: number
  reportCount: number
  passRate: number
}

/**
 * 趋势统计 - 整体汇总（顶栏卡片）。
 */
export interface TrendSummary {
  totalCases: number
  passedCases: number
  failedCases: number
  errorCases: number
  reportCount: number
  passRate: number
}

export interface ReportDetail {
  id: string
  reportId: string
  stepName: string
  status: string
  error?: string
  assertions: AssertionResult[]
  extracts: ExtractResult[]
}

export interface AssertionResult {
  source?: string
  property?: string
  operator: string
  expected?: string
  actual?: string
  passed: boolean
  message: string
}

export interface ExtractResult {
  variable: string
  value: string
}

export interface StepResult {
  stepName: string
  status: string
  durationMs: number
  error?: string
  assertions: AssertionResult[]
  extracts: ExtractResult[]
  requestSummary?: string
  responseSummary?: string
}

export interface PerfCase extends BaseEntity {
  projectId: string
  name: string
  description?: string
  threads: number
  rampUp: number
  loops: number
  duration: number
  thinkTime: number
  onSampleError: string
  variables: KeyValueItem[]
  steps: PerfStep[]
  profile: { loadProfile?: 'fixed' | 'stepping'; stepping?: number; concurrency?: number }
  deletedAt?: string
}

export interface PerfStep {
  name?: string
  method?: string
  host?: string
  port?: string
  path?: string
  body?: string
}

export interface PerfReport {
  id: string
  projectId: string
  caseId?: string
  name: string
  status: 'success' | 'failed' | 'error'
  duration: number
  startedAt: string
  summary: any
  series: any[]
  labels: any[]
  errors: any[]
  message?: string
}

export interface UiTestCase extends BaseEntity {
  projectId: string
  name: string
  description?: string
  baseUrl?: string
  setupSteps: UiStep[]
  steps: UiStep[]
  teardownSteps: UiStep[]
}

export interface UiStep {
  action: 'open' | 'click' | 'input' | 'clear' | 'submit' | 'wait'
    | 'assert_text' | 'assert_title' | 'screenshot' | 'script'
  locator?: 'id' | 'name' | 'css' | 'xpath' | 'class' | 'tag' | 'link_text' | 'partial_link_text'
  selector?: string
  value?: string
  timeoutMs?: number
}

export interface UiScenario extends BaseEntity {
  projectId: string
  name: string
  description?: string
}

export interface UiReport {
  id: string
  projectId: string
  testCaseId?: string
  name: string
  status: string
  duration: number
  startedAt: string
  details: any[]
}

export interface AuditLog {
  id: string
  userId?: string
  username?: string
  action: string
  entityType: string
  entityId?: string
  beforeJson?: any
  afterJson?: any
  ip?: string
  createdAt: string
}

export interface ExecutorNode {
  id: string
  name?: string
  capabilities: string[]
  status: 'online' | 'offline' | 'busy'
  lastHeartbeat: string
  currentTask?: string
}