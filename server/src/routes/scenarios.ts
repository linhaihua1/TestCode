import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { runScenario } from '../engine/runner.js'

interface ScenarioBody {
  name: string
  description?: string
}

interface StepInput {
  order: number
  apiCaseId?: string | null
  name?: string | null
  assertions?: unknown
  extracts?: unknown
}

export async function scenarioRoutes(app: FastifyInstance) {
  app.get('/api/projects/:projectId/scenarios', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.scenario.findMany({ where: { projectId }, orderBy: { createdAt: 'asc' } })
  })

  app.post('/api/projects/:projectId/scenarios', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as ScenarioBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.scenario.create({
      data: { projectId, name: body.name, description: body.description },
    })
  })

  app.get('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.scenario.findUnique({
      where: { id },
      include: {
        steps: { orderBy: { order: 'asc' }, include: { apiCase: { include: { api: true } } } },
      },
    })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    return scenario
  })

  app.put('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ScenarioBody
    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    return prisma.scenario.update({ where: { id }, data: body })
  })

  app.delete('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })
    await prisma.scenario.delete({ where: { id } })
    return { ok: true }
  })

  // 批量更新步骤（整体替换）
  app.put('/api/scenarios/:id/steps', async (req, reply) => {
    const { id } = req.params as { id: string }
    const steps = req.body as StepInput[]
    if (!Array.isArray(steps)) return reply.code(400).send({ error: 'steps 必须是数组' })

    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' })

    await prisma.$transaction(async (tx) => {
      await tx.scenarioStep.deleteMany({ where: { scenarioId: id } })
      for (const s of steps) {
        await tx.scenarioStep.create({
          data: {
            scenarioId: id,
            order: s.order,
            apiCaseId: s.apiCaseId ?? null,
            name: s.name ?? null,
            assertions: s.assertions ?? [],
            extracts: s.extracts ?? [],
          },
        })
      }
    })
    return { ok: true }
  })

  // 执行场景
  app.post('/api/scenarios/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { environmentId: string }
    if (!body?.environmentId) return reply.code(400).send({ error: 'environmentId 必填' })

    try {
      const { report } = await runScenario({ scenarioId: id, environmentId: body.environmentId })
      return report
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(404).send({ error: message })
    }
  })
}
