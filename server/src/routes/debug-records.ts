import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { runCaseDebug } from '../engine/debug-runner.js'

export async function debugRecordRoutes(app: FastifyInstance) {
  // 获取项目的调试记录列表
  app.get('/api/projects/:projectId/debug-records', async (req) => {
    const { projectId } = req.params as { projectId: string }
    const query = req.query as Record<string, string>
    const where: Record<string, unknown> = { caseInfo: { projectId } }
    if (query.result) where.result = query.result
    if (query.caseName) where.caseNameSnapshot = { contains: query.caseName }
    if (query.caseId) where.caseId = query.caseId
    return prisma.debugRecord.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      take: 100,
    })
  })

  // 获取单条调试记录详情
  app.get('/api/debug-records/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const r = await prisma.debugRecord.findUnique({ where: { id }, include: { caseInfo: true } })
    if (!r) return reply.code(404).send({ error: '记录不存在' })
    return r
  })

  // 一键重放：用相同环境重新执行该记录对应的用例
  app.post('/api/debug-records/:id/replay', async (req, reply) => {
    const { id } = req.params as { id: string }
    const r = await prisma.debugRecord.findUnique({ where: { id } })
    if (!r) return reply.code(404).send({ error: '记录不存在' })
    try {
      return await runCaseDebug(r.caseId, { environmentId: r.environmentId })
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(404).send({ error: message })
    }
  })

  // 对比两条调试记录
  app.get('/api/debug-records/compare', async (req, reply) => {
    const q = req.query as { a?: string; b?: string }
    if (!q.a || !q.b) return reply.code(400).send({ error: '缺少对比记录 ID' })
    const [a, b] = await Promise.all([
      prisma.debugRecord.findUnique({ where: { id: q.a } }),
      prisma.debugRecord.findUnique({ where: { id: q.b } }),
    ])
    if (!a || !b) return reply.code(404).send({ error: '记录不存在' })

    const stepsA = Array.isArray(a.stepResults) ? (a.stepResults as unknown as Array<{ name: string; status: string; message: string }>) : []
    const stepsB = Array.isArray(b.stepResults) ? (b.stepResults as unknown as Array<{ name: string; status: string; message: string }>) : []
    const varsA = a.extractedVariables && typeof a.extractedVariables === 'object' && !Array.isArray(a.extractedVariables)
      ? (a.extractedVariables as Record<string, string>)
      : {}
    const varsB = b.extractedVariables && typeof b.extractedVariables === 'object' && !Array.isArray(b.extractedVariables)
      ? (b.extractedVariables as Record<string, string>)
      : {}

    const stepDiffs = stepsA.map((sa, i) => ({
      name: sa.name ?? `步骤 ${i + 1}`,
      statusA: sa.status,
      statusB: stepsB[i]?.status,
      changed: sa.status !== stepsB[i]?.status,
    }))

    const varDiffs = {
      added: [] as Array<{ name: string; value: string }>,
      removed: [] as Array<{ name: string; value: string }>,
      changed: [] as Array<{ name: string; a: string; b: string }>,
    }
    const allVarKeys = new Set([...Object.keys(varsA), ...Object.keys(varsB)])
    for (const k of allVarKeys) {
      if (varsA[k] === undefined) varDiffs.added.push({ name: k, value: varsB[k] })
      else if (varsB[k] === undefined) varDiffs.removed.push({ name: k, value: varsA[k] })
      else if (varsA[k] !== varsB[k]) varDiffs.changed.push({ name: k, a: varsA[k], b: varsB[k] })
    }

    return {
      a: { id: a.id, result: a.result, totalDuration: a.totalDuration, createdAt: a.createdAt },
      b: { id: b.id, result: b.result, totalDuration: b.totalDuration, createdAt: b.createdAt },
      diff: {
        resultChanged: a.result !== b.result,
        durationDelta: b.totalDuration - a.totalDuration,
        stepDiffs,
        varDiffs,
      },
    }
  })

  // 删除调试记录
  app.delete('/api/debug-records/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const r = await prisma.debugRecord.findUnique({ where: { id } })
    if (!r) return reply.code(404).send({ error: '记录不存在' })
    await prisma.debugRecord.delete({ where: { id } })
    return { ok: true }
  })

  // 清理超过 N 天的调试记录
  app.delete('/api/debug-records/clean', async (req, reply) => {
    const body = req.body as { days?: number }
    const days = body?.days ?? 30
    const cutoff = new Date(Date.now() - days * 24 * 60 * 60 * 1000)
    const result = await prisma.debugRecord.deleteMany({ where: { createdAt: { lt: cutoff } } })
    return { deleted: result.count }
  })
}
