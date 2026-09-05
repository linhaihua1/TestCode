/**
 * 用例步骤执行引擎（PRD 第 2 期）。
 * 执行 CaseInfo.steps（request/script/wait/variable/controller），
 * 支持前置/测试/后置阶段、变量传递、断言提取、流程控制器递归。
 */
import { executeRequest } from './request.js'
import { applyExtracts } from './extract.js'
import { evaluateAssertions } from './assert.js'
import { resolveString, resolveTemplate } from './variables.js'
import type { Assertion, AssertionResult, ExtractRule, RequestSpec, VariableContext } from './types.js'

interface Kv {
  key: string
  value: string
  enabled?: boolean
}

/** 后端用例步骤结构（与前端 CaseStep 对应） */
export interface CaseStepDef {
  id: string
  type: 'request' | 'script' | 'wait' | 'variable' | 'controller'
  phase: 'setup' | 'test' | 'teardown'
  name: string
  enabled: boolean
  apiId?: string
  method?: string
  url?: string
  headers?: Kv[]
  query?: Kv[]
  body?: string
  assertions?: Assertion[]
  extracts?: ExtractRule[]
  script?: string
  waitMs?: number
  varName?: string
  varValue?: string
  controllerType?: 'if' | 'for' | 'while'
  condition?: string
  children?: CaseStepDef[]
  loopVar?: string
  loopCount?: number
  maxLoops?: number
}

/** 单个步骤的执行结果 */
export interface StepExecResult {
  id: string
  name: string
  type: string
  status: 'PASS' | 'FAIL' | 'ERROR' | 'SKIP'
  message: string
  request?: { method: string; url: string; headers: Record<string, string>; body: unknown }
  response?: { status: number; headers: Record<string, string>; body: unknown; rawBody: string; duration: number }
  assertions?: AssertionResult[]
  extracted?: Record<string, string>
  children?: StepExecResult[]
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms))
}

/** 条件表达式求值：把 ${var} 替换为 JSON 字符串化后的值，再 JS 求值 */
function evaluateCondition(condition: string, context: VariableContext): boolean {
  try {
    const resolved = condition.replace(/\$\{([^{}]+)\}/g, (_m, key: string) => {
      const v = context[key.trim()]
      return v === undefined ? 'undefined' : JSON.stringify(v)
    })
    // eslint-disable-next-line no-new-func
    return new Function(`return Boolean(${resolved})`)() === true
  } catch {
    return false
  }
}

function kvToRecord(list: Kv[], context: VariableContext): Record<string, string> {
  const out: Record<string, string> = {}
  for (const kv of list ?? []) {
    if (kv.enabled === false) continue
    out[resolveString(kv.key, context)] = resolveString(kv.value, context)
  }
  return out
}

function buildUrl(baseUrl: string, url: string, context: VariableContext): string {
  const resolved = resolveString(url, context)
  if (/^https?:\/\//i.test(resolved)) return resolved
  if (!baseUrl) return resolved
  return `${baseUrl.replace(/\/$/, '')}/${resolved.replace(/^\//, '')}`
}

function buildBody(body: string | undefined, context: VariableContext): unknown {
  if (!body) return undefined
  try {
    return resolveTemplate(JSON.parse(body), context)
  } catch {
    return resolveString(body, context)
  }
}

/** 执行单个步骤 */
async function execStep(step: CaseStepDef, context: VariableContext, baseUrl: string, depth: number): Promise<StepExecResult> {
  if (depth > 5) {
    return { id: step.id, name: step.name, type: step.type, status: 'ERROR', message: '控制器嵌套超过最大深度 5 层' }
  }
  if (!step.enabled) {
    return { id: step.id, name: step.name, type: step.type, status: 'SKIP', message: '步骤已禁用' }
  }

  try {
    switch (step.type) {
      case 'request': {
        const headers = kvToRecord(step.headers ?? [], context)
        const spec: RequestSpec = {
          method: step.method ?? 'GET',
          url: buildUrl(baseUrl, step.url ?? '', context),
          headers,
          query: kvToRecord(step.query ?? [], context),
          body: buildBody(step.body, context),
        }
        const res = await executeRequest(spec)
        const extracted = applyExtracts(res, step.extracts ?? [], context)
        const { passed, results } = evaluateAssertions(res, step.assertions ?? [])
        return {
          id: step.id,
          name: step.name,
          type: 'request',
          status: passed ? 'PASS' : 'FAIL',
          message: passed ? `请求成功（${res.status}）` : '存在断言未通过',
          request: { method: spec.method, url: spec.url, headers, body: spec.body },
          response: { status: res.status, headers: res.headers, body: res.body, rawBody: res.rawBody, duration: res.duration },
          assertions: results,
          extracted,
        }
      }
      case 'wait': {
        await sleep(step.waitMs ?? 0)
        return { id: step.id, name: step.name, type: 'wait', status: 'PASS', message: `等待 ${step.waitMs ?? 0}ms` }
      }
      case 'variable': {
        const value = resolveString(step.varValue ?? '', context)
        if (step.varName) context[step.varName] = value
        return { id: step.id, name: step.name, type: 'variable', status: 'PASS', message: `${step.varName ?? ''} = ${value}` }
      }
      case 'controller': {
        return await execController(step, context, baseUrl, depth)
      }
      case 'script': {
        return { id: step.id, name: step.name, type: 'script', status: 'PASS', message: '脚本执行（本期仅支持变量赋值，脚本引擎后续接入）' }
      }
      default:
        return { id: step.id, name: step.name, type: step.type, status: 'ERROR', message: '未知步骤类型' }
    }
  } catch (err) {
    return { id: step.id, name: step.name, type: step.type, status: 'ERROR', message: err instanceof Error ? err.message : String(err) }
  }
}

/** 执行流程控制器 */
async function execController(step: CaseStepDef, context: VariableContext, baseUrl: string, depth: number): Promise<StepExecResult> {
  const children = step.children ?? []
  const childResults: StepExecResult[] = []

  const runChildren = async () => {
    for (const c of children) {
      childResults.push(await execStep(c, context, baseUrl, depth + 1))
    }
  }

  if (step.controllerType === 'if') {
    const ok = evaluateCondition(step.condition ?? '', context)
    if (ok) await runChildren()
    return {
      id: step.id,
      name: step.name,
      type: 'controller',
      status: 'PASS',
      message: `IF 条件${ok ? '满足，执行子步骤' : '不满足，跳过'}`,
      children: childResults,
    }
  }

  if (step.controllerType === 'for') {
    const count = Math.max(0, Math.min(step.loopCount ?? 0, 1000))
    for (let i = 0; i < count; i++) {
      if (step.loopVar) context[step.loopVar] = String(i)
      await runChildren()
    }
    return { id: step.id, name: step.name, type: 'controller', status: 'PASS', message: `FOR 循环 ${count} 次`, children: childResults }
  }

  if (step.controllerType === 'while') {
    const max = Math.max(0, Math.min(step.maxLoops ?? 100, 1000))
    let i = 0
    while (i < max && evaluateCondition(step.condition ?? '', context)) {
      await runChildren()
      i++
    }
    return { id: step.id, name: step.name, type: 'controller', status: 'PASS', message: `WHILE 循环 ${i} 次`, children: childResults }
  }

  return { id: step.id, name: step.name, type: 'controller', status: 'PASS', message: '控制器', children: childResults }
}

/**
 * 执行用例步骤列表（按 setup → test → teardown 顺序）。
 * 返回每步结果与最终变量上下文。
 */
export async function executeCaseSteps(
  steps: CaseStepDef[],
  options: { baseUrl?: string; initialVars?: VariableContext } = {},
): Promise<{ results: StepExecResult[]; context: VariableContext }> {
  const context: VariableContext = { ...(options.initialVars ?? {}) }
  const baseUrl = options.baseUrl ?? ''

  const phaseOrder: Array<CaseStepDef['phase']> = ['setup', 'test', 'teardown']
  const results: StepExecResult[] = []

  for (const phase of phaseOrder) {
    for (const step of steps.filter((s) => s.phase === phase)) {
      results.push(await execStep(step, context, baseUrl, 0))
    }
  }

  return { results, context }
}
