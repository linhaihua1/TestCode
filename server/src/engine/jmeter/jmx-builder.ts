/**
 * 用例模型 → JMeter .jmx（测试计划）XML 生成器。
 *
 * 生成的文件可被 JMeter GUI 直接打开，也可由 `jmeter -n -t plan.jmx` 无界面执行。
 * 关键约定：
 *   - JMeter 用「元素 + 紧随其后的 hashTree」表达父子结构，每个元素后必须跟一个 hashTree
 *   - 断言的属性名 `Asserion.test_strings` 是 JMeter 自身的历史拼写，必须原样保留
 *   - duration > 0 时启用调度器（按时间压测，循环设为无限）
 *   - URL 完整写入 HTTPSampler.path（JMeter 会自行解析协议/域名/端口），从而兼容 ${变量}
 */
import { escapeXml } from './xml.js'
import type { PerfAssertion, PerfCaseModel, PerfKeyValue, PerfStep } from './types.js'

const IND = (n: number) => '  '.repeat(n)

function props(list: PerfKeyValue[] | undefined): PerfKeyValue[] {
  return (list ?? []).filter((k) => k && k.key && k.enabled !== false)
}

/** User Defined Variables / Arguments 集合 */
function argumentCollection(list: PerfKeyValue[], level: number): string {
  const rows = props(list).map(
    (kv) =>
      [
        `${IND(level + 1)}<elementProp name="${escapeXml(kv.key)}" elementType="Argument">`,
        `${IND(level + 2)}<stringProp name="Argument.name">${escapeXml(kv.key)}</stringProp>`,
        `${IND(level + 2)}<stringProp name="Argument.value">${escapeXml(kv.value)}</stringProp>`,
        `${IND(level + 2)}<stringProp name="Argument.metadata">=</stringProp>`,
        `${IND(level + 1)}</elementProp>`,
      ].join('\n'),
  )
  const inner = rows.length ? `\n${rows.join('\n')}\n${IND(level)}` : ''
  return `${IND(level)}<collectionProp name="Arguments.arguments">${inner}</collectionProp>`
}

/** 响应断言（挂在采样器的 hashTree 下） */
function buildAssertion(a: PerfAssertion, level: number): string {
  const isCode = a.type === 'responseCode'
  const testField = isCode ? 'Assertion.response_code' : 'Assertion.response_data'
  // 1=matches 2=contains 8=equals 16=substring
  const testType = a.operator === 'equals' ? 8 : 2
  return [
    `${IND(level)}<ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="${escapeXml(isCode ? '响应码断言' : '响应文本断言')}" enabled="true">`,
    `${IND(level + 1)}<collectionProp name="Asserion.test_strings">`,
    `${IND(level + 2)}<stringProp name="0">${escapeXml(a.expected)}</stringProp>`,
    `${IND(level + 1)}</collectionProp>`,
    `${IND(level + 1)}<stringProp name="Assertion.custom_message"></stringProp>`,
    `${IND(level + 1)}<stringProp name="Assertion.test_field">${testField}</stringProp>`,
    `${IND(level + 1)}<boolProp name="Assertion.assume_success">false</boolProp>`,
    `${IND(level + 1)}<intProp name="Assertion.test_type">${testType}</intProp>`,
    `${IND(level)}</ResponseAssertion>`,
    `${IND(level)}<hashTree />`,
  ].join('\n')
}

/** 单个 HTTP 请求采样器（含其断言） */
function buildSampler(step: PerfStep, level: number): string {
  const method = (step.method || 'GET').toUpperCase()
  const headers = props(step.headers)
  const body = step.body && step.body.trim() ? step.body : undefined
  const lines: string[] = []

  lines.push(
    `${IND(level)}<HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="${escapeXml(step.name || 'HTTP请求')}" enabled="${step.enabled === false ? 'false' : 'true'}">`,
  )

  if (body) {
    // raw 请求体：单个匿名参数承载全部内容
    lines.push(`${IND(level + 1)}<boolProp name="HTTPSampler.postBodyRaw">true</boolProp>`)
    lines.push(
      `${IND(level + 1)}<elementProp name="HTTPsampler.Arguments" elementType="Arguments">`,
      `${IND(level + 2)}<collectionProp name="Arguments.arguments">`,
      `${IND(level + 3)}<elementProp name="" elementType="HTTPArgument">`,
      `${IND(level + 4)}<boolProp name="HTTPArgument.always_encode">false</boolProp>`,
      `${IND(level + 4)}<stringProp name="Argument.value">${escapeXml(body)}</stringProp>`,
      `${IND(level + 4)}<stringProp name="Argument.metadata">=</stringProp>`,
      `${IND(level + 3)}</elementProp>`,
      `${IND(level + 2)}</collectionProp>`,
      `${IND(level + 1)}</elementProp>`,
    )
  } else {
    const query = props(step.query ?? [])
    const rows = query.map(
      (kv) =>
        [
          `${IND(level + 3)}<elementProp name="${escapeXml(kv.key)}" elementType="HTTPArgument">`,
          `${IND(level + 4)}<boolProp name="HTTPArgument.always_encode">false</boolProp>`,
          `${IND(level + 4)}<stringProp name="Argument.value">${escapeXml(kv.value)}</stringProp>`,
          `${IND(level + 4)}<stringProp name="Argument.metadata">=</stringProp>`,
          `${IND(level + 4)}<boolProp name="HTTPArgument.use_equals">true</boolProp>`,
          `${IND(level + 4)}<stringProp name="Argument.name">${escapeXml(kv.key)}</stringProp>`,
          `${IND(level + 3)}</elementProp>`,
        ].join('\n'),
    )
    const inner = rows.length ? `\n${rows.join('\n')}\n${IND(level + 2)}` : ''
    lines.push(
      `${IND(level + 1)}<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">`,
      `${IND(level + 2)}<collectionProp name="Arguments.arguments">${inner}</collectionProp>`,
      `${IND(level + 1)}</elementProp>`,
    )
  }

  lines.push(
    `${IND(level + 1)}<stringProp name="HTTPSampler.domain"></stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.port"></stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.protocol"></stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.path">${escapeXml(step.url || '')}</stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.method">${escapeXml(method)}</stringProp>`,
    `${IND(level + 1)}<boolProp name="HTTPSampler.follow_redirects">true</boolProp>`,
    `${IND(level + 1)}<boolProp name="HTTPSampler.auto_redirects">false</boolProp>`,
    `${IND(level + 1)}<boolProp name="HTTPSampler.use_keepalive">true</boolProp>`,
    `${IND(level + 1)}<boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.embedded_url_re"></stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.connect_timeout"></stringProp>`,
    `${IND(level + 1)}<stringProp name="HTTPSampler.response_timeout"></stringProp>`,
    `${IND(level)}</HTTPSamplerProxy>`,
  )

  // 采样器的子节点：请求头 + 断言
  const children: string[] = []
  if (headers.length) {
    const rows = headers.map(
      (kv) =>
        [
          `${IND(level + 2)}<elementProp name="${escapeXml(kv.key)}" elementType="Header">`,
          `${IND(level + 3)}<stringProp name="Header.name">${escapeXml(kv.key)}</stringProp>`,
          `${IND(level + 3)}<stringProp name="Header.value">${escapeXml(kv.value)}</stringProp>`,
          `${IND(level + 2)}</elementProp>`,
        ].join('\n'),
    )
    children.push(
      [
        `${IND(level + 1)}<HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP信息头管理器" enabled="true">`,
        `${IND(level + 2)}<collectionProp name="HeaderManager.headers">`,
        ...rows,
        `${IND(level + 2)}</collectionProp>`,
        `${IND(level + 1)}</HeaderManager>`,
        `${IND(level + 1)}<hashTree />`,
      ].join('\n'),
    )
  }
  for (const a of step.assertions ?? []) {
    if (a && a.expected !== undefined && a.expected !== '') children.push(buildAssertion(a, level + 1))
  }

  lines.push(
    children.length
      ? `${IND(level)}<hashTree>\n${children.join('\n')}\n${IND(level)}</hashTree>`
      : `${IND(level)}<hashTree />`,
  )
  return lines.join('\n')
}

/** 循环控制器（AbstractThreadGroup 必需的子属性；循环数 -1 表示由线程组自身调度决定） */
function loopController(level: number, loops: number): string[] {
  return [
    `${IND(level)}<elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="循环控制器" enabled="true">`,
    `${IND(level + 1)}<boolProp name="Controller.master">true</boolProp>`,
    `${IND(level + 1)}<boolProp name="LoopController.continue_forever">false</boolProp>`,
    `${IND(level + 1)}<stringProp name="LoopController.loops">${loops}</stringProp>`,
    `${IND(level)}</elementProp>`,
  ]
}

/** 生成线程组元素本体（按加压方式选用不同元件，插件元件需运行时已装对应插件） */
function buildThreadGroupElement(model: PerfCaseModel, level: number): string[] {
  const threads = Math.max(1, Math.floor(model.threads || 1))
  const rampUp = Math.max(1, Math.floor(model.rampUp || 1))
  const duration = Math.max(0, Math.floor(model.duration || 0))
  const onSampleError = model.onSampleError || 'continue'
  const name = escapeXml(model.name || '线程组')

  if (model.loadProfile === 'stepping') {
    const s = model.stepping ?? {}
    // Stepping Thread Group（kg.apc）：按批次递增到目标并发后保持，键名为其显示名常量
    return [
      `${IND(level)}<kg.apc.jmeter.threads.SteppingThreadGroup guiclass="SteppingThreadGroupGui" testclass="kg.apc.jmeter.threads.SteppingThreadGroup" testname="${name}" enabled="true">`,
      `${IND(level + 1)}<stringProp name="ThreadGroup.on_sample_error">${onSampleError}</stringProp>`,
      `${IND(level + 1)}<stringProp name="ThreadGroup.num_threads">${threads}</stringProp>`,
      `${IND(level + 1)}<stringProp name="ThreadGroup.ramp_time">${rampUp}</stringProp>`,
      ...loopController(level + 1, -1),
      `${IND(level + 1)}<stringProp name="Threads initial delay">${Math.max(0, Math.floor(s.initialDelay ?? 0))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Start users count">${Math.max(1, Math.floor(s.batchThreads ?? 1))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Start users count burst">${Math.max(0, Math.floor(s.burstThreads ?? 0))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Start users period">${Math.max(1, Math.floor(s.batchInterval ?? 1))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Stop users count">0</stringProp>`,
      `${IND(level + 1)}<stringProp name="Stop users period">1</stringProp>`,
      `${IND(level + 1)}<stringProp name="flightTime">${Math.max(0, Math.floor(s.flightTime ?? 0))}</stringProp>`,
      `${IND(level)}</kg.apc.jmeter.threads.SteppingThreadGroup>`,
    ]
  }

  if (model.loadProfile === 'concurrency') {
    const c = model.concurrency ?? {}
    // Concurrency Thread Group（blazemeter）：按阶梯逼近目标并发并保持，键名为首字母大写常量
    return [
      `${IND(level)}<com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup guiclass="com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroupGui" testclass="com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup" testname="${name}" enabled="true">`,
      `${IND(level + 1)}<stringProp name="ThreadGroup.on_sample_error">${onSampleError}</stringProp>`,
      ...loopController(level + 1, -1),
      `${IND(level + 1)}<stringProp name="TargetLevel">${threads}</stringProp>`,
      `${IND(level + 1)}<stringProp name="RampUp">${rampUp}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Steps">${Math.max(1, Math.floor(c.steps ?? 1))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Hold">${Math.max(0, Math.floor(c.holdTarget ?? 0))}</stringProp>`,
      `${IND(level + 1)}<stringProp name="Unit">${c.unit ?? 'S'}</stringProp>`,
      `${IND(level + 1)}<stringProp name="IterationsLimit"></stringProp>`,
      `${IND(level + 1)}<stringProp name="LogFilename"></stringProp>`,
      `${IND(level)}</com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup>`,
    ]
  }

  // 默认：固定并发（JMeter 原生 ThreadGroup）
  const useScheduler = duration > 0
  const loops = useScheduler ? -1 : Math.max(1, Math.floor(model.loops || 1))
  return [
    `${IND(level)}<ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="${name}" enabled="true">`,
    `${IND(level + 1)}<stringProp name="ThreadGroup.on_sample_error">${onSampleError}</stringProp>`,
    ...loopController(level + 1, loops),
    `${IND(level + 1)}<stringProp name="ThreadGroup.num_threads">${threads}</stringProp>`,
    `${IND(level + 1)}<stringProp name="ThreadGroup.ramp_time">${rampUp}</stringProp>`,
    `${IND(level + 1)}<boolProp name="ThreadGroup.scheduler">${useScheduler ? 'true' : 'false'}</boolProp>`,
    `${IND(level + 1)}<stringProp name="ThreadGroup.duration">${useScheduler ? duration : ''}</stringProp>`,
    `${IND(level + 1)}<stringProp name="ThreadGroup.delay"></stringProp>`,
    `${IND(level + 1)}<boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>`,
    `${IND(level)}</ThreadGroup>`,
  ]
}

/** 生成一个线程组（含其子元素：采样器、定时器） */
function buildThreadGroupBlock(model: PerfCaseModel, level: number): string {
  const thinkTime = Math.max(0, Math.floor(model.thinkTime || 0))
  const steps = (model.steps ?? []).filter((s) => s && s.url !== undefined)

  const samplerBlocks = steps.map((s) => buildSampler(s, level + 1))
  if (thinkTime > 0) {
    samplerBlocks.push(
      [
        `${IND(level + 1)}<ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="固定定时器" enabled="true">`,
        `${IND(level + 2)}<stringProp name="ConstantTimer.delay">${thinkTime}</stringProp>`,
        `${IND(level + 1)}</ConstantTimer>`,
        `${IND(level + 1)}<hashTree />`,
      ].join('\n'),
    )
  }
  const threadGroupChildren = samplerBlocks.length
    ? `\n${samplerBlocks.join('\n')}\n${IND(level)}`
    : ''

  return [
    ...buildThreadGroupElement(model, level),
    threadGroupChildren
      ? `${IND(level)}<hashTree>${threadGroupChildren}</hashTree>`
      : `${IND(level)}<hashTree />`,
  ].join('\n')
}

/** 合并多个用例的自定义变量（按 key 去重，保留首次出现） */
function mergeVariables(models: PerfCaseModel[]): PerfKeyValue[] {
  const seen = new Map<string, PerfKeyValue>()
  for (const m of models) {
    for (const kv of props(m.variables)) {
      if (!seen.has(kv.key)) seen.set(kv.key, kv)
    }
  }
  return [...seen.values()]
}

/** 生成包含若干线程组的完整 .jmx */
export function buildJmxMulti(models: PerfCaseModel[], planName?: string): string {
  const list = models.length ? models : []
  const udv = argumentCollection(mergeVariables(list), 3)
  const threadGroups = list.map((m) => buildThreadGroupBlock(m, 3))

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.2.1">',
    `${IND(1)}<hashTree>`,
    [
      `${IND(2)}<TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="${escapeXml(planName || list[0]?.name || '性能测试计划')}" enabled="true">`,
      `${IND(3)}<stringProp name="TestPlan.comments">由 Api-web 性能测试模块生成</stringProp>`,
      `${IND(3)}<boolProp name="TestPlan.functional_mode">false</boolProp>`,
      `${IND(3)}<boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>`,
      `${IND(3)}<boolProp name="TestPlan.serialize_threadgroups">false</boolProp>`,
      `${IND(3)}<elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">`,
      udv,
      `${IND(3)}</elementProp>`,
      `${IND(3)}<stringProp name="TestPlan.user_define_classpath"></stringProp>`,
      `${IND(2)}</TestPlan>`,
    ].join('\n'),
    threadGroups.length
      ? `${IND(2)}<hashTree>\n${threadGroups.join('\n')}\n${IND(2)}</hashTree>`
      : `${IND(2)}<hashTree />`,
    `${IND(1)}</hashTree>`,
    '</jmeterTestPlan>',
    '',
  ].join('\n')
}

/** 生成单个用例对应的完整 .jmx 文本 */
export function buildJmx(model: PerfCaseModel): string {
  return buildJmxMulti([model], model.name)
}
