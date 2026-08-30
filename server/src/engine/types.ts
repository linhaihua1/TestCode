/** 变量上下文：变量名 -> 字符串值 */
export type VariableContext = Record<string, string>

/** 断言类型 */
export type AssertionType = 'statusCode' | 'jsonPath' | 'header' | 'regex'

/** 比较操作符 */
export type AssertionOperator = 'eq' | 'ne' | 'contains' | 'notContains' | 'regex' | 'gt' | 'lt'

export interface Assertion {
  type: AssertionType
  /** jsonPath 表达式 / header 名 / 正则 */
  expression: string
  expected: string
  operator?: AssertionOperator
}

export interface AssertionResult {
  passed: boolean
  type: AssertionType
  expression: string
  expected: string
  actual: string
  message: string
}

/** 提取类型 */
export type ExtractType = 'jsonPath' | 'header' | 'regex'

export interface ExtractRule {
  name: string
  type: ExtractType
  expression: string
}

/** 请求规格（变量已替换后的最终值） */
export interface RequestSpec {
  method: string
  url: string
  headers?: Record<string, string>
  query?: Record<string, string>
  body?: unknown
  timeout?: number
}

export interface ResponseData {
  status: number
  headers: Record<string, string>
  /** 解析后的响应体（JSON 对象或文本） */
  body: unknown
  rawBody: string
  duration: number
}

/** 一个执行步骤（场景中的一步，绑定某个接口用例） */
export interface StepResult {
  stepId: string
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  error?: string
  assertions: AssertionResult[]
  extracted: Record<string, string>
  duration: number
}
