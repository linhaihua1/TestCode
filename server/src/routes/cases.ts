import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { executeCaseSteps, type CaseStepDef } from '../engine/case-executor.js'
import { buildMergedContext, loadGlobalVariables } from '../engine/resolver.js'
import { recordAudit } from '../audit.js'

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
    const created = await prisma.caseInfo.create({
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
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'create', entityType: 'case', entityId: created.id, after: { name: created.name } })
    return created
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
    }).then(async (updated) => {
      await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'update', entityType: 'case', entityId: id, after: { name: updated.name, status: updated.status } })
      return updated
    })
  })

  // 软删除用例
  app.delete('/api/case-info/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    await prisma.caseInfo.update({ where: { id }, data: { deletedAt: new Date().toISOString() } })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'delete', entityType: 'case', entityId: id, before: { name: c.name } })
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
    const updated = await prisma.caseInfo.update({
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
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'rollback', entityType: 'case', entityId: id, after: { version: newVersion } })
    return updated
  })

  // ---------- 评审 ----------
  // 提交/通过/驳回评审（含评审意见）
  app.post('/api/case-info/:id/review', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { action?: string; comment?: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    const user = (req as unknown as { user: { userId: string; username: string } }).user
    const action = body?.action ?? 'submit'

    const transition: Record<string, string> = {
      submit: 'pending',
      approve: 'passed',
      reject: 'rejected',
    }
    const toStatus = transition[action] ?? c.status

    const review = await prisma.caseReview.create({
      data: {
        caseId: id,
        reviewerId: user.userId,
        reviewerName: user.username,
        action,
        comment: body?.comment ?? null,
        fromStatus: c.status,
        toStatus,
      },
    })

    if (toStatus !== c.status) {
      await prisma.caseInfo.update({ where: { id }, data: { status: toStatus } })
    }
    await recordAudit({ user, action: `review:${action}`, entityType: 'case', entityId: id, after: { status: toStatus, comment: body?.comment ?? null } })
    return review
  })

  // 评审记录列表
  app.get('/api/case-info/:id/reviews', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    return prisma.caseReview.findMany({ where: { caseId: id }, orderBy: { createdAt: 'desc' } })
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

  // 调试执行用例（执行前置/测试/后置步骤，记录调试结果）
  app.post('/api/case-info/:id/debug', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { environmentId?: string; debugVars?: Record<string, string> }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })

    // 加载环境变量 + 全局变量，构建变量上下文（四级优先级）
    let envVars: Record<string, string> = {}
    let baseUrl = ''
    if (body?.environmentId) {
      const env = await prisma.environment.findUnique({ where: { id: body.environmentId } })
      if (env) {
        baseUrl = env.baseUrl ?? ''
        for (const kv of (env.variables as unknown as { key: string; value: string }[]) ?? []) {
          envVars[kv.key] = kv.value
        }
      }
    }
    const globalVars = await loadGlobalVariables(c.projectId)
    const merged = buildMergedContext(globalVars, envVars, {}, body?.debugVars ?? {})

    const steps = (c.steps as unknown as CaseStepDef[]) ?? []
    const start = Date.now()
    const { results, context } = await executeCaseSteps(steps, { baseUrl, initialVars: merged.vars })
    const duration = Date.now() - start

    const overall = results.every((r) => r.status === 'PASS')
      ? 'success'
      : results.some((r) => r.status === 'ERROR')
        ? 'error'
        : 'fail'

    // 记录调试记录
    await prisma.debugRecord.create({
      data: {
        caseId: id,
        caseNameSnapshot: c.name,
        environmentId: body?.environmentId ?? null,
        executeMode: 'server',
        result: overall,
        totalDuration: duration,
        stepResults: results as unknown as Prisma.InputJsonValue,
        extractedVariables: context as unknown as Prisma.InputJsonValue,
      },
    })

    return { status: overall, duration, results, variables: context }
  })

  // ---------- 回收站 ----------
  // 查询软删除的用例（回收站列表）
  app.get('/api/projects/:projectId/cases/recycle', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.caseInfo.findMany({
      where: { projectId, deletedAt: { not: null } },
      orderBy: { deletedAt: 'desc' },
    })
  })

  // 还原用例（清除 deletedAt）
  app.post('/api/case-info/:id/restore', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    await prisma.caseInfo.update({ where: { id }, data: { deletedAt: null } })
    return { ok: true }
  })

  // 永久删除用例（物理删除，连同版本与调试记录）
  app.delete('/api/case-info/:id/permanent', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.caseInfo.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    await prisma.caseInfo.delete({ where: { id } })
    return { ok: true }
  })
}
