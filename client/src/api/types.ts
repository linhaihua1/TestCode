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
  createdAt: string // 创建时间
  updatedAt: string // 更新时间
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

/** 接口用例实体（一组断言 + 提取规则的组合） */
export interface ApiCase {
  id: string // 用例 ID
  apiId: string // 关联的接口 ID
  name: string // 用例名称
  assertions: Assertion[] // 断言列表
  extracts: ExtractRule[] // 提取规则列表
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
