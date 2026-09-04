import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

export async function auditLogRoutes(app: FastifyInstance) {
  // 记录操作日志（内部调用，不在路由表注册）
  app.post('/api/internal/audit', async (req, reply) => {
    const body = req.body as {
      userId?: string
      action?: string
      entityType?: string
      entityId?: string
      before?: unknown
      after?: unknown
      ip?: string
    }
    await prisma.auditLog.create({
      data: {
        userId: body.userId,
        action: body.action ?? 'unknown',
        entityType: body.entityType ?? 'unknown',
        entityId: body.entityId,
        before: body.before as unknown as any,
        after: body.after as unknown as any,
        ip: body.ip ?? (req.ip ?? undefined),
      },
    })
    return { ok: true }
  })
}
