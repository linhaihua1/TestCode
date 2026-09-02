import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { runScenario } from '../engine/runner.js'

/**
 * 场景相关的 REST 路由：
 * - 场景（Scenario）的增删改查（列表 / 新建 / 详情 / 更新 / 删除）
 * - 场景步骤批量更新（整体替换）
 * - 场景执行（触发一次自动化测试运行）
 */
interface ScenarioBody {
  name: string // 场景名称
  description?: string // 场景描述（可选）
}

interface StepInput {
  order: number // 步骤执行顺序
  apiCaseId?: string | null // 关联的接口用例 ID（可选）
  name?: string | null // 步骤名称（可选）
  assertions?: unknown // 步骤断言配置（可选）
  extracts?: unknown // 步骤变量提取配置（可选）
}

export async function scenarioRoutes(app: FastifyInstance) {
  // 获取项目下的场景列表
  app.get('/api/projects/:projectId/scenarios', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.scenario.findMany({ where: { projectId }, orderBy: { createdAt: 'asc' } })
  })

  // 在项目下新建场景
  app.post('/api/projects/:projectId/scenarios', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as ScenarioBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' }) // 场景名称必填校验
    return prisma.scenario.create({
      data: { projectId, name: body.name, description: body.description },
    })
  })

  // 获取场景详情（含步骤及其关联的接口用例/接口）
  app.get('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.scenario.findUnique({
      where: { id },
      include: {
        // 按顺序加载步骤，并级联加载用例与接口
        steps: { orderBy: { order: 'asc' }, include: { apiCase: { include: { api: true } } } },
      },
    })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' }) // 场景不存在返回 404
    return scenario
  })

  // 更新场景
  app.put('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ScenarioBody
    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' }) // 场景不存在返回 404
    // 只允许更新 name 和 description，防止前端传入不存在的字段导致 Prisma 报错
    return prisma.scenario.update({
      where: { id },
      data: {
        name: body.name,
        description: body.description,
      },
    })
  })

  // 删除场景
  app.delete('/api/scenarios/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' }) // 场景不存在返回 404
    await prisma.scenario.delete({ where: { id } })
    return { ok: true }
  })

  // 批量更新步骤（整体替换）
  app.put('/api/scenarios/:id/steps', async (req, reply) => {
    const { id } = req.params as { id: string }
    const steps = req.body as StepInput[]
    if (!Array.isArray(steps)) return reply.code(400).send({ error: 'steps 必须是数组' }) // 步骤必须为数组

    const scenario = await prisma.scenario.findUnique({ where: { id } })
    if (!scenario) return reply.code(404).send({ error: '场景不存在' }) // 场景不存在返回 404

    await prisma.$transaction(async (tx) => {
      await tx.scenarioStep.deleteMany({ where: { scenarioId: id } }) // 先删除旧步骤，实现整体替换
      for (const s of steps) {
        await tx.scenarioStep.create({
          data: {
            scenarioId: id,
            order: s.order,
            apiCaseId: s.apiCaseId ?? null, // 关联用例缺省为 null
            name: s.name ?? null, // 步骤名缺省为 null
            assertions: s.assertions ?? [], // 断言缺省为空数组
            extracts: s.extracts ?? [], // 变量提取缺省为空数组
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
    if (!body?.environmentId) return reply.code(400).send({ error: 'environmentId 必填' }) // 环境 ID 必填校验

    try {
      const { report } = await runScenario({ scenarioId: id, environmentId: body.environmentId })
      return report
    } catch (err) {
      // 执行失败时统一以 404 返回错误信息
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(404).send({ error: message })
    }
  })
}
