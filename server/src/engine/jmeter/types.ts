/**
 * 性能测试（JMeter）数据模型。
 * 平台使用「结构化用例模型」描述压测方案，保存/导出时由 jmx-builder 转换为标准 .jmx，
 * 导入时由 jmx-parser 从 .jmx 还原为该模型，两者可无损往返（round-trip）。
 */

/** 通用键值对（请求头 / Query / 自定义变量） */
export interface PerfKeyValue {
  key: string
  value: string
  enabled?: boolean
}

/** 断言类型：响应码 / 响应文本 */
export type PerfAssertionType = 'responseCode' | 'responseText'

/** 断言操作符 */
export type PerfAssertionOperator = 'equals' | 'contains'

export interface PerfAssertion {
  type: PerfAssertionType
  operator: PerfAssertionOperator
  expected: string
}

/** 压测请求步骤（对应 JMeter 的 HTTP Request 采样器） */
export interface PerfStep {
  id: string
  name: string // 请求名称（JMeter 中作为 label）
  method: string // GET / POST / PUT / DELETE ...
  url: string // 完整 URL 或相对路径，支持 JMeter ${变量}
  headers?: PerfKeyValue[]
  query?: PerfKeyValue[] // Query / 表单参数（有 body 时忽略）
  body?: string // 请求体（POST/PUT，raw）
  assertions?: PerfAssertion[]
  enabled?: boolean
}

/** 出错处理方式（对应 JMeter ThreadGroup.on_sample_error） */
export type PerfOnError = 'continue' | 'startnext' | 'stopthread' | 'stoptest'

/** 性能测试用例模型 */
export interface PerfCaseModel {
  name: string
  threads: number // 并发线程数
  rampUp: number // Ramp-Up 秒
  loops: number // 循环次数（duration=0 时生效）
  duration: number // 持续时长秒（>0 时启用调度器，按时间压测）
  thinkTime: number // 思考时间毫秒
  onSampleError: PerfOnError
  variables: PerfKeyValue[] // 用户自定义变量
  steps: PerfStep[]
}
