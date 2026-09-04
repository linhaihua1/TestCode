import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

interface VariableBody {
  name?: string
  type?: string
  value?: string
  encrypted?: boolean
  description?: string
}

export async function globalVariableRoutes(app: FastifyInstance) {
  // 获取项目下的全局变量列表
  app.get('/api/projects/:projectId/global-variables', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.globalVariable.findMany({
      where: { projectId },
      orderBy: { name: 'asc' },
    })
  })

  // 新建全局变量
  app.post('/api/projects/:projectId/global-variables', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as VariableBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    // 变量名校验：字母/数字/下划线，不能以数字开头
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(body.name)) {
      return reply.code(400).send({ error: '变量名格式不正确（字母/数字/下划线，不能以数字开头）' })
    }
    // 同名检查
    const exists = await prisma.globalVariable.findFirst({ where: { projectId, name: body.name } })
    if (exists) return reply.code(409).send({ error: '变量名已存在' })
    return prisma.globalVariable.create({
      data: {
        projectId,
        name: body.name,
        type: body.type ?? 'string',
        value: body.value ?? '',
        encrypted: body.encrypted ?? false,
        description: body.description,
      },
    })
  })

  // 更新全局变量
  app.put('/api/global-variables/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as VariableBody
    const v = await prisma.globalVariable.findUnique({ where: { id } })
    if (!v) return reply.code(404).send({ error: '变量不存在' })
    return prisma.globalVariable.update({ where: { id }, data: { value: body.value, description: body.description } })
  })

  // 删除全局变量
  app.delete('/api/global-variables/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const v = await prisma.globalVariable.findUnique({ where: { id } })
    if (!v) return reply.code(404).send({ error: '变量不存在' })
    await prisma.globalVariable.delete({ where: { id } })
    return { ok: true }
  })

  // 批量删除
  app.delete('/api/global-variables', async (req, reply) => {
    const body = req.body as { ids: string[] }
    if (!body?.ids?.length) return reply.code(400).send({ error: 'ids 必填' })
    await prisma.globalVariable.deleteMany({ where: { id: { in: body.ids } } })
    return { ok: true }
  })

  // 获取所有全局变量（跨项目，供变量解析器使用）
  app.get('/api/global-variables', async () => {
    return prisma.globalVariable.findMany({ orderBy: { name: 'asc' } })
  })
}
