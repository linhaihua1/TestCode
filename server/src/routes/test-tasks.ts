import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { executeCaseSteps, type CaseStepDef, type StepExecResult } from '../engine/case-executor.js'
import { buildMergedContext, loadGlobalVariables } from '../engine/resolver.js'
import { recordAudit } from '../audit.js'
import { fail } from '../error-codes.js'

/**
 * 测试任务与报告路由（PRD 第 5 期）。
 * - 测试任务（TestTask）的增删改查
 * - 手动执行任务：按任务选中的用例批量执行，生成测试报告（TestTaskRun）
 * - 报告查询：任务报告列表 + 单次报告详情
 */

interface TaskBody {
  name?: string
  description?: string
  caseIds?: unknown
  environmentId?: string | null
  executeMode?: string
  retryCount?: number
  timeout?: number
  cronExpr?: string | null
  enabled?: boolean
  notifyUrl?: string | null
}

/** 单条用例在本次报告中的执行结果 */
interface CaseRunDetail {
  caseId: string
  caseName: string
  status: 'PASS' | 'FAIL' | 'ERROR' | 'SKIP'
  duration: number
  retries: number
  error?: string
  stepResults: StepExecResult[]
}

function parseCaseIds(v: unknown): string[] {
  if (Array.isArray(v)) return v.map((x) => String(x))
  return []
}

/** 读取任务执行环境，返回 baseUrl 与环境变量 */
async function loadEnvContext(environmentId?: string | null): Promise<{ baseUrl: string; envVars: Record<string, string> }> {
  if (!environmentId) return { baseUrl: '', envVars: {} }
  const env = await prisma.environment.findUnique({ where: { id: environmentId } })
  if (!env) return { baseUrl: '', envVars: {} }
  const envVars: Record<string, string> = {}
  for (const kv of (env.variables as unknown as { key: string; value: string }[]) ?? []) {
    envVars[kv.key] = kv.value
  }
  return { baseUrl: env.baseUrl ?? '', envVars }
}

/** 给 Promise 套一个超时，超时则抛错（底层请求不中断，仅标记本用例超时） */
function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  if (!ms || ms <= 0) return p
  return Promise.race([
    p,
    new Promise<never>((_, reject) => setTimeout(() => reject(new Error(`执行超时（${ms}ms）`)), ms)),
  ])
}

interface RunContext {
  baseUrl: string
  globalVars: Record<string, string>
  envVars: Record<string, string>
  timeout: number
}

/** 执行单个用例（按前置/测试/后置步骤顺序），返回本用例结果 */
async function runOneCase(caseId: string, ctx: RunContext): Promise<CaseRunDetail> {
  const c = await prisma.caseInfo.findUnique({ where: { id: caseId } })
  if (!c || c.deletedAt) {
    return { caseId, caseName: c?.name ?? caseId, status: 'SKIP', duration: 0, retries: 0, error: '用例不存在或已删除，跳过', stepResults: [] }
  }
  if (c.status === 'deprecated') {
    return { caseId, caseName: c.name, status: 'SKIP', duration: 0, retries: 0, error: '用例已废弃，跳过', stepResults: [] }
  }
  const merged = buildMergedContext(ctx.globalVars, ctx.envVars, {}, {})
  const steps = (c.steps as unknown as CaseStepDef[]) ?? []
  const start = Date.now()
  try {
    const { results } = await withTimeout(
      executeCaseSteps(steps, { baseUrl: ctx.baseUrl, initialVars: merged.vars }),
      ctx.timeout,
    )
    const status: CaseRunDetail['status'] = results.every((r) => r.status === 'PASS' || r.status === 'SKIP')
      ? 'PASS'
      : results.some((r) => r.status === 'ERROR')
        ? 'ERROR'
        : 'FAIL'
    return { caseId, caseName: c.name, status, duration: Date.now() - start, retries: 0, stepResults: results }
  } catch (err) {
    return {
      caseId,
      caseName: c.name,
      status: 'ERROR',
      duration: Date.now() - start,
      retries: 0,
      error: err instanceof Error ? err.message : String(err),
      stepResults: [],
    }
  }
}

/** 带重试执行单个用例 */
async function runOneCaseWithRetry(caseId: string, ctx: RunContext, retryCount: number): Promise<CaseRunDetail> {
  let last: CaseRunDetail | null = null
  for (let attempt = 0; attempt <= Math.max(0, retryCount); attempt++) {
    last = await runOneCase(caseId, ctx)
    last.retries = attempt
    if (last.status === 'PASS' || last.status === 'SKIP') break
  }
  return last!
}

/** 汇总报告结果 */
function summarize(details: CaseRunDetail[]): { total: number; passed: number; failed: number; error: number; skipped: number; result: string } {
  const summary = {
    total: details.length,
    passed: details.filter((d) => d.status === 'PASS').length,
    failed: details.filter((d) => d.status === 'FAIL').length,
    error: details.filter((d) => d.status === 'ERROR').length,
    skipped: details.filter((d) => d.status === 'SKIP').length,
  }
  const result = summary.error > 0 ? 'ERROR' : summary.failed > 0 ? 'FAIL' : 'PASS'
  return { ...summary, result }
}

export async function testTaskRoutes(app: FastifyInstance) {
  // ---------- 任务 CRUD ----------
  // 任务列表（含最近一次执行结果）
  app.get('/api/projects/:projectId/test-tasks', async (req) => {
    const { projectId } = req.params as { projectId: string }
    const tasks = await prisma.testTask.findMany({
      where: { projectId, deletedAt: null },
      orderBy: { updatedAt: 'desc' },
      include: { runs: { orderBy: { startedAt: 'desc' }, take: 1 } },
    })
    return tasks.map((t) => {
      const { runs, ...rest } = t
      return { ...rest, latestRun: runs[0] ?? null }
    })
  })

  // 新建任务
  app.post('/api/projects/:projectId/test-tasks', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as TaskBody
    if (!body?.name) return fail(reply, 'TASK_NAME_EMPTY')
    const created = await prisma.testTask.create({
      data: {
        projectId,
        name: body.name,
        description: body.description ?? null,
        caseIds: parseCaseIds(body.caseIds) as unknown as Prisma.InputJsonValue,
        environmentId: body.environmentId ?? null,
        executeMode: body.executeMode ?? 'sequential',
        retryCount: body.retryCount ?? 0,
        timeout: body.timeout ?? 300000,
        cronExpr: body.cronExpr ?? null,
        enabled: body.enabled ?? true,
        notifyUrl: body.notifyUrl ?? null,
      },
    })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'create', entityType: 'task', entityId: created.id, after: { name: created.name } })
    return created
  })

  // 任务详情
  app.get('/api/test-tasks/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const task = await prisma.testTask.findUnique({
      where: { id },
      include: { runs: { orderBy: { startedAt: 'desc' }, take: 10 } },
    })
    if (!task) return reply.code(404).send({ error: '任务不存在' })
    return task
  })

  // 更新任务
  app.put('/api/test-tasks/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as TaskBody
    const task = await prisma.testTask.findUnique({ where: { id } })
    if (!task) return reply.code(404).send({ error: '任务不存在' })
    return prisma.testTask.update({
      where: { id },
      data: {
        name: body.name ?? task.name,
        description: body.description ?? task.description,
        caseIds: (body.caseIds !== undefined ? parseCaseIds(body.caseIds) : task.caseIds) as unknown as Prisma.InputJsonValue,
        environmentId: body.environmentId ?? task.environmentId,
        executeMode: body.executeMode ?? task.executeMode,
        retryCount: body.retryCount ?? task.retryCount,
        timeout: body.timeout ?? task.timeout,
        cronExpr: body.cronExpr ?? task.cronExpr,
        enabled: body.enabled ?? task.enabled,
        notifyUrl: body.notifyUrl ?? task.notifyUrl,
      },
    })
  })

  // 软删除任务
  app.delete('/api/test-tasks/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const task = await prisma.testTask.findUnique({ where: { id } })
    if (!task) return reply.code(404).send({ error: '任务不存在' })
    await prisma.testTask.update({ where: { id }, data: { deletedAt: new Date() } })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'delete', entityType: 'task', entityId: id, before: { name: task.name } })
    return { ok: true }
  })

  // ---------- 执行 ----------
  // 手动执行任务
  app.post('/api/test-tasks/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const task = await prisma.testTask.findUnique({ where: { id } })
    if (!task) return reply.code(404).send({ error: '任务不存在' })

    const caseIds = parseCaseIds(task.caseIds)
    if (caseIds.length === 0) return fail(reply, 'TASK_NO_CASES')

    const { baseUrl, envVars } = await loadEnvContext(task.environmentId)
    const globalVars = await loadGlobalVariables(task.projectId)
    const ctx: RunContext = { baseUrl, globalVars, envVars, timeout: task.timeout }

    const start = Date.now()
    let details: CaseRunDetail[]
    if (task.executeMode === 'parallel') {
      details = await Promise.all(caseIds.map((cid) => runOneCaseWithRetry(cid, ctx, task.retryCount)))
    } else {
      details = []
      for (const cid of caseIds) {
        details.push(await runOneCaseWithRetry(cid, ctx, task.retryCount))
      }
    }
    const duration = Date.now() - start
    const summary = summarize(details)

    const run = await prisma.testTaskRun.create({
      data: {
        taskId: id,
        result: summary.result,
        duration,
        startedAt: new Date(start),
        endedAt: new Date(),
        details: details as unknown as Prisma.InputJsonValue,
      },
    })

    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'run', entityType: 'task', entityId: id, after: { result: summary.result, total: summary.total } })
    return { ...run, details, summary }
  })

  // ---------- 报告 ----------
  // 任务下的报告列表
  app.get('/api/test-tasks/:id/runs', async (req) => {
    const { id } = req.params as { id: string }
    const runs = await prisma.testTaskRun.findMany({ where: { taskId: id }, orderBy: { startedAt: 'desc' } })
    return runs.map((r) => {
      const details = (r.details as unknown as CaseRunDetail[]) ?? []
      return { ...r, details, summary: summarize(details) }
    })
  })

  // 单次报告详情
  app.get('/api/test-task-runs/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const run = await prisma.testTaskRun.findUnique({ where: { id } })
    if (!run) return reply.code(404).send({ error: '报告不存在' })
    const details = (run.details as unknown as CaseRunDetail[]) ?? []
    return { ...run, details, summary: summarize(details) }
  })
}
