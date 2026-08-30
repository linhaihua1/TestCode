import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

/**
 * 测试报告（Report）相关的 REST 查询路由：
 * - 获取项目下的报告列表（含明细）
 * - 获取单个报告详情（含明细）
 */
export async function reportRoutes(app: FastifyInstance) {
  // 获取项目下的报告列表（含明细，按开始时间倒序）
  app.get('/api/projects/:projectId/reports', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.report.findMany({
      where: { projectId },
      orderBy: { startedAt: 'desc' },
      include: { details: true },
    })
  })

  // 获取单个报告详情（含明细）
  app.get('/api/reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.report.findUnique({ where: { id }, include: { details: true } })
    if (!report) return reply.code(404).send({ error: '报告不存在' }) // 报告不存在返回 404
    return report
  })
}
