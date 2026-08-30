import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

export async function reportRoutes(app: FastifyInstance) {
  app.get('/api/projects/:projectId/reports', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.report.findMany({
      where: { projectId },
      orderBy: { startedAt: 'desc' },
      include: { details: true },
    })
  })

  app.get('/api/reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.report.findUnique({ where: { id }, include: { details: true } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    return report
  })
}
