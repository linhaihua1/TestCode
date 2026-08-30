/**
 * UI 自动化测试路由：用例 CRUD + 执行 + 报告查询。
 */
import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { runUiSteps } from '../ui-engine/runner.js'
import type { UiStep, UiStepResult } from '../ui-engine/types.js'

interface UiTestCaseBody {
  name?: string
  description?: string
  baseUrl?: string
  setupSteps?: unknown
  steps?: unknown
  teardownSteps?: unknown
}

export async function uiTestRoutes(app: FastifyInstance) {
  // ---------- 用例 ----------
  // 用例列表
  app.get('/api/projects/:projectId/ui-tests', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.uiTestCase.findMany({ where: { projectId }, orderBy: { createdAt: 'asc' } })
  })

  // 新建用例
  app.post('/api/projects/:projectId/ui-tests', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as UiTestCaseBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.uiTestCase.create({
      data: {
        projectId,
        name: body.name,
        description: body.description,
        baseUrl: body.baseUrl,
        setupSteps: (body.setupSteps ?? []) as Prisma.InputJsonValue,
        steps: (body.steps ?? []) as Prisma.InputJsonValue,
        teardownSteps: (body.teardownSteps ?? []) as Prisma.InputJsonValue,
      },
    })
  })

  // 用例详情
  app.get('/api/ui-tests/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const testCase = await prisma.uiTestCase.findUnique({ where: { id } })
    if (!testCase) return reply.code(404).send({ error: 'UI 测试不存在' })
    return testCase
  })

  // 更新用例
  app.put('/api/ui-tests/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as UiTestCaseBody
    const testCase = await prisma.uiTestCase.findUnique({ where: { id } })
    if (!testCase) return reply.code(404).send({ error: 'UI 测试不存在' })
    return prisma.uiTestCase.update({
      where: { id },
      data: {
        name: body.name,
        description: body.description,
        baseUrl: body.baseUrl,
        setupSteps: (body.setupSteps ?? []) as Prisma.InputJsonValue,
        steps: (body.steps ?? []) as Prisma.InputJsonValue,
        teardownSteps: (body.teardownSteps ?? []) as Prisma.InputJsonValue,
      },
    })
  })

  // 删除用例
  app.delete('/api/ui-tests/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const testCase = await prisma.uiTestCase.findUnique({ where: { id } })
    if (!testCase) return reply.code(404).send({ error: 'UI 测试不存在' })
    await prisma.uiTestCase.delete({ where: { id } })
    return { ok: true }
  })

  // ---------- 执行 ----------
  // 执行用例：驱动真实浏览器逐步执行，返回报告
  app.post('/api/ui-tests/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const testCase = await prisma.uiTestCase.findUnique({ where: { id } })
    if (!testCase) return reply.code(404).send({ error: 'UI 测试不存在' })

    // 按 Pytest 三段式顺序执行：前置(setup) → 测试步骤 → 后置(teardown)
    const setupSteps = (testCase.setupSteps as unknown as UiStep[]) ?? []
    const steps = (testCase.steps as unknown as UiStep[]) ?? []
    const teardownSteps = (testCase.teardownSteps as unknown as UiStep[]) ?? []
    const allSteps = [...setupSteps, ...steps, ...teardownSteps]
    const start = Date.now()
    let results: UiStepResult[]
    try {
      results = await runUiSteps(allSteps, { baseUrl: testCase.baseUrl ?? undefined })
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(500).send({ error: `执行失败：${message}` })
    }

    const duration = Date.now() - start
    const status = results.every((r) => r.status === 'PASS')
      ? 'PASS'
      : results.some((r) => r.status === 'ERROR')
        ? 'ERROR'
        : 'FAIL'

    const report = await prisma.uiReport.create({
      data: {
        projectId: testCase.projectId,
        testCaseId: testCase.id,
        name: testCase.name,
        status,
        duration,
        details: results as unknown as Prisma.InputJsonValue,
      },
    })
    return report
  })

  // ---------- 报告 ----------
  // 报告列表
  app.get('/api/projects/:projectId/ui-reports', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.uiReport.findMany({ where: { projectId }, orderBy: { startedAt: 'desc' } })
  })

  // 报告详情
  app.get('/api/ui-reports/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const report = await prisma.uiReport.findUnique({ where: { id } })
    if (!report) return reply.code(404).send({ error: '报告不存在' })
    return report
  })
}
