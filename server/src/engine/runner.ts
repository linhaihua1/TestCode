/**
 * 场景执行编排模块（核心）。
 * 负责串联整个执行流程：加载场景与环境 → 初始化变量上下文 →
 * 逐步骤替换变量 → 发 HTTP 请求 → 提取 → 断言 → 汇总结果并落库生成报告。
 */
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import type {
  Assertion,
  AssertionResult,
  ExtractRule,
  RequestSpec,
  StepResult,
  VariableContext,
} from './types.js'
import { resolveString, resolveTemplate } from './variables.js'
import { executeRequest } from './request.js'
import { applyExtracts } from './extract.js'
import { evaluateAssertions } from './assert.js'

/** 键值对结构：接口请求头/查询参数、环境变量等均以此存储 */
interface KeyValue {
  key: string
  value: string
  /** 是否启用，为 false 时跳过 */
  enabled?: boolean
}

/** 把 KeyValue 列表转成对象，键值均做变量替换，并跳过禁用项 */
function kvToRecord(list: KeyValue[], context: VariableContext): Record<string, string> {
  const out: Record<string, string> = {}
  for (const kv of list) {
    if (kv.enabled === false) continue // 跳过禁用的键值对
    out[resolveString(kv.key, context)] = resolveString(kv.value, context) // 键值都替换变量
  }
  return out
}

/** 拼接完整 URL：path 已是绝对地址则直接使用，否则拼到 baseUrl 后面 */
function buildUrl(baseUrl: string, path: string, context: VariableContext): string {
  const resolved = resolveString(path, context) // 先替换路径中的变量
  if (/^https?:\/\//i.test(resolved)) return resolved // 绝对地址直接返回
  // 去掉 baseUrl 末尾斜杠与 path 开头斜杠后再拼接，避免出现双斜杠
  return `${baseUrl.replace(/\/$/, '')}/${resolved.replace(/^\//, '')}`
}

/** 构造请求体：JSON 字符串解析后递归替换变量；解析失败则整段做字符串替换 */
function buildBody(body: string | null, context: VariableContext): unknown {
  if (!body) return undefined // 无请求体返回 undefined
  try {
    const parsed = JSON.parse(body) // 尝试解析 JSON
    return resolveTemplate(parsed, context) // 对 JSON 结构递归替换变量
  } catch {
    return resolveString(body, context) // 非 JSON 文本则整体字符串替换
  }
}

/** 已加载的接口信息（用于构造请求） */
interface LoadedApi {
  method: string
  path: string
  headers: KeyValue[]
  query: KeyValue[]
  body: string | null
}

/** 已加载的接口用例信息（承载断言与提取规则） */
interface LoadedCase {
  name: string
  assertions: Assertion[]
  extracts: ExtractRule[]
}

/**
 * 执行单个步骤：替换变量 → 发请求 → 提取 → 断言，返回步骤结果。
 * 任何异常都归入 ERROR 状态，不中断整个场景。
 */
async function executeStep(
  step: { id: string; name: string | null; assertions: Assertion[]; extracts: ExtractRule[] },
  apiCase: LoadedCase | null,
  api: LoadedApi | null,
  context: VariableContext,
  envHeaders: KeyValue[],
  baseUrl: string,
): Promise<StepResult> {
  const name = step.name ?? apiCase?.name ?? '未命名步骤' // 步骤名：优先步骤自身，其次用例名，最后兜底
  const started = Date.now() // 记录步骤开始时间

  if (!api) {
    // 步骤未绑定接口，直接返回 ERROR
    return {
      stepId: step.id,
      name,
      status: 'ERROR',
      error: '步骤未绑定接口用例',
      assertions: [],
      extracted: {},
      duration: Date.now() - started,
    }
  }

  try {
    // 合并环境级与接口级请求头（接口级覆盖环境级）
    const headers = {
      ...kvToRecord(envHeaders, context),
      ...kvToRecord(api.headers, context),
    }
    // 组装最终请求规格（变量替换后的最终值）
    const spec: RequestSpec = {
      method: api.method,
      url: buildUrl(baseUrl, api.path, context),
      headers,
      query: kvToRecord(api.query, context),
      body: buildBody(api.body, context),
    }

    const res = await executeRequest(spec) // 发 HTTP 请求

    // 合并用例级与步骤级提取规则后统一执行
    const extracts: ExtractRule[] = [...(apiCase?.extracts ?? []), ...step.extracts]
    const extracted = applyExtracts(res, extracts, context)

    // 合并用例级与步骤级断言后统一判定
    const assertions: Assertion[] = [...(apiCase?.assertions ?? []), ...step.assertions]
    const { passed, results } = evaluateAssertions(res, assertions)

    return {
      stepId: step.id,
      name,
      status: passed ? 'PASS' : 'FAIL',
      assertions: results,
      extracted,
      duration: Date.now() - started,
    }
  } catch (err) {
    // 请求异常（网络错误/超时等）：记录错误信息为 ERROR
    return {
      stepId: step.id,
      name,
      status: 'ERROR',
      error: err instanceof Error ? err.message : String(err),
      assertions: [],
      extracted: {},
      duration: Date.now() - started,
    }
  }
}

/** 执行场景的入参 */
export interface RunScenarioInput {
  scenarioId: string
  environmentId: string
}

/**
 * 执行整个测试场景：
 * 加载场景与环境 → 初始化变量上下文 → 逐步骤执行 → 汇总状态 → 生成报告落库。
 */
export async function runScenario(input: RunScenarioInput) {
  // 加载场景及其步骤、每个步骤绑定的接口用例与接口
  const scenario = await prisma.scenario.findUnique({
    where: { id: input.scenarioId },
    include: {
      steps: {
        orderBy: { order: 'asc' }, // 按步骤顺序字段升序执行
        include: { apiCase: { include: { api: true } } },
      },
    },
  })
  if (!scenario) throw new Error(`场景不存在: ${input.scenarioId}`)

  // 加载执行环境
  const env = await prisma.environment.findUnique({ where: { id: input.environmentId } })
  if (!env) throw new Error(`环境不存在: ${input.environmentId}`)

  // 初始化变量上下文：先把环境变量写入
  const context: VariableContext = {}
  for (const kv of env.variables as unknown as KeyValue[]) {
    context[kv.key] = kv.value
  }

  // 环境级请求头与基础 URL
  const envHeaders = (env.headers as unknown as KeyValue[]) ?? []
  const baseUrl = env.baseUrl ?? ''

  const started = Date.now() // 记录场景开始时间
  const stepResults: StepResult[] = []

  // 逐步骤顺序执行，变量上下文在步骤间传递（前一步提取的变量可供后续步骤使用）
  for (const step of scenario.steps) {
    const apiCase = step.apiCase
    const api = apiCase?.api ?? null
    const result = await executeStep(
      {
        id: step.id,
        name: step.name,
        assertions: (step.assertions as unknown as Assertion[]) ?? [],
        extracts: (step.extracts as unknown as ExtractRule[]) ?? [],
      },
      apiCase
        ? { name: apiCase.name, assertions: (apiCase.assertions as unknown as Assertion[]) ?? [], extracts: (apiCase.extracts as unknown as ExtractRule[]) ?? [] }
        : null,
      api
        ? {
            method: api.method,
            path: api.path,
            headers: (api.headers as unknown as KeyValue[]) ?? [],
            query: (api.query as unknown as KeyValue[]) ?? [],
            body: api.body,
          }
        : null,
      context,
      envHeaders,
      baseUrl,
    )
    stepResults.push(result)
  }

  const duration = Date.now() - started
  // 汇总场景整体状态：全部 PASS 则 PASS，存在 ERROR 则 ERROR，否则 FAIL
  const overallStatus: 'PASS' | 'FAIL' | 'ERROR' = stepResults.every((r) => r.status === 'PASS')
    ? 'PASS'
    : stepResults.some((r) => r.status === 'ERROR')
      ? 'ERROR'
      : 'FAIL'

  // 生成执行报告并落库，同时保存每个步骤的详情
  const report = await prisma.report.create({
    data: {
      projectId: scenario.projectId,
      scenarioId: scenario.id,
      name: scenario.name,
      status: overallStatus,
      duration,
      details: {
        create: stepResults.map((r) => ({
          stepName: r.name,
          status: r.status,
          error: r.error,
          assertions: r.assertions as unknown as Prisma.InputJsonValue,
          extracts: r.extracted as unknown as Prisma.InputJsonValue,
        })),
      },
    },
    include: { details: true },
  })

  return { report, context }
}

/** 单用例调试结果：请求、响应、提取、断言 */
export interface DebugResult {
  request: RequestSpec
  response: {
    status: number
    headers: Record<string, string>
    body: unknown
    rawBody: string
    duration: number
  }
  extracted: Record<string, string>
  assertions: AssertionResult[]
  passed: boolean
}

/**
 * 调试单个接口用例：按接口定义发送请求，执行提取与断言，
 * 返回完整响应、提取到的变量与断言结果，供前端即时查看。
 * 可选传入环境 ID 以使用其 baseUrl / 变量 / 公共请求头。
 */
export async function debugCase(caseId: string, environmentId?: string): Promise<DebugResult> {
  const apiCase = await prisma.apiCase.findUnique({ where: { id: caseId }, include: { api: true } })
  if (!apiCase) throw new Error('用例不存在')

  const context: VariableContext = {}
  let baseUrl = ''
  let envHeaders: KeyValue[] = []

  // 加载环境（可选）：提供 baseUrl、变量、公共请求头
  if (environmentId) {
    const env = await prisma.environment.findUnique({ where: { id: environmentId } })
    if (env) {
      baseUrl = env.baseUrl ?? ''
      for (const kv of (env.variables as unknown as KeyValue[]) ?? []) context[kv.key] = kv.value
      envHeaders = (env.headers as unknown as KeyValue[]) ?? []
    }
  }

  const api = apiCase.api
  const headers = {
    ...kvToRecord(envHeaders, context),
    ...kvToRecord((api.headers as unknown as KeyValue[]) ?? [], context),
  }
  const spec: RequestSpec = {
    method: api.method,
    url: buildUrl(baseUrl, api.path, context),
    headers,
    query: kvToRecord((api.query as unknown as KeyValue[]) ?? [], context),
    body: buildBody(api.body, context),
  }

  const res = await executeRequest(spec)
  const extracted = applyExtracts(res, (apiCase.extracts as unknown as ExtractRule[]) ?? [], context)
  const { passed, results } = evaluateAssertions(res, (apiCase.assertions as unknown as Assertion[]) ?? [])

  return {
    request: spec,
    response: res,
    extracted,
    assertions: results,
    passed,
  }
}

/** 用例内部步骤定义（多步骤） */
interface CaseStepDef {
  apiId: string
  name?: string
  assertions?: Assertion[]
  extracts?: ExtractRule[]
}

/**
 * 独立运行一个接口用例：支持多步骤（stepDefs）或单接口（向后兼容）。
 * 多步骤时按顺序执行，变量在步骤间传递；最终生成报告（scenarioId 为空表示用例独立运行）。
 */
export async function runCase(caseId: string, environmentId?: string) {
  const apiCase = await prisma.apiCase.findUnique({ where: { id: caseId }, include: { api: true } })
  if (!apiCase) throw new Error('用例不存在')

  // 加载环境（可选）
  const context: VariableContext = {}
  let baseUrl = ''
  let envHeaders: KeyValue[] = []
  if (environmentId) {
    const env = await prisma.environment.findUnique({ where: { id: environmentId } })
    if (env) {
      baseUrl = env.baseUrl ?? ''
      for (const kv of (env.variables as unknown as KeyValue[]) ?? []) context[kv.key] = kv.value
      envHeaders = (env.headers as unknown as KeyValue[]) ?? []
    }
  }

  const stepDefs = (apiCase.stepDefs as unknown as CaseStepDef[]) ?? []
  const started = Date.now()
  const stepResults: StepResult[] = []

  if (stepDefs.length > 0) {
    // 多步骤：每步引用一个接口，按顺序执行并传递变量
    for (const def of stepDefs) {
      const api = await prisma.apiDefinition.findUnique({ where: { id: def.apiId } })
      const result = await executeStep(
        { id: def.apiId, name: def.name ?? api?.name ?? '步骤', assertions: def.assertions ?? [], extracts: def.extracts ?? [] },
        null,
        api
          ? {
              method: api.method,
              path: api.path,
              headers: (api.headers as unknown as KeyValue[]) ?? [],
              query: (api.query as unknown as KeyValue[]) ?? [],
              body: api.body,
            }
          : null,
        context,
        envHeaders,
        baseUrl,
      )
      stepResults.push(result)
    }
  } else {
    // 单接口用例（向后兼容）：用主接口 + 用例级断言/提取
    const api = apiCase.api
    const result = await executeStep(
      {
        id: apiCase.id,
        name: apiCase.name,
        assertions: (apiCase.assertions as unknown as Assertion[]) ?? [],
        extracts: (apiCase.extracts as unknown as ExtractRule[]) ?? [],
      },
      {
        name: apiCase.name,
        assertions: (apiCase.assertions as unknown as Assertion[]) ?? [],
        extracts: (apiCase.extracts as unknown as ExtractRule[]) ?? [],
      },
      api
        ? {
            method: api.method,
            path: api.path,
            headers: (api.headers as unknown as KeyValue[]) ?? [],
            query: (api.query as unknown as KeyValue[]) ?? [],
            body: api.body,
          }
        : null,
      context,
      envHeaders,
      baseUrl,
    )
    stepResults.push(result)
  }

  const duration = Date.now() - started
  const status = stepResults.every((r) => r.status === 'PASS')
    ? 'PASS'
    : stepResults.some((r) => r.status === 'ERROR')
      ? 'ERROR'
      : 'FAIL'

  // 落库报告（scenarioId 为空表示用例独立运行）
  const report = await prisma.report.create({
    data: {
      projectId: apiCase.api.projectId,
      scenarioId: null,
      name: apiCase.name,
      status,
      duration,
      details: {
        create: stepResults.map((r) => ({
          stepName: r.name,
          status: r.status,
          error: r.error,
          assertions: r.assertions as unknown as Prisma.InputJsonValue,
          extracts: r.extracted as unknown as Prisma.InputJsonValue,
        })),
      },
    },
    include: { details: true },
  })

  return { report, context }
}
