/**
 * 性能测试路由（基于 JMeter）：用例 CRUD + 执行 + .jmx 导入导出 + 报告查询。
 *
 * 数据以「结构化用例模型」存储，.jmx 只在导入导出的边界上转换，
 * 因此导出→导入→导出不会产生漂移。
 */
import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { recordAudit } from '../audit.js'
import { buildJmx, buildJmxMulti } from '../engine/jmeter/jmx-builder.js'
import { parseJmx } from '../engine/jmeter/jmx-parser.js'
import { locateJmeter, runPerfTest } from '../engine/jmeter/runner.js'
import type { PerfAssertion, PerfCaseModel, PerfKeyValue, PerfOnError, PerfStep } from '../engine/jmeter/types.js'

const ON_ERROR: PerfOnError[] = ['continue', 'startnext', 'stopthread', 'stoptest']
const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']

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
}): PerfCaseModel {
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

export async function perfRoutes(app: FastifyInstance) {
  // ---------- 环境探测 ----------
  // 前端进入性能测试页时调用，用于提示 JMeter 运行时是否就绪
  app.get('/api/perf-env', async () => {
    const jmeter = locateJmeter()
    return {
      available: Boolean(jmeter),
      source: jmeter?.source ?? null,
      home: jmeter?.home ?? null,
      javaHome: process.env.JAVA_HOME ?? null,
    }
  })

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
      },
    })
    await recordAudit({ user: userOf(req), action: 'update', entityType: 'perf-case', entityId: id, after: { name: updated.name } })
    return updated
  })

  // 软删除：报告保留（caseId 置空由级联处理），用例进回收站语义
  app.delete('/api/perf-cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    await prisma.perfCase.update({ where: { id }, data: { deletedAt: new Date() } })
    await recordAudit({ user: userOf(req), action: 'delete', entityType: 'perf-case', entityId: id, before: { name: row.name } })
    return { ok: true }
  })

  // ---------- 执行 ----------
  app.post('/api/perf-cases/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    const body = (req.body ?? {}) as { timeoutMs?: number }
    const timeoutMs = Number(body.timeoutMs) > 0 ? num(body.timeoutMs, 3600000, 10000, 4 * 60 * 60 * 1000) : undefined

    const result = await runPerfTest(toModel(row), { timeoutMs })
    const report = await prisma.perfReport.create({
      data: {
        projectId: row.projectId,
        caseId: row.id,
        name: row.name,
        status: result.status,
        duration: result.durationMs,
        summary: result.summary as unknown as Prisma.InputJsonValue,
        series: result.series as unknown as Prisma.InputJsonValue,
        labels: result.labels as unknown as Prisma.InputJsonValue,
        errors: result.errors as unknown as Prisma.InputJsonValue,
        message: result.message ?? null,
      },
    })
    await recordAudit({
      user: userOf(req),
      action: 'perf:run',
      entityType: 'perf-case',
      entityId: id,
      after: { status: result.status, samples: result.summary.samples, reportId: report.id },
    })
    return report
  })

  // ---------- JMeter 格式导入 / 导出 ----------
  // 导入：支持一次上传多个 .jmx，每个计划生成一个用例（仅取第一个线程组）
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

  // 导出单个用例为标准 .jmx
  app.get('/api/perf-cases/:id/export', async (req, reply) => {
    const { id } = req.params as { id: string }
    const row = await prisma.perfCase.findFirst({ where: { id, deletedAt: null } })
    if (!row) return reply.code(404).send({ error: '压测用例不存在' })
    return attachment(reply, `${row.name}.jmx`, buildJmx(toModel(row)))
  })

  // 批量导出：多个用例合并为一个测试计划（每个用例一个线程组）
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
    // 列表不需要时序数据，剔除以减小响应体
    return rows.map(({ series, ...rest }) => rest)
  })

  app.get('/api/perf-reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.perfReport.findUnique({ where: { id }, include: { perfCase: { select: { id: true, name: true } } } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    return report
  })

  app.delete('/api/perf-reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.perfReport.findUnique({ where: { id } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    await prisma.perfReport.delete({ where: { id } })
    await recordAudit({ user: userOf(req), action: 'delete', entityType: 'perf-report', entityId: id, before: { name: report.name } })
    return { ok: true }
  })
}
