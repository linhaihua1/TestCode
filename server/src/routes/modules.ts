import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'

interface ModuleBody {
  name?: string
  type?: string
  parentId?: string | null
  sortOrder?: number
}

export async function moduleRoutes(app: FastifyInstance) {
  // 获取项目下的目录树（扁平列表，前端自行构建树）
  app.get('/api/projects/:projectId/modules', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.module.findMany({
      where: { projectId },
      orderBy: { sortOrder: 'asc' },
    })
  })

  // 新建目录
  app.post('/api/projects/:projectId/modules', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as ModuleBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    // 校验父目录存在（且属于当前项目），避免外键错误；不存在则回退到根目录
    let parentId: string | null = body.parentId ?? null
    if (parentId) {
      const parent = await prisma.module.findFirst({ where: { id: parentId, projectId } })
      if (!parent) parentId = null
    }
    return prisma.module.create({
      data: {
        projectId,
        name: body.name,
        type: body.type ?? 'case',
        parentId,
        sortOrder: body.sortOrder ?? 0,
      },
    })
  })

  // 更新目录
  app.put('/api/modules/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ModuleBody
    const m = await prisma.module.findUnique({ where: { id } })
    if (!m) return reply.code(404).send({ error: '目录不存在' })
    return prisma.module.update({ where: { id }, data: { name: body.name, parentId: body.parentId ?? null, sortOrder: body.sortOrder ?? m.sortOrder } })
  })

  // 删除目录（子目录和用例同步删除）
  app.delete('/api/modules/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const m = await prisma.module.findUnique({ where: { id } })
    if (!m) return reply.code(404).send({ error: '目录不存在' })
    // 先删除子目录
    await prisma.module.deleteMany({ where: { parentId: id } })
    await prisma.module.delete({ where: { id } })
    return { ok: true }
  })

  // 获取目录详情（含子目录和用例列表）
  app.get('/api/modules/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const m = await prisma.module.findUnique({
      where: { id },
      include: { children: { orderBy: { sortOrder: 'asc' } }, cases: { orderBy: { name: 'asc' } } },
    })
    if (!m) return reply.code(404).send({ error: '目录不存在' })
    return m
  })
}
