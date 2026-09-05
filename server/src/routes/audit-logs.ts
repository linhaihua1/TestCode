import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

/**
 * 审计日志路由：查询操作日志（列表 + 筛选枚举）。
 * 日志由各业务路由通过 recordAudit 助手写入。
 */
export async function auditLogRoutes(app: FastifyInstance) {
  // 审计日志列表（支持按动作/实体类型/用户筛选 + 分页）
  app.get('/api/audit-logs', async (req) => {
    const q = req.query as Record<string, string>
    const where: Record<string, unknown> = {}
    if (q.action) where.action = q.action
    if (q.entityType) where.entityType = q.entityType
    if (q.userId) where.userId = q.userId

    const limit = Math.min(Math.max(Number(q.limit ?? 100) || 100, 1), 500)
    const offset = Math.max(Number(q.offset ?? 0) || 0, 0)

    const [total, logs, users] = await Promise.all([
      prisma.auditLog.count({ where }),
      prisma.auditLog.findMany({ where, orderBy: { createdAt: 'desc' }, take: limit, skip: offset }),
      prisma.user.findMany({ select: { id: true, username: true } }),
    ])

    // userId → username 映射，便于展示
    const userMap = new Map(users.map((u) => [u.id, u.username]))
    const enriched = logs.map((l) => ({ ...l, username: l.userId ? userMap.get(l.userId) ?? null : null }))

    return { total, logs: enriched }
  })

  // 筛选下拉枚举（动作 / 实体类型）
  app.get('/api/audit-logs/meta', async () => {
    const [actions, entityTypes] = await Promise.all([
      prisma.auditLog.findMany({ distinct: ['action'], select: { action: true }, orderBy: { action: 'asc' } }),
      prisma.auditLog.findMany({ distinct: ['entityType'], select: { entityType: true }, orderBy: { entityType: 'asc' } }),
    ])
    return {
      actions: actions.map((a) => a.action),
      entityTypes: entityTypes.map((e) => e.entityType),
    }
  })
}
