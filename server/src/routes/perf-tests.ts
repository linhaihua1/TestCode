/**
 * 性能测试路由（基于 JMeter）：用例 CRUD + 异步执行 + .jmx 导入导出 + 报告查询 + 插件探测。
 *
 * 数据以「结构化用例模型」存储，.jmx 只在导入导出的边界上转换，导出→导入→导出不漂移。
 *
 * 执行是**异步**的：压测可能持续数十分钟，run 端点创建 status='running' 的报告后立即返回，
 * 由 engine/jmeter/registry.ts 持有 JMeter 子进程句柄；报告详情在运行中叠加实时指标，
 * 可通过 stop 端点中止。原始 JTL 与 JMeter 官方 HTML 报告保存在 server/.perf-runs/<reportId>/。
 */
import type { FastifyInstance } from 'fastify'
import { existsSync, mkdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { extname, join, resolve, sep } from 'node:path'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { recordAudit } from '../audit.js'
import { buildJmx, buildJmxMulti } from '../engine/jmeter/jmx-builder.js'
import { parseJmx } from '../engine/jmeter/jmx-parser.js'
import { detectPlugins } from '../engine/jmeter/plugins.js'
import {
  activeCount,
  getRun,
  isCaseRunning,
  isRunActive,
  maxConcurrency,
  registerRun,
  stopRun,
  unregisterRun,
} from '../engine/jmeter/registry.js'
import { locateJmeter, preflightPerf, startPerfRun, type PerfRunResult } from '../engine/jmeter/runner.js'
import type {
  PerfAssertion,
  PerfCaseModel,
  PerfConcurrency,
  PerfKeyValue,
  PerfLoadProfile,
  PerfOnError,
  PerfStep,
  PerfStepping,
} from '../engine/jmeter/types.js'

const ON_ERROR: PerfOnError[] = ['continue', 'startnext', 'stopthread', 'stoptest']
const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']
const PROFILES: PerfLoadProfile[] = ['constant', 'stepping', 'concurrency']

/** 压测工作目录：原始 JTL + 官方 HTML 报告（.gitignore 排除） */
const PERF_RUNS = resolve(process.cwd(), '.perf-runs')

interface PerfCaseBody {
  name?: string
  description?: string
  threads?: number
  rampUp?: number
  loops?: number
  duration?: number
  thinkTime?: number
  onSampleError?: string
  variables?: unknown
  steps?: unknown
  loadProfile?: string
  stepping?: PerfStepping
  concurrency?: PerfConcurrency
}

/** 取整并夹在 [min,max] 内；非数字回退默认值 */
function num(v: unknown, def: number, min: number, max: number): number {
  const n = Number(v)
  if (!Number.isFinite(n)) return def
  return Math.min(max, Math.max(min, Math.floor(n)))
}

/** 规范化键值对数组（丢弃无 key 与已禁用的项） */
function kvList(v: unknown): PerfKeyValue[] {
  if (!Array.isArray(v)) return []
  return v
    .filter((x) => x && typeof x === 'object' && String((x as PerfKeyValue).key ?? '').trim())
    .map((x) => {
      const o = x as PerfKeyValue
      return { key: String(o.key).trim(), value: String(o.value ?? ''), enabled: o.enabled !== false }
    })
}

function assertionList(v: unknown): PerfAssertion[] {
  if (!Array.isArray(v)) return []
  return v
    .filter((x) => x && typeof x === 'object' && String((x as PerfAssertion).expected ?? '').trim())
    .map((x) => {
      const o = x as PerfAssertion
      return {
        type: o.type === 'responseText' ? 'responseText' : 'responseCode',
        operator: o.operator === 'contains' ? 'contains' : 'equals',
        expected: String(o.expected),
      }
    })
}

function stepList(v: unknown): PerfStep[] {
  if (!Array.isArray(v)) return []
  return v
    .filter((x) => x && typeof x === 'object' && String((x as PerfStep).url ?? '').trim())
    .map((x, i) => {
      const o = x as PerfStep
      const method = String(o.method ?? 'GET').toUpperCase()
      return {
        id: String(o.id ?? `step-${i + 1}`),
        name: String(o.name ?? `请求 ${i + 1}`),
        method: METHODS.includes(method) ? method : 'GET',
        url: String(o.url).trim(),
        headers: kvList(o.headers),
        query: kvList(o.query),
        body: typeof o.body === 'string' && o.body.trim() ? o.body : undefined,
        assertions: assertionList(o.assertions),
        enabled: o.enabled !== false,
      }
    })
}

/** 从请求体解析加压方式（固定并发 / 阶梯 / 目标并发） */
function normalizeProfile(body: PerfCaseBody | undefined): { loadProfile: PerfLoadProfile; stepping?: PerfStepping; concurrency?: PerfConcurrency } {
  const lp = String(body?.loadProfile ?? 'constant')
  const loadProfile: PerfLoadProfile = PROFILES.includes(lp as PerfLoadProfile) ? (lp as PerfLoadProfile) : 'constant'
  if (loadProfile === 'stepping') {
    const s = body?.stepping ?? {}
    return {
      loadProfile,
      stepping: {
        initialDelay: num(s.initialDelay, 0, 0, 3600),
        batchThreads: num(s.batchThreads, 1, 1, 2000),
        batchInterval: num(s.batchInterval, 1, 1, 3600),
        flightTime: num(s.flightTime, 0, 0, 86400),
        burstThreads: num(s.burstThreads, 0, 0, 2000),
        burstInterval: num(s.burstInterval, 0, 0, 3600),
      },
    }
  }
  if (loadProfile === 'concurrency') {
    const c = body?.concurrency ?? {}
    return {
      loadProfile,
      concurrency: {
        steps: num(c.steps, 1, 1, 1000),
        holdTarget: num(c.holdTarget, 0, 0, 86400),
        unit: (['S', 'M', 'H', 'D'] as const).includes((c.unit as 'S') ?? 'S') ? (c.unit as 'S' | 'M' | 'H' | 'D') : 'S',
      },
    }
  }
  return { loadProfile }
}

/** 校验并规范化请求体为用例模型 */
function normalizeCase(body: PerfCaseBody | undefined): { model: PerfCaseModel } | { error: string } {
  const name = String(body?.name ?? '').trim()
  if (!name) return { error: 'name 必填' }
  const onErr = String(body?.onSampleError ?? 'continue')
  return {
    model: {
      name,
      threads: num(body?.threads, 1, 1, 2000),
      rampUp: num(body?.rampUp, 1, 1, 3600),
      loops: num(body?.loops, 1, 1, 100000),
      duration: num(body?.duration, 0, 0, 86400),
      thinkTime: num(body?.thinkTime, 0, 0, 600000),
      onSampleError: (ON_ERROR.includes(onErr as PerfOnError) ? onErr : 'continue') as PerfOnError,
      variables: kvList(body?.variables),
      steps: stepList(body?.steps),
      ...normalizeProfile(body),
    },
  }
}

/** 数据库行 → 用例模型 */
function toModel(row: {
  name: string
  threads: number
  rampUp: number
  loops: number
  duration: number
  thinkTime: number
  onSampleError: string
  variables: unknown
  steps: unknown
  profile?: unknown
}): PerfCaseModel {
  const p = (row.profile ?? {}) as Partial<PerfCaseModel>
  return {
    name: row.name,
    threads: row.threads,
    rampUp: row.rampUp,
    loops: row.loops,
    duration: row.duration,
    thinkTime: row.thinkTime,
    onSampleError: (ON_ERROR.includes(row.onSampleError as PerfOnError) ? row.onSampleError : 'continue') as PerfOnError,
    variables: kvList(row.variables),
    steps: stepList(row.steps),
    loadProfile: p.loadProfile ?? 'constant',
    ...(p.loadProfile === 'stepping' ? { stepping: p.stepping } : {}),
    ...(p.loadProfile === 'concurrency' ? { concurrency: p.concurrency } : {}),
  }
}

/** 生成下载文件名（保留中文，走 RFC 5987 filename*） */
function attachment(reply: any, filename: string, content: string) {
  const fallback = filename.replace(/[^\w.\-]/g, '_') || 'download'
  reply
    .header('Content-Type', 'application/octet-stream')
    .header('Content-Disposition', `attachment; filename="${fallback}"; filename*=UTF-8''${encodeURIComponent(filename)}`)
  return content
}

const userOf = (req: unknown) => (req as { user: { userId: string } }).user

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.csv': 'text/csv; charset=utf-8',
}

function runDirOf(reportId: string): string {
  return join(PERF_RUNS, reportId)
}

/** 把压测结果写回报告（无论成功失败/停止，都会走到这里） */
function finalizeReport(reportId: string) {
  return async (result: PerfRunResult) => {
    unregisterRun(reportId)
    try {
      await prisma.perfReport.update({
        where: { id: reportId },
        data: {
          status: result.status,
          duration: result.durationMs,
          summary: result.summary as unknown as Prisma.InputJsonValue,
          series: result.series as unknown as Prisma.InputJsonValue,
          labels: result.labels as unknown as Prisma.InputJsonValue,
          errors: result.errors as unknown as Prisma.InputJsonValue,
          message: result.message ?? null,
        },
      })
    } catch {
      // 报告可能已被删除，忽略
    }
  }
}

/** 服务重启后，把孤儿 running 报告标记为失败 */
export async function reapOrphanPerfRuns(): Promise<number> {
  const r = await prisma.perfReport.updateMany({
    where: { status: 'running' },
    data: { status: 'error', message: '服务重启导致压测中断' },
  })
  return r.count
}

/** 安全读取 .perf-runs/<reportId> 下的静态文件（HTML 报告资源） */
function serveRunFile(reply: any, reportId: string, rel: string) {
  const base = runDirOf(reportId)
  const abs = resolve(base, rel)
  if (abs !== base && !abs.startsWith(base + sep)) return reply.code(404).send({ error: '资源不存在' })
  try {
    if (!existsSync(abs) || !statSync(abs).isFile()) return reply.code(404).send({ error: '资源不存在' })
    return reply.header('Content-Type', MIME[extname(abs).toLowerCase()] ?? 'application/octet-stream').send(readFileSync(abs))
  } catch {
    return reply.code(404).send({ error: '资源不存在' })
  }
}

export async function perfRoutes(app: FastifyInstance) {
  // ---------- 环境与插件探测 ----------
  app.get('/api/perf-env', async () => {
    const jmeter = locateJmeter()
    return {
      available: Boolean(jmeter),
      source: jmeter?.source ?? null,
      home: jmeter?.home ?? null,
      javaHome: process.env.JAVA_HOME ?? null,
    }
  })

  // 插件目录：探测 lib/ext 下已装 / 半装 / 缺失的第三方插件
  app.get('/api/perf-plugins', async () => detectPlugins())

  // ---------- 用例 CRUD ----------
  app.get('/api/projects/:projectId/perf-cases', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.perfCase.findMany({ where: { projectId, deletedAt: null }, orderBy: { createdAt: 'asc' } })
  })

  app.post('/api/projects/:projectId/perf-cases', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const normalized = normalizeCase(req.body as PerfCaseBody)
    if ('error' in normalized) return reply.code(400).send({ error: normalized.error })
    const m = normalized.model
    const created = await prisma.perfCase.create({
      data: {
        projectId,
        name: m.name,
        description: (req.body as PerfCaseBody)?.description ?? null,
        threads: m.threads,
        rampUp: m.rampUp,
        loops: m.loops,
        duration: m.duration,
        thinkTime: m.thinkTime,
        onSampleError: m.onSampleError,
        variables: m.variables as unknown as Prisma.InputJsonValue,
        steps: m.steps as unknown as Prisma.InputJsonValue,
        profile: normalizeProfile(req.body as PerfCaseBody) as unknown as Prisma.InputJsonValue,
      },
    })
    await recordAudit({ user: userOf(req), action: 'create', entityType: 'perf-case', entityId: created.id, after: { name: created.name } })
    return created
  })

  app.get('/api/perf-cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    return row
  })

  app.put('/api/perf-cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    const normalized = normalizeCase({ ...(req.body as PerfCaseBody), name: (req.body as PerfCaseBody)?.name ?? row.name })
    if ('error' in normalized) return reply.code(400).send({ error: normalized.error })
    const m = normalized.model
    const updated = await prisma.perfCase.update({
      where: { id },
      data: {
        name: m.name,
        description: (req.body as PerfCaseBody)?.description ?? row.description,
        threads: m.threads,
        rampUp: m.rampUp,
        loops: m.loops,
        duration: m.duration,
        thinkTime: m.thinkTime,
        onSampleError: m.onSampleError,
        variables: m.variables as unknown as Prisma.InputJsonValue,
        steps: m.steps as unknown as Prisma.InputJsonValue,
        profile: normalizeProfile(req.body as PerfCaseBody) as unknown as Prisma.InputJsonValue,
      },
    })
    await recordAudit({ user: userOf(req), action: 'update', entityType: 'perf-case', entityId: id, after: { name: updated.name } })
    return updated
  })

  // 软删除：报告保留（caseId 置空由级联处理）
  app.delete('/api/perf-cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    await prisma.perfCase.update({ where: { id }, data: { deletedAt: new Date() } })
    await recordAudit({ user: userOf(req), action: 'delete', entityType: 'perf-case', entityId: id, before: { name: row.name } })
    return { ok: true }
  })

  // ---------- 执行（异步） ----------
  app.post('/api/perf-cases/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })

    const model = toModel(row)
    const preflight = preflightPerf(model)
    if (preflight) return reply.code(400).send({ error: preflight })

    if (isCaseRunning(id)) return reply.code(409).send({ error: '该用例已有正在运行的压测' })
    if (activeCount() >= maxConcurrency()) {
      return reply.code(429).send({ error: `并发压测已达上限（${maxConcurrency()}），请等待其它压测结束` })
    }

    const body = (req.body ?? {}) as { timeoutMs?: number }
    const timeoutMs = Number(body.timeoutMs) > 0 ? num(body.timeoutMs, 3600000, 10000, 4 * 60 * 60 * 1000) : undefined

    // 先落一条 running 报告，再启动进程
    const report = await prisma.perfReport.create({
      data: {
        projectId: row.projectId,
        caseId: row.id,
        name: row.name,
        status: 'running',
        duration: 0,
        summary: { samples: 0, errors: 0, errorRate: 0, avg: 0, min: 0, max: 0, median: 0, p90: 0, p95: 0, p99: 0, throughput: 0 },
        series: [],
        labels: [],
        errors: [],
        message: null,
      },
    })

    const dir = runDirOf(report.id)
    try {
      mkdirSync(dir, { recursive: true })
    } catch {
      /* ignore */
    }
    const handle = startPerfRun(model, { timeoutMs, workDir: dir, dashboard: true })
    registerRun(report.id, row.id, handle)
    handle.finished.then(finalizeReport(report.id)).catch(() => unregisterRun(report.id))

    await recordAudit({ user: userOf(req), action: 'perf:run', entityType: 'perf-case', entityId: id, after: { reportId: report.id } })
    return reply.code(202).send({ id: report.id, status: 'running', startedAt: report.startedAt })
  })

  // 停止运行中的压测
  app.post('/api/perf-reports/:id/stop', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.perfReport.findUnique({ where: { id } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    if (stopRun(id)) return { ok: true, status: 'stopping' }
    return reply.code(409).send({ error: '该报告已不在执行中' })
  })

  // ---------- JMeter 格式导入 / 导出 ----------
  app.post('/api/projects/:projectId/perf-cases/import', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as {
      filename?: string
      content?: string
      files?: Array<{ filename?: string; content?: string }>
    }
    const files = Array.isArray(body?.files) && body.files.length ? body.files : [{ filename: body?.filename, content: body?.content }]

    const created: Array<{ id: string; name: string }> = []
    const failed: Array<{ filename: string; reason: string }> = []
    let index = 0

    for (const file of files) {
      index += 1
      const filename = String(file?.filename ?? `第 ${index} 个文件`)
      const content = String(file?.content ?? '')
      if (!content.trim()) {
        failed.push({ filename, reason: '文件内容为空' })
        continue
      }
      try {
        const model = parseJmx(content)
        const threadGroups = (content.match(/<ThreadGroup[\s>]/g) ?? []).length
        const row = await prisma.perfCase.create({
          data: {
            projectId,
            name: model.name || filename,
            description: `从 JMeter 文件导入：${filename}`,
            threads: model.threads,
            rampUp: model.rampUp,
            loops: model.loops,
            duration: model.duration,
            thinkTime: model.thinkTime,
            onSampleError: model.onSampleError,
            variables: model.variables as unknown as Prisma.InputJsonValue,
            steps: model.steps as unknown as Prisma.InputJsonValue,
            profile: { loadProfile: model.loadProfile ?? 'constant', stepping: model.stepping, concurrency: model.concurrency } as unknown as Prisma.InputJsonValue,
          },
        })
        created.push({ id: row.id, name: row.name })
        if (threadGroups > 1) {
          failed.push({ filename, reason: `该计划含 ${threadGroups} 个线程组，仅导入第 1 个「${row.name}」` })
        }
      } catch (err) {
        failed.push({ filename, reason: err instanceof Error ? err.message : String(err) })
      }
    }

    if (!created.length) return reply.code(400).send({ error: failed[0]?.reason ?? '导入失败', failed })
    await recordAudit({ user: userOf(req), action: 'import:jmeter', entityType: 'perf-case', after: { created: created.length, failed: failed.length } })
    return { created, failed }
  })

  app.get('/api/perf-cases/:id/export', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    return attachment(reply, `${row.name}.jmx`, buildJmx(toModel(row)))
  })

  app.post('/api/projects/:projectId/perf-cases/export', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as { ids?: string[]; planName?: string }
    const ids = Array.isArray(body?.ids) ? body.ids.filter(Boolean) : []
    if (!ids.length) return reply.code(400).send({ error: 'ids 必填' })
    const rows = await prisma.perfCase.findMany({ where: { projectId, id: { in: ids }, deletedAt: null } })
    if (!rows.length) return reply.code(404).send({ error: '没有可导出的用例' })
    const planName = String(body?.planName ?? '').trim() || `${rows[0].name} 等 ${rows.length} 个用例`
    return attachment(reply, `${planName}.jmx`, buildJmxMulti(rows.map(toModel), planName))
  })

  // ---------- 报告 ----------
  app.get('/api/projects/:projectId/perf-reports', async (req) => {
    const { projectId } = req.params as { projectId: string }
    const rows = await prisma.perfReport.findMany({
      where: { projectId },
      orderBy: { startedAt: 'desc' },
      include: { perfCase: { select: { id: true, name: true, deletedAt: true } } },
    })
    // 列表不需要时序数据，剔除；运行中的报告叠加轻量实时汇总
    return rows.map(({ series, ...rest }) => {
      const run = getRun(rest.id)
      if (run && run.handle.running()) {
        const live = run.handle.live()
        return { ...rest, running: true, liveSummary: live.summary, elapsedMs: live.elapsedMs, expectedDurationMs: live.expectedDurationMs }
      }
      return rest
    })
  })

  app.get('/api/perf-reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.perfReport.findUnique({ where: { id }, include: { perfCase: { select: { id: true, name: true } } } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    const run = getRun(id)
    if (run && run.handle.running()) {
      const live = run.handle.live()
      return {
        ...report,
        live: true,
        summary: live.summary,
        series: live.series,
        labels: live.labels,
        errors: live.errors,
        elapsedMs: live.elapsedMs,
        expectedDurationMs: live.expectedDurationMs,
      }
    }
    return report
  })

  // 官方 HTML 报告（默认页 + 静态资源）
  app.get('/api/perf-reports/:id/dashboard', async (req, reply) => {
    const { id } = req.params as { id: string }
    return serveRunFile(reply, id, join('report', 'index.html'))
  })

  app.get('/api/perf-reports/:id/dashboard/*', async (req, reply) => {
    const { id } = req.params as { id: string }
    const rel = (req.params as Record<string, string>)['*'] ?? ''
    return serveRunFile(reply, id, join('report', rel))
  })

  app.delete('/api/perf-reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.perfReport.findUnique({ where: { id } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    if (isRunActive(id)) return reply.code(409).send({ error: '压测仍在执行中，请先停止' })
    await prisma.perfReport.delete({ where: { id } })
    rmSync(runDirOf(id), { recursive: true, force: true })
    await recordAudit({ user: userOf(req), action: 'delete', entityType: 'perf-report', entityId: id, before: { name: report.name } })
    return { ok: true }
  })
}
