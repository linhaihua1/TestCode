/**
 * 执行引擎类型定义。
 * 集中声明变量、断言、提取、请求与响应、步骤结果等核心数据结构，
 * 供 variables / request / extract / assert / runner 各模块共用。
 */

/** 变量上下文：变量名 -> 字符串值 */
export type VariableContext = Record<string, string>

/** 断言类型：状态码 / JSONPath / 响应头 / 正则 */
export type AssertionType = 'statusCode' | 'jsonPath' | 'header' | 'regex'

/** 比较操作符：等于/不等于/包含/不包含/正则匹配/大于/小于 */
export type AssertionOperator = 'eq' | 'ne' | 'contains' | 'notContains' | 'regex' | 'gt' | 'lt'

/** 一条断言规则 */
export interface Assertion {
  /** 断言类型 */
  type: AssertionType
  /** jsonPath 表达式 / header 名 / 正则 */
  expression: string
  /** 期望值 */
  expected: string
  /** 比较操作符，缺省为 eq（等于） */
  operator?: AssertionOperator
  /** 失败提示文案 */
  failMessage?: string
  /** 失败策略：continue（继续）/ stop（停止后续步骤） */
  failStrategy?: 'continue' | 'stop'
}

/** 单条断言的判定结果 */
export interface AssertionResult {
  /** 是否通过 */
  passed: boolean
  /** 断言类型 */
  type: AssertionType
  /** 取值表达式 */
  expression: string
  /** 期望值 */
  expected: string
  /** 实际值（字符串化） */
  actual: string
  /** 描述文案（通过/失败原因） */
  message: string
}

/** 提取类型：JSONPath / 响应头 / 正则 */
export type ExtractType = 'jsonPath' | 'header' | 'regex'

/** 一条提取规则 */
export interface ExtractRule {
  /** 提取结果写入上下文的变量名 */
  name: string
  /** 提取类型 */
  type: ExtractType
  /** 提取表达式（JSONPath / header 名 / 正则） */
  expression: string
  /** 提取失败时使用的默认值 */
  defaultValue?: string
}

/** 请求规格（变量已替换后的最终值） */
export interface RequestSpec {
  /** HTTP 方法（GET/POST/PUT/DELETE 等） */
  method: string
  /** 完整请求 URL */
  url: string
  /** 请求头 */
  headers?: Record<string, string>
  /** URL 查询参数 */
  query?: Record<string, string>
  /** 请求体（对象或字符串） */
  body?: unknown
  /** 超时时间（毫秒） */
  timeout?: number
}

/** 统一结构的 HTTP 响应 */
export interface ResponseData {
  /** HTTP 状态码 */
  status: number
  /** 响应头 */
  headers: Record<string, string>
  /** 解析后的响应体（JSON 对象或文本） */
  body: unknown
  /** 原始响应体字符串 */
  rawBody: string
  /** 请求耗时（毫秒） */
  duration: number
}

/** 一个执行步骤（场景中的一步，绑定某个接口用例） */
export interface StepResult {
  /** 步骤 ID */
  stepId: string
  /** 步骤名称 */
  name: string
  /** 执行状态：通过/失败/异常 */
  status: 'PASS' | 'FAIL' | 'ERROR'
  /** 异常时的错误信息 */
  error?: string
  /** 各断言结果 */
  assertions: AssertionResult[]
  /** 本步骤实际提取到的变量 */
  extracted: Record<string, string>
  /** 步骤耗时（毫秒） */
  duration: number
}
