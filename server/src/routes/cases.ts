import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

interface CaseBody {
  name?: string
  description?: string
  moduleId?: string | null
  status?: string
  priority?: string
  tags?: unknown
  steps?: unknown
}

export async function caseRoutes(app: FastifyInstance) {
  // 获取项目下的用例列表（仅未删除的）
  app.get('/api/projects/:projectId/cases', async (req) => {
    const { projectId } = req.params as { projectId: string }
    const query = req.query as Record<string, string>
    const where: Record<string, unknown> = { projectId, deletedAt: null }
    if (query.status) where.status = query.status
    if (query.moduleId) where.moduleId = query.moduleId
    return prisma.caseInfo.findMany({
      where,
      orderBy: { updatedAt: 'desc' },
      include: { module: true },
    })
  })

  // 新建用例
  app.post('/api/projects/:projectId/cases', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as CaseBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.caseInfo.create({
      data: {
        projectId,
        name: body.name,
        description: body.description ?? null,
        moduleId: body.moduleId ?? null,
        status: body.status ?? 'draft',
        priority: body.priority ?? 'P2',
        tags: body.tags ?? [],
        steps: [],
      },
    })
  })

  // 获取用例详情
  app.get('/api/case-info/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({
      where: { id },
      include: { module: true, versions: { orderBy: { version: 'desc' }, take: 10 } },
    })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    return c
  })

  // 更新用例
  app.put('/api/case-info/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as CaseBody
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    const newVersion = c.version + 1
    await prisma.caseVersion.create({
      data: {
        caseId: id,
        version: newVersion,
        snapshot: { name: body.name, description: body.description, moduleId: body.moduleId, status: body.status, priority: body.priority, tags: body.tags, steps: body.steps } as unknown as Prisma.InputJsonValue,
        changeSummary: body.description ?? '',
      },
    })
    return prisma.caseInfo.update({
      where: { id },
      data: {
        name: body.name ?? c.name,
        description: body.description ?? c.description,
        moduleId: body.moduleId ?? c.moduleId,
        status: body.status ?? c.status,
        priority: body.priority ?? c.priority,
        tags: (body.tags ?? c.tags) as unknown as Prisma.InputJsonValue,
        steps: (body.steps ?? c.steps) as unknown as Prisma.InputJsonValue,
        version: newVersion,
      },
    })
  })

  // 软删除用例
  app.delete('/api/case-info/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    await prisma.caseInfo.update({ where: { id }, data: { deletedAt: new Date().toISOString() } })
    return { ok: true }
  })

  // 版本回退
  app.post('/api/case-info/:id/rollback/:versionId', async (req, reply) => {
    const { id, versionId } = req.params as { id: string; versionId: string }
    const ver = await prisma.caseVersion.findUnique({ where: { id: versionId }, include: { case: true } })
    if (!ver) return reply.code(404).send({ error: '版本不存在' })
    const snapshot = ver.snapshot as Record<string, unknown>
    const newVersion = (ver.case as any).version + 1
    await prisma.caseVersion.create({
      data: {
        caseId: id,
        version: newVersion,
        snapshot: ver.snapshot as unknown as Prisma.InputJsonValue,
        changeSummary: 'rollback to version ' + ver.version,
      },
    })
    return prisma.caseInfo.update({
      where: { id },
      data: {
        name: (snapshot.name as string) ?? (ver.case as any).name,
        steps: (snapshot.steps as unknown as Prisma.InputJsonValue) ?? [],
        tags: (snapshot.tags as unknown as Prisma.InputJsonValue) ?? [],
        status: (snapshot.status as string) ?? 'draft',
        priority: (snapshot.priority as string) ?? 'P2',
        version: newVersion,
      },
    })
  })

  // 获取可用目录选项
  app.get('/api/projects/:projectId/modules-for-select', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.module.findMany({
      where: { projectId },
      orderBy: { sortOrder: 'asc' },
      select: { id: true, name: true, parentId: true },
    })
  })
}
