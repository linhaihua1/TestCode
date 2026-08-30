import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import type {
  Assertion,
  ExtractRule,
  RequestSpec,
  StepResult,
  VariableContext,
} from './types.js'
import { resolveString, resolveTemplate } from './variables.js'
import { executeRequest } from './request.js'
import { applyExtracts } from './extract.js'
import { evaluateAssertions } from './assert.js'

interface KeyValue {
  key: string
  value: string
  enabled?: boolean
}

function kvToRecord(list: KeyValue[], context: VariableContext): Record<string, string> {
  const out: Record<string, string> = {}
  for (const kv of list) {
    if (kv.enabled === false) continue
    out[resolveString(kv.key, context)] = resolveString(kv.value, context)
  }
  return out
}

function buildUrl(baseUrl: string, path: string, context: VariableContext): string {
  const resolved = resolveString(path, context)
  if (/^https?:\/\//i.test(resolved)) return resolved
  return `${baseUrl.replace(/\/$/, '')}/${resolved.replace(/^\//, '')}`
}

function buildBody(body: string | null, context: VariableContext): unknown {
  if (!body) return undefined
  try {
    const parsed = JSON.parse(body)
    return resolveTemplate(parsed, context)
  } catch {
    return resolveString(body, context)
  }
}

interface LoadedApi {
  method: string
  path: string
  headers: KeyValue[]
  query: KeyValue[]
  body: string | null
}

interface LoadedCase {
  name: string
  assertions: Assertion[]
  extracts: ExtractRule[]
}

async function executeStep(
  step: { id: string; name: string | null; assertions: Assertion[]; extracts: ExtractRule[] },
  apiCase: LoadedCase | null,
  api: LoadedApi | null,
  context: VariableContext,
  envHeaders: KeyValue[],
  baseUrl: string,
): Promise<StepResult> {
  const name = step.name ?? apiCase?.name ?? '未命名步骤'
  const started = Date.now()

  if (!api) {
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
    const headers = {
      ...kvToRecord(envHeaders, context),
      ...kvToRecord(api.headers, context),
    }
    const spec: RequestSpec = {
      method: api.method,
      url: buildUrl(baseUrl, api.path, context),
      headers,
      query: kvToRecord(api.query, context),
      body: buildBody(api.body, context),
    }

    const res = await executeRequest(spec)

    const extracts: ExtractRule[] = [...(apiCase?.extracts ?? []), ...step.extracts]
    const extracted = applyExtracts(res, extracts, context)

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

export interface RunScenarioInput {
  scenarioId: string
  environmentId: string
}

export async function runScenario(input: RunScenarioInput) {
  const scenario = await prisma.scenario.findUnique({
    where: { id: input.scenarioId },
    include: {
      steps: {
        orderBy: { order: 'asc' },
        include: { apiCase: { include: { api: true } } },
      },
    },
  })
  if (!scenario) throw new Error(`场景不存在: ${input.scenarioId}`)

  const env = await prisma.environment.findUnique({ where: { id: input.environmentId } })
  if (!env) throw new Error(`环境不存在: ${input.environmentId}`)

  const context: VariableContext = {}
  for (const kv of env.variables as unknown as KeyValue[]) {
    context[kv.key] = kv.value
  }

  const envHeaders = (env.headers as unknown as KeyValue[]) ?? []
  const baseUrl = env.baseUrl ?? ''

  const started = Date.now()
  const stepResults: StepResult[] = []

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
  const overallStatus: 'PASS' | 'FAIL' | 'ERROR' = stepResults.every((r) => r.status === 'PASS')
    ? 'PASS'
    : stepResults.some((r) => r.status === 'ERROR')
      ? 'ERROR'
      : 'FAIL'

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
