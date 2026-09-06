/**
 * JMeter .jmx → 用例模型解析器。
 *
 * JMeter 的父子关系靠「元素节点 + 紧随其后的 hashTree」表达：
 *   <HTTPSamplerProxy/><hashTree>...该采样器的 HeaderManager / ResponseAssertion...</hashTree>
 * 本解析器先建立该映射，再从中还原请求头与断言。
 */
import { findAll, getCollection, getElementProp, getProp, parseXml, decodeEntities, type XmlNode } from './xml.js'
import type { PerfAssertion, PerfCaseModel, PerfKeyValue, PerfStep } from './types.js'

/** 建立「元素 → 其子元素列表」映射（依据 hashTree 约定） */
function buildChildMap(root: XmlNode): Map<XmlNode, XmlNode[]> {
  const map = new Map<XmlNode, XmlNode[]>()
  const walk = (n: XmlNode) => {
    for (let i = 0; i < n.children.length; i++) {
      const el = n.children[i]
      const next = n.children[i + 1]
      if (el.name !== 'hashTree' && next && next.name === 'hashTree') {
        map.set(el, next.children)
      }
      walk(el)
    }
  }
  walk(root)
  return map
}

/** 收集某元素下（含其子 hashTree）指定类型的属性键值 */
function readArguments(collectionHost: XmlNode | undefined, nameKey: string, valueKey: string): PerfKeyValue[] {
  if (!collectionHost) return []
  const out: PerfKeyValue[] = []
  const args = getElementProp(collectionHost, 'HTTPsampler.Arguments') ?? getElementProp(collectionHost, 'TestPlan.user_defined_variables')
  const host = args ?? collectionHost
  for (const el of getCollection(host, 'Arguments.arguments')) {
    const key = getProp(el, nameKey) ?? (el.attrs.name ?? '')
    const value = getProp(el, valueKey) ?? getProp(el, 'Argument.value') ?? ''
    if (key) out.push({ key, value, enabled: true })
  }
  return out
}

/** 解析 HeaderManager（可能位于采样器子节点或线程组子节点） */
function readHeaders(headerManagers: XmlNode[]): PerfKeyValue[] {
  const out: PerfKeyValue[] = []
  for (const hm of headerManagers) {
    for (const el of getCollection(hm, 'HeaderManager.headers')) {
      const key = getProp(el, 'Header.name') ?? el.attrs.name ?? ''
      const value = getProp(el, 'Header.value') ?? ''
      if (key) out.push({ key, value, enabled: true })
    }
  }
  return out
}

/** 解析 ResponseAssertion → 断言模型 */
function readAssertions(nodes: XmlNode[]): PerfAssertion[] {
  const out: PerfAssertion[] = []
  for (const n of nodes) {
    if (n.name !== 'ResponseAssertion') continue
    const field = getProp(n, 'Assertion.test_field') ?? ''
    const type: PerfAssertion['type'] = field.includes('response_code') ? 'responseCode' : 'responseText'
    const testType = Number(getProp(n, 'Assertion.test_type') ?? '2')
    // 位标志：1=matches 2=contains 8=equals 16=substring
    const operator: PerfAssertion['operator'] = testType === 8 ? 'equals' : 'contains'
    const strings = getCollection(n, 'Asserion.test_strings')
    const expected = (strings[0]?.text ?? '').trim()
    if (expected) out.push({ type, operator, expected })
  }
  return out
}

/** 沿 hashTree 父子映射收集某元素的全部后代（用于把采样器限定在线程组子树内） */
function descendantsOf(node: XmlNode, childMap: Map<XmlNode, XmlNode[]>): XmlNode[] {
  const out: XmlNode[] = []
  const seen = new Set<XmlNode>()
  const walk = (n: XmlNode) => {
    for (const c of childMap.get(n) ?? []) {
      if (seen.has(c)) continue
      seen.add(c)
      out.push(c)
      walk(c)
    }
  }
  walk(node)
  return out
}

/** 从 URL 还原 JMeter 的 protocol/domain/port/path（若为完整 URL） */
function samplerUrl(el: XmlNode): string {
  const path = getProp(el, 'HTTPSampler.path') ?? ''
  const domain = getProp(el, 'HTTPSampler.domain') ?? ''
  if (!domain) return decodeEntities(path)
  const protocol = getProp(el, 'HTTPSampler.protocol') || 'http'
  const port = getProp(el, 'HTTPSampler.port') ?? ''
  const suffix = path.startsWith('/') || path === '' ? path : `/${path}`
  return `${protocol}://${domain}${port ? `:${port}` : ''}${suffix}`
}

/** 解析 .jmx 文本为用例模型；无法识别时抛错 */
export function parseJmx(xml: string): PerfCaseModel {
  if (!xml || !xml.includes('jmeterTestPlan')) {
    throw new Error('不是有效的 JMeter 测试计划（缺少 jmeterTestPlan 根节点）')
  }
  const root = parseXml(xml)
  const childMap = buildChildMap(root)
  const childrenOf = (n: XmlNode) => childMap.get(n) ?? []

  const plan = findAll(root, 'TestPlan')[0]
  const name = (plan?.attrs.testname || '导入的压测用例').trim()

  // 用户自定义变量
  const variables = plan ? readArguments(plan, 'Argument.name', 'Argument.value') : []

  // 线程组参数（取第一个；没有线程组的计划无法压测）
  const tg = findAll(root, 'ThreadGroup')[0]
  if (!tg) throw new Error('该 JMeter 计划中没有线程组（Thread Group），无法导入为压测用例')
  const threads = Number(getProp(tg, 'ThreadGroup.num_threads') ?? 1) || 1
  const rampUp = Number(getProp(tg, 'ThreadGroup.ramp_time') ?? 1) || 1
  const scheduler = (getProp(tg, 'ThreadGroup.scheduler') ?? 'false') === 'true'
  const duration = scheduler ? Number(getProp(tg, 'ThreadGroup.duration') ?? 0) || 0 : 0
  const controller = getElementProp(tg, 'ThreadGroup.main_controller')
  let loops = Number(controller ? getProp(controller, 'LoopController.loops') : 1) || 1
  if (loops < 0) loops = 1 // 按时间压测时循环为无限
  const onSampleError = (getProp(tg, 'ThreadGroup.on_sample_error') ?? 'continue') as PerfCaseModel['onSampleError']

  // 仅取该线程组子树内的元素：多线程组计划导入时不会混入其它线程组的请求
  const scope = descendantsOf(tg, childMap)

  // 思考时间（固定定时器）
  const timer = scope.find((n) => n.name === 'ConstantTimer') ?? findAll(root, 'ConstantTimer')[0]
  const thinkTime = timer ? Number(getProp(timer, 'ConstantTimer.delay') ?? 0) || 0 : 0

  // 请求步骤（HTTP 采样器）
  const samplers = scope.filter((n) => n.name === 'HTTPSamplerProxy')
  const steps: PerfStep[] = samplers.map((el, i) => {
    const kids = childrenOf(el)
    const rawBody = (getProp(el, 'HTTPSampler.postBodyRaw') ?? 'false') === 'true'
    let body: string | undefined
    let query: PerfKeyValue[] = []
    if (rawBody) {
      const argsEl = getElementProp(el, 'HTTPsampler.Arguments')
      const first = argsEl ? getCollection(argsEl, 'Arguments.arguments')[0] : undefined
      const v = first ? getProp(first, 'Argument.value') ?? '' : ''
      body = v || undefined
    } else {
      query = readArguments(el, 'Argument.name', 'Argument.value')
    }
    return {
      id: `imported-${i + 1}`,
      name: (el.attrs.testname || `请求 ${i + 1}`).trim(),
      method: (getProp(el, 'HTTPSampler.method') ?? 'GET').toUpperCase(),
      url: samplerUrl(el),
      headers: readHeaders(kids.filter((k) => k.name === 'HeaderManager')),
      query,
      body,
      assertions: readAssertions(kids),
      enabled: el.attrs.enabled !== 'false',
    }
  })

  return { name, threads, rampUp, loops, duration, thinkTime, onSampleError, variables, steps }
}
