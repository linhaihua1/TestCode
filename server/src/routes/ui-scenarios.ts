/**
 * UI 自动化执行场景路由：场景 CRUD + 步骤批量更新 + 执行 + 报告。
 */
import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { runUiScenario } from '../ui-engine/runner.js'
import type { UiStep } from '../ui-engine/types.js'

interface UiScenarioBody {
  name?: string
  description?: string
}

interface StepInput {
  order: number
  uiTestCaseId?: string | null
}

export async function uiScenarioRoutes(app: FastifyInstance) {
  // 场景列表
  app.get('/api/projects/:projectId/ui-scenarios', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.uiScenario.findMany({ where: { projectId }, orderBy: { createdAt: 'asc' } })
  })

  // 新建场景
  app.post('/api/projects/:projectId/ui-scenarios', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as UiScenarioBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.uiScenario.create({
      data: { projectId, name: body.name, description: body.description },
    })
  })

  // 场景详情（含步骤 + 关联的 UI 用例）
  app.get('/api/ui-scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.uiScenario.findUnique({
      where: { id },
      include: {
        steps: { orderBy: { order: 'asc' }, include: { uiTestCase: true } },
      },
    })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    return scenario
  })

  // 更新场景
  app.put('/api/ui-scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as UiScenarioBody
    const scenario = await prisma.uiScenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    // 只允许更新 name 和 description，防止前端传入不存在的字段导致 Prisma 报错
    return prisma.uiScenario.update({
      where: { id },
      data: {
        name: body.name,
        description: body.description,
      },
    })
  })

  // 删除场景
  app.delete('/api/ui-scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.uiScenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    await prisma.uiScenario.delete({ where: { id } })
    return { ok: true }
  })

  // 批量更新步骤（整体替换）
  app.put('/api/ui-scenarios/:id/steps', async (req, reply) => {
    const { id } = req.params as { id: string }
    const steps = req.body as StepInput[]
    if (!Array.isArray(steps)) return reply.code(400).send({ error: 'steps 必须是数组' })

    const scenario = await prisma.uiScenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })

    await prisma.$transaction(async (tx) => {
      await tx.uiScenarioStep.deleteMany({ where: { scenarioId: id } })
      for (const s of steps) {
        await tx.uiScenarioStep.create({
          data: { scenarioId: id, order: s.order, uiTestCaseId: s.uiTestCaseId ?? null },
        })
      }
    })
    return { ok: true }
  })

  // 执行场景：同一浏览器会话串联所有 UI 用例
  app.post('/api/ui-scenarios/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.uiScenario.findUnique({
      where: { id },
      include: { steps: { orderBy: { order: 'asc' }, include: { uiTestCase: true } } },
    })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })

    // 组装用例列表（跳过未绑定用例的步骤），每个用例按 setup→test→teardown 顺序执行
    const cases = scenario.steps
      .filter((s) => s.uiTestCase)
      .map((s) => ({
        id: s.uiTestCase!.id,
        name: s.uiTestCase!.name,
        baseUrl: s.uiTestCase!.baseUrl,
        steps: [
          ...((s.uiTestCase!.setupSteps as unknown as UiStep[]) ?? []),
          ...((s.uiTestCase!.steps as unknown as UiStep[]) ?? []),
          ...((s.uiTestCase!.teardownSteps as unknown as UiStep[]) ?? []),
        ],
      }))

    const start = Date.now()
    let caseResults
    try {
      caseResults = await runUiScenario(cases)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(500).send({ error: `执行失败：${message}` })
    }

    const duration = Date.now() - start
    const status = caseResults.every((r) => r.status === 'PASS')
      ? 'PASS'
      : caseResults.some((r) => r.status === 'ERROR')
        ? 'ERROR'
        : 'FAIL'

    const report = await prisma.uiReport.create({
      data: {
        projectId: scenario.projectId,
        testCaseId: null, // 场景执行，非单用例
        name: scenario.name,
        status,
        duration,
        details: caseResults as unknown as Prisma.InputJsonValue,
      },
    })
    return report
  })

  // 直接执行：接收按顺序排列的 UI 用例 ID 列表，收集用例后在同一个浏览器会话中执行
  app.post('/api/projects/:projectId/ui-execute', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as { testCaseIds?: string[] }
    const ids = body?.testCaseIds ?? []

    // 按传入顺序加载用例（跳过不存在的），每个用例按 setup→test→teardown 顺序执行
    const cases: { id: string; name: string; baseUrl?: string | null; steps: UiStep[] }[] = []
    for (const id of ids) {
      const tc = await prisma.uiTestCase.findUnique({ where: { id } })
      if (tc) {
        cases.push({
          id: tc.id,
          name: tc.name,
          baseUrl: tc.baseUrl,
          steps: [
            ...((tc.setupSteps as unknown as UiStep[]) ?? []),
            ...((tc.steps as unknown as UiStep[]) ?? []),
            ...((tc.teardownSteps as unknown as UiStep[]) ?? []),
          ],
        })
      }
    }

    const start = Date.now()
    let caseResults
    try {
      caseResults = await runUiScenario(cases)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(500).send({ error: `执行失败：${message}` })
    }

    const duration = Date.now() - start
    const status = caseResults.every((r) => r.status === 'PASS')
      ? 'PASS'
      : caseResults.some((r) => r.status === 'ERROR')
        ? 'ERROR'
        : 'FAIL'

    const report = await prisma.uiReport.create({
      data: {
        projectId,
        testCaseId: null,
        name: 'UI 用例执行',
        status,
        duration,
        details: caseResults as unknown as Prisma.InputJsonValue,
      },
    })
    return report
  })
}
