import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

export async function debugRecordRoutes(app: FastifyInstance) {
  // 获取项目的调试记录列表
  app.get('/api/projects/:projectId/debug-records', async (req) => {
    const { projectId } = req.params as { projectId: string }
    const query = req.query as Record<string, string>
    const where: Record<string, unknown> = { caseInfo: { projectId } }
    if (query.result) where.result = query.result
    if (query.caseName) where.caseNameSnapshot = { contains: query.caseName }
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
