/**
 * 前端领域类型定义
 *
 * 职责：声明前端使用的核心数据结构，字段与后端 Prisma 实体及执行结果对齐。
 * 这些类型同时被 API 封装层和各页面组件复用，保证前后端契约一致。
 */

/** 通用键值对（用于变量、请求头、Query 参数等） */
export interface KeyValue {
  key: string // 键名
  value: string // 键值
  enabled?: boolean // 是否启用（可选）
}

/** 项目实体 */
export interface Project {
  id: string // 项目 ID
  name: string // 项目名称
  description?: string | null // 项目描述
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** 环境实体（一套可切换的接口运行配置） */
export interface Environment {
  id: string // 环境 ID
  projectId: string // 所属项目 ID
  name: string // 环境名称（如 dev / test / prod）
  baseUrl?: string | null // 基础地址
  variables: KeyValue[] // 环境变量列表
  headers: KeyValue[] // 公共请求头列表
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** 接口定义实体 */
export interface ApiDefinition {
  id: string // 接口 ID
  projectId: string // 所属项目 ID
  name: string // 接口名称
  method: string // HTTP 方法（GET/POST 等）
  path: string // 请求路径（支持 ${变量} 占位）
  headers: KeyValue[] // 请求头列表
  query: KeyValue[] // Query 参数列表
  body?: string | null // 请求体（JSON 或文本）
  description?: string | null // 接口描述
  mockEnabled: boolean // 是否启用 Mock
  mockResponse?: string | null // Mock 响应内容
  moduleId?: string | null // 所属模块
  tags: string[] // 标签
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** 批量导入的单个接口条目（Excel 模板导入 / Swagger 导入共用） */
export interface ApiImportItem {
  name: string
  method: string
  path: string
  headers?: KeyValue[]
  query?: KeyValue[]
  body?: string | null
  description?: string
  module?: string // 模块/标签名
  tags?: string[]
  mockEnabled?: boolean
  mockResponse?: string | null
}

/** 断言规则（用于校验响应是否符合预期） */
export interface Assertion {
  type: 'statusCode' | 'jsonPath' | 'header' | 'regex' // 断言类型
  expression: string // 表达式（如 JSONPath、响应头名、正则）
  expected: string // 期望值
  operator?: 'eq' | 'ne' | 'contains' | 'notContains' | 'regex' | 'gt' | 'lt' // 比较运算符
}

/** 提取规则（从响应中抽取变量供后续步骤引用） */
export interface ExtractRule {
  name: string // 提取后的变量名
  type: 'jsonPath' | 'header' | 'regex' // 提取类型
  expression: string // 提取表达式
}

/** 接口用例内的单个步骤定义（多步骤用例） */
export interface CaseStepDef {
  apiId: string // 该步骤引用的接口 ID
  name?: string // 步骤名称
  assertions: Assertion[] // 该步骤的断言
  extracts: ExtractRule[] // 该步骤的提取
}

/** 接口用例实体（支持多步骤：前置准备/核心请求/后置清理） */
export interface ApiCase {
  id: string // 用例 ID
  apiId: string // 关联的默认接口 ID（单接口用例时使用）
  name: string // 用例名称
  assertions: Assertion[] // 断言列表（单接口用例时使用）
  extracts: ExtractRule[] // 提取规则列表（单接口用例时使用）
  stepDefs: CaseStepDef[] // 多步骤定义（非空则按多步骤执行）
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** 场景实体（多个步骤串成的自动化流程） */
export interface Scenario {
  id: string // 场景 ID
  projectId: string // 所属项目 ID
  name: string // 场景名称
  description?: string | null // 场景描述
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** 单条断言的执行结果 */
export interface AssertionResult {
  passed: boolean // 是否通过
  type: string // 断言类型
  expression: string // 表达式
  expected: string // 期望值
  actual: string // 实际值
  message: string // 结果描述信息
}

/** 场景步骤实体（场景中的单个执行单元） */
export interface ScenarioStep {
  id: string // 步骤 ID
  scenarioId: string // 所属场景 ID
  order: number // 步骤执行顺序
  apiCaseId?: string | null // 关联的接口用例 ID
  name?: string | null // 步骤名称
  assertions: Assertion[] // 断言列表
  extracts: ExtractRule[] // 提取规则列表
  apiCase?: (ApiCase & { api?: ApiDefinition }) | null // 关联的用例及其接口详情
}

/** 报告中单个步骤的执行明细 */
export interface ReportDetail {
  id: string // 明细 ID
  stepName: string // 步骤名称
  status: 'PASS' | 'FAIL' | 'ERROR' // 步骤执行状态
  error?: string | null // 错误信息（出错时）
  assertions: AssertionResult[] // 断言执行结果
  extracts: Record<string, string> // 提取到的变量及其值
}

/** 测试报告实体（一次场景执行的完整结果） */
export interface Report {
  id: string // 报告 ID
  projectId: string // 所属项目 ID
  scenarioId?: string | null // 关联的场景 ID
  name: string // 场景名称
  status: 'PASS' | 'FAIL' | 'ERROR' // 整体执行状态
  duration: number // 执行耗时（毫秒）
  startedAt: string // 开始执行时间
  details: ReportDetail[] // 各步骤执行明细
}

/** 支持的 HTTP 方法列表（用于请求构建器的下拉选择） */
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const

/** 系统用户（用户管理） */
export interface User {
  id: string // 用户 ID
  username: string // 用户名（唯一）
  role: string // 角色
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
}

/** UI 自动化步骤动作类型（参考 Selenium） */
export type UiAction =
  | 'open' // 打开页面
  | 'click' // 点击元素
  | 'type' // 输入文本
  | 'assertText' // 断言元素文本
  | 'assertExists' // 断言元素存在
  | 'assertTitle' // 断言页面标题
  | 'wait' // 等待元素出现

/** UI 元素定位方式 */
export type LocatorType = 'css' | 'xpath' | 'id' | 'name' | 'linkText'

/** UI 测试步骤定义 */
export interface UiStep {
  action: UiAction
  locatorType?: LocatorType // 定位方式，默认 css
  target?: string // 选择器（或 open 时的 URL）
  value?: string // 输入值 / 期望值 / 等待超时（秒）
}

/** UI 测试步骤执行结果 */
export interface UiStepResult {
  action: UiAction
  target?: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  message: string
  screenshot?: string // 失败/出错时的 base64 截图
}

/** UI 自动化测试用例 */
export interface UiTestCase {
  id: string
  projectId: string
  name: string
  description?: string | null
  baseUrl?: string | null
  setupSteps: UiStep[] // 前置步骤（如打开浏览器、登录）
  steps: UiStep[] // 测试步骤（核心操作 + 断言）
  teardownSteps: UiStep[] // 后置步骤（如清理、关闭）
  createdAt: string
  updatedAt: string
}

/** UI 自动化测试报告 */
export interface UiReport {
  id: string
  projectId: string
  testCaseId?: string | null
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  duration: number
  startedAt: string
  details: UiStepResult[]
}

/** UI 执行场景中的单个步骤（引用一个 UI 用例） */
export interface UiScenarioStep {
  id: string
  scenarioId: string
  order: number
  uiTestCaseId?: string | null
  uiTestCase?: UiTestCase | null
}

/** UI 自动化执行场景（组合多个 UI 用例） */
export interface UiScenario {
  id: string
  projectId: string
  name: string
  description?: string | null
  createdAt: string
  updatedAt: string
}

/** UI 步骤动作的可读中文名（用于下拉选择） */
export const UI_ACTIONS: { value: UiAction; label: string }[] = [
  { value: 'open', label: '打开页面' },
  { value: 'click', label: '点击元素' },
  { value: 'type', label: '输入文本' },
  { value: 'assertText', label: '断言元素文本' },
  { value: 'assertExists', label: '断言元素存在' },
  { value: 'assertTitle', label: '断言页面标题' },
  { value: 'wait', label: '等待元素出现' },
]

/** UI 元素定位方式的可读中文名 */
export const UI_LOCATORS: { value: LocatorType; label: string }[] = [
  { value: 'css', label: 'CSS 选择器' },
  { value: 'xpath', label: 'XPath' },
  { value: 'id', label: 'ID' },
  { value: 'name', label: 'Name' },
  { value: 'linkText', label: '链接文本' },
]

// 全局变量
export interface GlobalVariable {
  id: string
  projectId: string
  name: string
  type: string
  value: string
  encrypted: boolean
  description?: string | null
  createdAt: string
  updatedAt: string
}

// 目录模块
export interface Module {
  id: string
  projectId: string
  parentId?: string | null
  name: string
  type: string
  sortOrder: number
  createdAt: string
  updatedAt: string
}

// 新用例（含模块和版本）
export interface CaseInfo {
  id: string
  projectId: string
  moduleId?: string | null
  module?: Module | null
  name: string
  description?: string | null
  status: string
  priority: string
  tags: unknown[]
  steps: unknown[]
  version: number
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
  versions?: CaseVersion[]
}

export interface CaseVersion {
  id: string
  caseId: string
  version: number
  snapshot: unknown
  changeSummary?: string | null
  createdBy?: string | null
  createdAt: string
}

// 调试记录
export interface DebugRecord {
  id: string
  caseId: string
  caseInfo?: CaseInfo | null
  caseNameSnapshot: string
  environmentId?: string | null
  executeMode: string
  result: string
  totalDuration: number
  requestSummary: unknown[]
  responseSummary: unknown[]
  assertionResults: unknown[]
  extractedVariables: unknown[]
  errorLog?: string | null
  stepResults: unknown[]
  createdBy?: string | null
  createdAt: string
}

/** 用例步骤类型（PRD 第四章节） */
export type CaseStepType = 'request' | 'script' | 'wait' | 'variable' | 'controller'

/** 流程控制器类型 */
export type ControllerType = 'if' | 'for' | 'while'

/** 用例步骤阶段：前置 / 测试 / 后置 */
export type CaseStepPhase = 'setup' | 'test' | 'teardown'

/** 用例步骤定义 */
export interface CaseStep {
  id: string // 步骤唯一 ID
  type: CaseStepType // 步骤类型
  phase: CaseStepPhase // 所属阶段
  name: string // 步骤名称
  enabled: boolean // 是否启用
  remark?: string // 步骤备注
  // 接口请求步骤
  apiId?: string // 引用的接口定义 ID
  method?: string // 请求方法
  url?: string // 请求 URL（支持 ${变量}）
  headers?: KeyValue[] // 请求头
  query?: KeyValue[] // Query 参数
  body?: string // 请求体
  assertions?: Assertion[] // 断言
  extracts?: ExtractRule[] // 提取
  // 脚本步骤
  script?: string // 脚本内容
  // 等待步骤
  waitMs?: number // 等待毫秒
  // 变量赋值步骤
  varName?: string // 变量名
  varValue?: string // 变量值
  // 流程控制器步骤
  controllerType?: ControllerType // 控制器类型
  condition?: string // 条件表达式（if/while 用）
  children?: CaseStep[] // 控制器子步骤（嵌套，支持递归）
  loopVar?: string // for 循环变量名
  loopCount?: number // for 循环次数
  maxLoops?: number // while 最大循环次数
}

// ---------- 测试任务与报告（PRD 第 5 期） ----------

/** 测试任务（选中一组用例 + 环境，可手动或定时执行） */
export interface TestTask {
  id: string
  projectId: string
  name: string
  description?: string | null
  caseIds: string[] // 选中的用例 ID 列表
  environmentId?: string | null
  executeMode: string // sequential / parallel
  retryCount: number
  timeout: number
  cronExpr?: string | null
  enabled: boolean
  notifyUrl?: string | null
  createdBy?: string | null
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
  latestRun?: TestTaskRun | null // 列表接口附带最近一次执行结果
}

/** 单个用例在报告中的执行结果 */
export interface CaseRunDetail {
  caseId: string
  caseName: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  duration: number
  retries: number
  error?: string
  stepResults: Array<{
    id: string
    name: string
    type: string
    status: string
    message: string
    request?: { method: string; url: string }
    response?: { status: number; body: unknown; duration: number }
    assertions?: Array<{ passed: boolean; message: string }>
    extracted?: Record<string, string>
    children?: Array<unknown>
  }>
}

/** 报告汇总 */
export interface RunSummary {
  total: number
  passed: number
  failed: number
  error: number
  result: string
}

/** 单次任务执行报告 */
export interface TestTaskRun {
  id: string
  taskId: string
  result: string
  duration: number
  startedAt: string
  endedAt?: string | null
  details: CaseRunDetail[]
  summary?: RunSummary
}
