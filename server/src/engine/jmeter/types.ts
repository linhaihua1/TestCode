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

/**
 * 加压方式（对应不同的线程组元件）：
 * - constant     固定并发：JMeter 原生 ThreadGroup，rampUp 内拉起 threads 后按循环/时长压
 * - stepping     阶梯加压：Stepping Thread Group（插件），按批次递增到目标并发并保持
 * - concurrency  目标并发：Concurrency Thread Group（插件），阶梯逼近目标并发
 */
export type PerfLoadProfile = 'constant' | 'stepping' | 'concurrency'

/** 阶梯加压参数 */
export interface PerfStepping {
  initialDelay?: number // 初始等待（秒）
  batchThreads?: number // 每批递增线程数
  batchInterval?: number // 每批间隔（秒）
  flightTime?: number // 到达目标后保持（秒）
  burstThreads?: number // 突增量
  burstInterval?: number // 突增间隔（秒）
}

/** 目标并发参数 */
export interface PerfConcurrency {
  steps?: number // 阶梯数
  holdTarget?: number // 达标后保持时长（按 unit）
  unit?: 'S' | 'M' | 'H' | 'D'
}

/** 性能测试用例模型 */
export interface PerfCaseModel {
  name: string
  threads: number // 并发线程数（各加压方式下的目标并发）
  rampUp: number // Ramp-Up 秒
  loops: number // 循环次数（duration=0 时生效）
  duration: number // 持续时长秒（>0 时启用调度器，按时间压测）
  thinkTime: number // 思考时间毫秒
  onSampleError: PerfOnError
  variables: PerfKeyValue[] // 用户自定义变量
  steps: PerfStep[]
  /** 加压方式，缺等价于 constant */
  loadProfile?: PerfLoadProfile
  stepping?: PerfStepping
  concurrency?: PerfConcurrency
}
