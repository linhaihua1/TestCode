import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { executeCaseSteps, type CaseStepDef } from './case-executor.js'
import { buildMergedContext, loadGlobalVariables } from './resolver.js'

/**
 * 用例调试执行（供调试路由与一键重放共用）。
 * 加载环境/全局变量 → 执行步骤 → 记录 DebugRecord → 返回结果。
 */
export interface DebugRunResult {
  status: 'success' | 'fail' | 'error'
  duration: number
  results: unknown[]
  variables: Record<string, string>
  truncated: boolean
  recordId: string
}

export async function runCaseDebug(
  caseId: string,
  opts: { environmentId?: string | null; debugVars?: Record<string, string> } = {},
): Promise<DebugRunResult> {
  const c = await prisma.caseInfo.findUnique({ where: { id: caseId } })
  if (!c) throw new Error('用例不存在')

  let envVars: Record<string, string> = {}
  let baseUrl = ''
  if (opts.environmentId) {
    const env = await prisma.environment.findUnique({ where: { id: opts.environmentId } })
    if (env) {
      baseUrl = env.baseUrl ?? ''
      for (const kv of (env.variables as unknown as { key: string; value: string }[]) ?? []) {
        envVars[kv.key] = kv.value
      }
    }
  }
  const globalVars = await loadGlobalVariables(c.projectId)
  const merged = buildMergedContext(globalVars, envVars, {}, opts.debugVars ?? {})

  const steps = (c.steps as unknown as CaseStepDef[]) ?? []
  const start = Date.now()
  const { results, context } = await executeCaseSteps(steps, { baseUrl, initialVars: merged.vars })
  const duration = Date.now() - start

  const overall = results.every((r) => r.status === 'PASS')
    ? 'success'
    : results.some((r) => r.status === 'ERROR')
      ? 'error'
      : 'fail'

  // 超过 1MB 截断存储
  const MAX_DEBUG_BYTES = 1024 * 1024
  let stepResults: unknown = results
  let extractedVars: unknown = context
  let truncated = false
  if (JSON.stringify(results).length > MAX_DEBUG_BYTES) {
    stepResults = { truncated: true, message: '内容过大已截断' }
    truncated = true
  }
  if (JSON.stringify(context).length > MAX_DEBUG_BYTES) {
    extractedVars = { truncated: true, message: '内容过大已截断' }
    truncated = true
  }

  const record = await prisma.debugRecord.create({
    data: {
      caseId,
      caseNameSnapshot: c.name,
      environmentId: opts.environmentId ?? null,
      executeMode: 'server',
      result: overall,
      totalDuration: duration,
      stepResults: stepResults as unknown as Prisma.InputJsonValue,
      extractedVariables: extractedVars as unknown as Prisma.InputJsonValue,
    },
  })

  return { status: overall, duration, results, variables: context, truncated, recordId: record.id }
}
